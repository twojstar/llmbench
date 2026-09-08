package com.twojstar.llmbench.data.skills

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
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
    val source: String
)

internal class LocalSkillAlreadyExistsException(
    val skillName: String
) : IOException("Local skill '$skillName' already exists.")

internal class LocalSkillActivationException(message: String) : IOException(message)

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
        val parsed = withContext(Dispatchers.Default) {
            AgentSkillManifestParser.parse(source)
        }
        val manifest = parsed.manifest?.takeIf { parsed.issues.isEmpty() }
            ?: throw IllegalArgumentException("Only valid portable SKILL.md content can be added.")
        val bytes = source.encodeToByteArray()
        if (bytes.size > MAX_SKILL_BYTES) throw IOException("Skill source exceeds the library size limit.")

        return mutex.withLock {
            val skillDirectory = storageDirectory(manifest.name)
            val existing = if (skillDirectory.isDirectory) readStoredDocument(skillDirectory) else null
            if (existing != null && !replaceExisting) {
                throw LocalSkillAlreadyExistsException(manifest.name)
            }
            if (skillDirectory.exists() && existing == null) {
                withContext(Dispatchers.IO) {
                    if (!skillDirectory.deleteRecursively()) {
                        throw IOException("Could not reclaim invalid local skill storage for '${manifest.name}'.")
                    }
                }
            }
            ensureCapacityFor(manifest.name)
            val enabled = existing != null && isEnabled(skillDirectory)
            if (enabled) {
                validateRuntimeBudgetFor(manifest)
            }
            withContext(Dispatchers.IO) {
                skillDirectory.mkdirs()
                writeAtomically(File(skillDirectory, SKILL_FILE_NAME), bytes)
            }
            manifest.toSummary(enabled)
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
        return LocalSkillDocument(manifest = manifest, source = source)
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

    private fun storageKey(name: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(name.encodeToByteArray())
        val hex = CharArray(digest.size * 2)
        digest.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            hex[index * 2] = HEX_DIGITS[value ushr 4]
            hex[index * 2 + 1] = HEX_DIGITS[value and 0x0F]
        }
        return STORAGE_PREFIX + hex.concatToString()
    }

    private fun isStorageKey(value: String): Boolean =
        value.length == STORAGE_PREFIX.length + SHA256_HEX_CHARS &&
            value.startsWith(STORAGE_PREFIX) &&
            value.drop(STORAGE_PREFIX.length).all { it in HEX_DIGITS }

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
        private const val SHA256_HEX_CHARS = 64
        private const val HEX_DIGITS = "0123456789abcdef"
    }
}
