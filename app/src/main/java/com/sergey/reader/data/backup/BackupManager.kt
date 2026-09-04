package com.sergey.reader.data.backup

import android.content.Context
import android.net.Uri
import com.sergey.reader.data.db.ReaderDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(
    private val context: Context,
    private val db: ReaderDatabase
) {
    suspend fun exportTo(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
                while (cursor.moveToNext()) Unit
            }
            val database = context.getDatabasePath(DB_NAME)
            require(database.isFile) { "База данных ещё не создана" }

            context.contentResolver.openOutputStream(uri, "w")?.use { raw ->
                ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                    zip.putNextEntry(ZipEntry("manifest.txt"))
                    zip.write(
                        buildString {
                            appendLine("Reader Backup")
                            appendLine("format=1")
                            appendLine("appVersion=0.3.0")
                            appendLine("createdAt=${System.currentTimeMillis()}")
                        }.toByteArray()
                    )
                    zip.closeEntry()

                    addFile(zip, database, "database/$DB_NAME")
                    addFileIfExists(zip, File(context.filesDir, "datastore/reader_settings.preferences_pb"), "datastore/reader_settings.preferences_pb")
                    listOf("books", "covers", "fonts", "epub_assets").forEach { name ->
                        addDirectory(zip, File(context.filesDir, name), "files/$name")
                    }
                }
            } ?: error("Не удалось открыть файл резервной копии")
        }
    }

    /**
     * Restore is staged and applied during the next Application.onCreate(), before Room/DataStore
     * are opened. This avoids replacing a live SQLite database underneath Room.
     */
    suspend fun stageRestore(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val pending = File(context.filesDir, PENDING_DIR)
            pending.deleteRecursively()
            pending.mkdirs()

            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val target = safeTarget(pending, entry.name)
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { out -> zip.copyTo(out) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("Не удалось прочитать резервную копию")

            val manifest = File(pending, "manifest.txt")
            require(manifest.isFile) { "Это не резервная копия Reader: отсутствует manifest.txt" }
            val manifestText = manifest.readText()
            require("Reader Backup" in manifestText && "format=1" in manifestText) {
                "Неподдерживаемый формат резервной копии"
            }
            require(File(pending, "database/$DB_NAME").isFile) { "В архиве нет базы Reader" }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PENDING, true)
                .apply()
        }
    }

    private fun addDirectory(zip: ZipOutputStream, dir: File, entryRoot: String) {
        if (!dir.exists()) return
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
            val rel = file.relativeTo(dir).invariantSeparatorsPath
            addFile(zip, file, "$entryRoot/$rel")
        }
    }

    private fun addFileIfExists(zip: ZipOutputStream, file: File, entryName: String) {
        if (file.isFile) addFile(zip, file, entryName)
    }

    private fun addFile(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun safeTarget(root: File, entryName: String): File {
        val target = File(root, entryName)
        val rootPath = root.canonicalPath + File.separator
        require(target.canonicalPath.startsWith(rootPath)) { "Некорректный путь в архиве" }
        return target
    }

    companion object {
        private const val DB_NAME = "reader.db"
        private const val PENDING_DIR = "pending_restore"
        private const val PREFS = "reader_restore_state"
        private const val KEY_PENDING = "pending"

        fun applyPendingRestore(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_PENDING, false)) return false
            val pending = File(context.filesDir, PENDING_DIR)
            val stagedDb = File(pending, "database/$DB_NAME")
            if (!stagedDb.isFile) {
                prefs.edit().remove(KEY_PENDING).apply()
                pending.deleteRecursively()
                return false
            }

            return runCatching {
                val dbTarget = context.getDatabasePath(DB_NAME)
                dbTarget.parentFile?.mkdirs()
                File(dbTarget.absolutePath + "-wal").delete()
                File(dbTarget.absolutePath + "-shm").delete()
                stagedDb.copyTo(dbTarget, overwrite = true)

                val stagedSettings = File(pending, "datastore/reader_settings.preferences_pb")
                if (stagedSettings.isFile) {
                    val settingsTarget = File(context.filesDir, "datastore/reader_settings.preferences_pb")
                    settingsTarget.parentFile?.mkdirs()
                    stagedSettings.copyTo(settingsTarget, overwrite = true)
                }

                val stagedFiles = File(pending, "files")
                listOf("books", "covers", "fonts", "epub_assets").forEach { name ->
                    val source = File(stagedFiles, name)
                    val target = File(context.filesDir, name)
                    target.deleteRecursively()
                    if (source.exists()) source.copyRecursively(target, overwrite = true)
                }

                prefs.edit().remove(KEY_PENDING).commit()
                pending.deleteRecursively()
                true
            }.getOrElse { false }
        }
    }
}
