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
    val description: String
)

internal data class LocalSkillDocument(
    val manifest: AgentSkillManifest,
    val source: String
)

internal class LocalSkillAlreadyExistsException(
    val skillName: String
) : IOException("Local skill '$skillName' already exists.")

internal class LocalSkillLibraryStore(
    private val rootDirectory: File
) {
    private val mutex = Mutex()

    suspend fun load(): List<LocalSkillSummary> = mutex.withLock {
        inspectStoredSkills(pruneInvalid = true).sortedBy(LocalSkillSummary::name)
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

        mutex.withLock {
            val skillDirectory = storageDirectory(manifest.name)
            if (skillDirectory.isDirectory && !replaceExisting) {
                throw LocalSkillAlreadyExistsException(manifest.name)
            }
            ensureCapacityFor(manifest.name)
            withContext(Dispatchers.IO) {
                skillDirectory.mkdirs()
                writeAtomically(File(skillDirectory, SKILL_FILE_NAME), bytes)
            }
        }
        return manifest.toSummary()
    }

    suspend fun remove(name: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val directory = storageDirectory(name)
            if (directory.exists() && !directory.deleteRecursively()) {
                throw IOException("Could not remove local skill '$name'.")
            }
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
                valid += document.manifest.toSummary()
            }
        }
        return valid
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
        val temporary = File(destination.parentFile, "$SKILL_FILE_NAME.tmp")
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

    private fun AgentSkillManifest.toSummary(): LocalSkillSummary =
        LocalSkillSummary(name = name, description = description)

    companion object {
        const val MAX_LOCAL_SKILLS = 32
        const val LIBRARY_DIRECTORY_NAME = "local-skills-v1"
        private const val SKILL_FILE_NAME = "SKILL.md"
        private const val MAX_SKILL_BYTES = 8 * 1024 * 1024
        private const val STORAGE_PREFIX = "skill-"
        private const val SHA256_HEX_CHARS = 64
        private const val HEX_DIGITS = "0123456789abcdef"
    }
}
