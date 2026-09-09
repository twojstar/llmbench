package com.twojstar.llmbench.data.skills

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class LocalSkillSummary(
    val name: String,
    val description: String,
    val enabled: Boolean
)

internal data class LocalSkillDocument(
    val manifest: AgentSkillManifest,
    val source: String,
    val sourceDigest: String
)

internal data class LocalSkillReplacementResult(
    val skill: LocalSkillSummary,
    val sourceDigest: String
)

internal class LocalSkillAlreadyExistsException(
    val skillName: String
) : IOException("Local skill '$skillName' already exists.")

internal class LocalSkillNotFoundException(
    val skillName: String
) : IOException("Local skill '$skillName' is no longer available.")

internal class LocalSkillRenameRequiredException(
    val existingName: String,
    val newName: String
) : IOException("Rename '$existingName' to '$newName' before saving this source.")

internal class LocalSkillSourceConflictException(
    val skillName: String
) : IOException("Local skill '$skillName' changed since this editor was opened. Reload it before saving.")

internal class LocalSkillActivationException(message: String) : IOException(message)

private data class ParsedLocalSkillSource(
    val manifest: AgentSkillManifest,
    val bytes: ByteArray,
    val sourceDigest: String
)

private data class RenameMarker(
    val sourceDirectoryName: String,
    val committed: Boolean
)

internal class LocalSkillLibraryStore(
    private val rootDirectory: File
) {
    private val mutex = PROCESS_MUTEX

    suspend fun load(): List<LocalSkillSummary> = mutex.withLock {
        recoverPendingRenames()
        inspectStoredSkills(pruneInvalid = true).sortedBy(LocalSkillSummary::name)
    }

    suspend fun loadEnabledManifests(): List<AgentSkillManifest> = mutex.withLock {
        recoverPendingRenames()
        val candidates = readEnabledManifests()
        val accepted = mutableListOf<AgentSkillManifest>()
        candidates.sortedBy(AgentSkillManifest::name).forEach { manifest ->
            if (localSkillRuntimeBudgetError(accepted + manifest) == null) {
                accepted += manifest
            } else {
                removeEnabledMarker(storageDirectory(manifest.name))
            }
        }
        accepted
    }

    suspend fun read(name: String): LocalSkillDocument? = mutex.withLock {
        recoverPendingRenames()
        val directory = storageDirectory(name)
        readStoredDocument(directory)
            ?.takeIf { it.manifest.name == name }
    }

    suspend fun add(
        source: String,
        replaceExisting: Boolean = false
    ): LocalSkillSummary {
        val parsed = parseLocalSkillSource(source)

        return mutex.withLock {
            recoverPendingRenames()
            val skillDirectory = storageDirectory(parsed.manifest.name)
            val existing = if (skillDirectory.isDirectory) readStoredDocument(skillDirectory) else null
            if (existing != null && !replaceExisting) {
                throw LocalSkillAlreadyExistsException(parsed.manifest.name)
            }
            if (skillDirectory.exists() && existing == null) {
                withContext(Dispatchers.IO) {
                    if (!skillDirectory.deleteRecursively()) {
                        throw IOException("Could not reclaim invalid local skill storage for '${parsed.manifest.name}'.")
                    }
                }
            }
            ensureCapacityFor(parsed.manifest.name)
            val enabled = existing != null && isEnabled(skillDirectory)
            if (enabled) {
                validateRuntimeBudgetFor(parsed.manifest)
            }
            withContext(Dispatchers.IO) {
                skillDirectory.mkdirs()
                writeAtomically(File(skillDirectory, SKILL_FILE_NAME), parsed.bytes)
            }
            parsed.manifest.toSummary(enabled)
        }
    }

    suspend fun replace(
        name: String,
        expectedSourceDigest: String,
        source: String
    ): LocalSkillReplacementResult {
        val parsed = parseLocalSkillSource(source)
        if (parsed.manifest.name != name) {
            throw LocalSkillRenameRequiredException(name, parsed.manifest.name)
        }

        return mutex.withLock {
            recoverPendingRenames()
            val skillDirectory = storageDirectory(name)
            val existing = readStoredDocument(skillDirectory)
                ?.takeIf { it.manifest.name == name }
                ?: throw LocalSkillNotFoundException(name)
            if (existing.sourceDigest != expectedSourceDigest) {
                throw LocalSkillSourceConflictException(name)
            }
            val enabled = isEnabled(skillDirectory)
            if (enabled) {
                validateRuntimeBudgetFor(parsed.manifest)
            }
            withContext(Dispatchers.IO) {
                writeAtomically(File(skillDirectory, SKILL_FILE_NAME), parsed.bytes)
            }
            LocalSkillReplacementResult(
                skill = parsed.manifest.toSummary(enabled),
                sourceDigest = parsed.sourceDigest
            )
        }
    }

    suspend fun rename(
        existingName: String,
        expectedSourceDigest: String,
        source: String
    ): LocalSkillReplacementResult {
        val parsed = parseLocalSkillSource(source)
        val newName = parsed.manifest.name
        if (newName == existingName) {
            return replace(existingName, expectedSourceDigest, source)
        }

        return mutex.withLock {
            recoverPendingRenames()
            val existingDirectory = storageDirectory(existingName)
            val existing = readStoredDocument(existingDirectory)
                ?.takeIf { it.manifest.name == existingName }
                ?: throw LocalSkillNotFoundException(existingName)
            if (existing.sourceDigest != expectedSourceDigest) {
                throw LocalSkillSourceConflictException(existingName)
            }

            val targetDirectory = storageDirectory(newName)
            if (targetDirectory.exists()) {
                val target = readStoredDocument(targetDirectory)
                if (target != null) throw LocalSkillAlreadyExistsException(newName)
                withContext(Dispatchers.IO) {
                    if (!targetDirectory.deleteRecursively()) {
                        throw IOException("Could not reclaim invalid local skill storage for '$newName'.")
                    }
                }
            }

            val enabled = isEnabled(existingDirectory)
            if (enabled) {
                validateRuntimeBudgetFor(parsed.manifest, excludeName = existingName)
            }

            withContext(Dispatchers.IO) {
                targetDirectory.mkdirs()
                val renameMarker = File(targetDirectory, RENAME_FROM_FILE_NAME)
                val pendingMarker = renameMarkerValue(existingDirectory.name, committed = false)
                val committedMarker = renameMarkerValue(existingDirectory.name, committed = true)
                writeAtomically(renameMarker, pendingMarker.encodeToByteArray())
                try {
                    writeAtomically(File(targetDirectory, SKILL_FILE_NAME), parsed.bytes)
                    if (enabled) {
                        writeAtomically(File(targetDirectory, ENABLED_FILE_NAME), ByteArray(0))
                    }
                    // Overwriting this marker is the rename commit point. Before it, recovery
                    // discards the staged target. After it, the old identity is logically hidden.
                    writeAtomically(renameMarker, committedMarker.encodeToByteArray())
                } catch (error: IOException) {
                    targetDirectory.deleteRecursively()
                    throw error
                }

                if (!existingDirectory.exists() || existingDirectory.deleteRecursively()) {
                    renameMarker.delete()
                }
            }

            LocalSkillReplacementResult(
                skill = parsed.manifest.toSummary(enabled),
                sourceDigest = parsed.sourceDigest
            )
        }
    }

    suspend fun setEnabled(name: String, enabled: Boolean): LocalSkillSummary? = mutex.withLock {
        recoverPendingRenames()
        val directory = storageDirectory(name)
        val document = readStoredDocument(directory)
            ?.takeIf { it.manifest.name == name }
            ?: return@withLock null
        if (enabled) {
            validateRuntimeBudgetFor(document.manifest)
        }
        withContext(Dispatchers.IO) {
            val marker = File(directory, ENABLED_FILE_NAME)
            if (enabled) {
                writeAtomically(marker, ByteArray(0))
            } else if (marker.exists() && !marker.delete()) {
                throw IOException("Could not disable local skill '$name'.")
            }
        }
        document.manifest.toSummary(enabled)
    }

    suspend fun remove(name: String) = mutex.withLock {
        recoverPendingRenames()
        withContext(Dispatchers.IO) {
            val directory = storageDirectory(name)
            if (directory.exists() && !directory.deleteRecursively()) {
                throw IOException("Could not remove local skill '$name'.")
            }
        }
    }

    private suspend fun parseLocalSkillSource(source: String): ParsedLocalSkillSource {
        val parsed = withContext(Dispatchers.Default) {
            AgentSkillManifestParser.parse(source)
        }
        val manifest = parsed.manifest?.takeIf { parsed.issues.isEmpty() }
            ?: throw IllegalArgumentException("Only valid portable SKILL.md content can be saved.")
        val bytes = source.encodeToByteArray()
        if (bytes.size > MAX_SKILL_BYTES) throw IOException("Skill source exceeds the library size limit.")
        return ParsedLocalSkillSource(
            manifest = manifest,
            bytes = bytes,
            sourceDigest = localSkillSourceDigest(source)
        )
    }

    private suspend fun validateRuntimeBudgetFor(
        candidate: AgentSkillManifest,
        excludeName: String = candidate.name
    ) {
        val enabledOthers = readEnabledManifests(excludeName = excludeName)
        localSkillRuntimeBudgetError(enabledOthers + candidate)?.let { message ->
            throw LocalSkillActivationException(message)
        }
    }

    private suspend fun readEnabledManifests(excludeName: String? = null): List<AgentSkillManifest> {
        val directories = withContext(Dispatchers.IO) { storageDirectories() }
        val enabled = ArrayList<AgentSkillManifest>(directories.size)
        directories.forEach { directory ->
            val document = readStoredDocument(directory)
            if (document == null) {
                withContext(Dispatchers.IO) { directory.deleteRecursively() }
            } else if (document.manifest.name != excludeName && isEnabled(directory)) {
                enabled += document.manifest
            }
        }
        return enabled
    }

    private suspend fun removeEnabledMarker(directory: File) = withContext(Dispatchers.IO) {
        val marker = File(directory, ENABLED_FILE_NAME)
        if (marker.exists() && !marker.delete()) {
            throw IOException("Could not disable an over-budget local skill.")
        }
    }

    private suspend fun ensureCapacityFor(name: String) {
        val targetDirectory = storageDirectory(name)
        val directories = withContext(Dispatchers.IO) {
            rootDirectory.mkdirs()
            storageDirectories()
        }
        if (targetDirectory.isDirectory || directories.size < MAX_LOCAL_SKILLS) return

        val valid = inspectStoredSkills(pruneInvalid = true)
        if (valid.size >= MAX_LOCAL_SKILLS) throw IOException("Local skill library is full.")
    }

    private suspend fun inspectStoredSkills(pruneInvalid: Boolean): List<LocalSkillSummary> {
        val directories = withContext(Dispatchers.IO) { storageDirectories() }
        val valid = ArrayList<LocalSkillSummary>(directories.size)
        directories.forEach { directory ->
            val document = readStoredDocument(directory)
            if (document == null) {
                if (pruneInvalid) {
                    withContext(Dispatchers.IO) { directory.deleteRecursively() }
                }
            } else {
                valid += document.manifest.toSummary(isEnabled(directory))
            }
        }
        return valid
    }

    private suspend fun recoverPendingRenames() {
        val directories = withContext(Dispatchers.IO) { storageDirectories() }
        directories.forEach { targetDirectory ->
            val markerFile = File(targetDirectory, RENAME_FROM_FILE_NAME)
            if (!markerFile.isFile) return@forEach
            val marker = withContext(Dispatchers.IO) {
                runCatching { parseRenameMarker(markerFile.readText()) }.getOrNull()
            }
            if (marker == null || !isStorageKey(marker.sourceDirectoryName)) {
                withContext(Dispatchers.IO) { targetDirectory.deleteRecursively() }
                return@forEach
            }

            val sourceDirectory = File(rootDirectory, marker.sourceDirectoryName)
            if (!marker.committed) {
                withContext(Dispatchers.IO) { targetDirectory.deleteRecursively() }
                return@forEach
            }

            val targetDocument = readStoredDocument(targetDirectory, includeRenamedSources = true)
            if (targetDocument == null) {
                withContext(Dispatchers.IO) { targetDirectory.deleteRecursively() }
                return@forEach
            }

            withContext(Dispatchers.IO) {
                // Once the marker is committed, the target is canonical. Failure to physically
                // remove the old directory must not make the whole library unavailable.
                if (!sourceDirectory.exists() || sourceDirectory.deleteRecursively()) {
                    markerFile.delete()
                }
            }
        }
    }

    private suspend fun isEnabled(directory: File): Boolean = withContext(Dispatchers.IO) {
        File(directory, ENABLED_FILE_NAME).isFile
    }

    private suspend fun readStoredDocument(
        directory: File,
        includeRenamedSources: Boolean = false
    ): LocalSkillDocument? {
        if (!includeRenamedSources && isCommittedRenameSourceDirectory(directory)) return null
        val source = withContext(Dispatchers.IO) {
            readStoredSource(directory)
        } ?: return null
        val parsed = withContext(Dispatchers.Default) {
            AgentSkillManifestParser.parse(source)
        }
        val manifest = parsed.manifest?.takeIf { parsed.issues.isEmpty() } ?: return null
        if (storageKey(manifest.name) != directory.name) return null
        return LocalSkillDocument(
            manifest = manifest,
            source = source,
            sourceDigest = localSkillSourceDigest(source)
        )
    }

    private fun isCommittedRenameSourceDirectory(directory: File): Boolean =
        storageDirectories().any { candidate ->
            val markerFile = File(candidate, RENAME_FROM_FILE_NAME)
            if (!markerFile.isFile) return@any false
            val marker = runCatching { parseRenameMarker(markerFile.readText()) }.getOrNull()
                ?: return@any false
            marker.committed && marker.sourceDirectoryName == directory.name
        }

    private fun renameMarkerValue(sourceDirectoryName: String, committed: Boolean): String =
        (if (committed) RENAME_COMMITTED_PREFIX else RENAME_PENDING_PREFIX) + sourceDirectoryName

    private fun parseRenameMarker(value: String): RenameMarker? = when {
        value.startsWith(RENAME_COMMITTED_PREFIX) -> RenameMarker(
            sourceDirectoryName = value.removePrefix(RENAME_COMMITTED_PREFIX),
            committed = true
        )
        value.startsWith(RENAME_PENDING_PREFIX) -> RenameMarker(
            sourceDirectoryName = value.removePrefix(RENAME_PENDING_PREFIX),
            committed = false
        )
        else -> null
    }

    private fun readStoredSource(directory: File): String? {
        val sourceFile = File(directory, SKILL_FILE_NAME)
        if (!sourceFile.isFile || sourceFile.length() !in 0..MAX_SKILL_BYTES.toLong()) return null
        return try {
            sourceFile.readBytes().decodeToString(throwOnInvalidSequence = true)
        } catch (_: IOException) {
            null
        }
    }

    private fun storageDirectories(): List<File> =
        rootDirectory.listFiles()
            ?.filter { it.isDirectory && isStorageKey(it.name) }
            .orEmpty()

    private fun storageDirectory(name: String): File =
        File(rootDirectory, storageKey(name))

    private fun storageKey(name: String): String =
        STORAGE_PREFIX + sha256Hex(name.encodeToByteArray())

    private fun isStorageKey(value: String): Boolean =
        value.startsWith(STORAGE_PREFIX) && isSha256Hex(value.drop(STORAGE_PREFIX.length))

    private fun writeAtomically(destination: File, bytes: ByteArray) {
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.flush()
            output.fd.sync()
        }
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            temporary.delete()
        }
    }

    private fun AgentSkillManifest.toSummary(enabled: Boolean): LocalSkillSummary =
        LocalSkillSummary(name = name, description = description, enabled = enabled)

    companion object {
        private val PROCESS_MUTEX = Mutex()
        const val MAX_LOCAL_SKILLS = 32
        const val LIBRARY_DIRECTORY_NAME = "local-skills-v1"
        private const val SKILL_FILE_NAME = "SKILL.md"
        private const val ENABLED_FILE_NAME = ".enabled"
        private const val RENAME_FROM_FILE_NAME = ".rename-from"
        private const val RENAME_PENDING_PREFIX = "pending:"
        private const val RENAME_COMMITTED_PREFIX = "committed:"
        private const val MAX_SKILL_BYTES = 8 * 1024 * 1024
        private const val STORAGE_PREFIX = "skill-"
    }
}
