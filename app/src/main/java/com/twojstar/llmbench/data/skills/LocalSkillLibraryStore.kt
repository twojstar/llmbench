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

internal data class LocalSkillDocument(
    val manifest: AgentSkillManifest,
    val source: String
)

internal class LocalSkillLibraryStore(
    private val rootDirectory: File
) {
    private val mutex = Mutex()

    suspend fun load(): List<LocalSkillDocument> {
        val snapshots = mutex.withLock {
            withContext(Dispatchers.IO) { readSnapshots() }
        }
        return withContext(Dispatchers.Default) {
            snapshots.mapNotNull { snapshot ->
                val parsed = AgentSkillManifestParser.parse(
                    source = snapshot.source,
                    directoryName = snapshot.directoryName
                )
                parsed.manifest?.takeIf { parsed.issues.isEmpty() }?.let { manifest ->
                    LocalSkillDocument(manifest = manifest, source = snapshot.source)
                }
            }.sortedBy { it.manifest.name }
        }
    }

    suspend fun add(source: String): LocalSkillDocument {
        val parsed = withContext(Dispatchers.Default) {
            AgentSkillManifestParser.parse(source)
        }
        val manifest = parsed.manifest?.takeIf { parsed.issues.isEmpty() }
            ?: throw IllegalArgumentException("Only valid portable SKILL.md content can be added.")
        val bytes = source.encodeToByteArray()
        if (bytes.size > MAX_SKILL_BYTES) throw IOException("Skill source exceeds the library size limit.")

        mutex.withLock {
            withContext(Dispatchers.IO) {
                ensureCapacityFor(manifest.name)
                val skillDirectory = File(rootDirectory, manifest.name)
                skillDirectory.mkdirs()
                writeAtomically(File(skillDirectory, SKILL_FILE_NAME), bytes)
            }
        }
        return LocalSkillDocument(manifest = manifest, source = source)
    }

    suspend fun remove(name: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val directory = safeSkillDirectory(name) ?: return@withContext
            if (directory.exists() && !directory.deleteRecursively()) {
                throw IOException("Could not remove local skill '$name'.")
            }
        }
    }

    private fun readSnapshots(): List<StoredSkillSnapshot> {
        if (!rootDirectory.isDirectory) return emptyList()
        return rootDirectory.listFiles()
            ?.asSequence()
            ?.filter(File::isDirectory)
            ?.mapNotNull { directory ->
                val sourceFile = File(directory, SKILL_FILE_NAME)
                if (!sourceFile.isFile || sourceFile.length() > MAX_SKILL_BYTES) return@mapNotNull null
                runCatching {
                    StoredSkillSnapshot(
                        directoryName = directory.name,
                        source = sourceFile.readBytes().decodeToString(throwOnInvalidSequence = true)
                    )
                }.getOrNull()
            }
            ?.toList()
            .orEmpty()
    }

    private fun ensureCapacityFor(name: String) {
        rootDirectory.mkdirs()
        val existing = safeSkillDirectory(name)
        if (existing?.isDirectory == true) return
        val count = rootDirectory.listFiles()?.count(File::isDirectory) ?: 0
        if (count >= MAX_LOCAL_SKILLS) throw IOException("Local skill library is full.")
    }

    private fun safeSkillDirectory(name: String): File? {
        if (name.isBlank()) return null
        val root = rootDirectory.canonicalFile
        val candidate = File(root, name).canonicalFile
        return candidate.takeIf { it.parentFile == root }
    }

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

    private data class StoredSkillSnapshot(
        val directoryName: String,
        val source: String
    )

    companion object {
        const val MAX_LOCAL_SKILLS = 32
        const val LIBRARY_DIRECTORY_NAME = "local-skills-v1"
        private const val SKILL_FILE_NAME = "SKILL.md"
        private const val MAX_SKILL_BYTES = 8 * 1024 * 1024
    }
}
