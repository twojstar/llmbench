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
) : IOException(
    "Changing a skill name while editing is not supported. Keep '$existingName' as the name, " +
        "or import '$newName' as a separate skill."
)

internal class LocalSkillSourceConflictException(
    val skillName: String
) : IOException("Local skill '$skillName' changed since this editor was opened. Reload it before saving.")

internal class LocalSkillActivationException(message: String) : IOException(message)

private data class ParsedLocalSkillSource(
    val manifest: AgentSkillManifest,
    val bytes: ByteArray,
    val sourceDigest: String
)

internal class LocalSkillLibraryStore(
    private val rootDirectory: File
) {
    private val mutex = PROCESS_MUTEX

    suspend fun load(): List<LocalSkillSummary> = mutex.withLock {
        inspectStoredSkills(pruneInvalid = true).sortedBy(LocalSkillSummary::name)
    }

    suspend fun loadEnabledManifests(): List<AgentSkillManifest> = mutex.withLock {
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

    suspend fun setEnabled(name: String, enabled: Boolean): LocalSkillSummary? = mutex.withLock {
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

    private suspend fun validateRuntimeBudgetFor(candidate: AgentSkillManifest) {
        val enabledOthers = readEnabledManifests(excludeName = candidate.name)
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

    private suspend fun isEnabled(directory: File): Boolean = withContext(Dispatchers.IO) {
        File(directory, ENABLED_FILE_NAME).isFile
    }

    private suspend fun readStoredDocument(directory: File): LocalSkillDocument? {
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
        private const val MAX_SKILL_BYTES = 8 * 1024 * 1024
        private const val STORAGE_PREFIX = "skill-"
    }
}
