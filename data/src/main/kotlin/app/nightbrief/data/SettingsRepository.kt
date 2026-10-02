package app.nightbrief.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

internal object AppStateSerializer : Serializer<AppState> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue: AppState = AppState()

    fun encode(state: AppState): String = json.encodeToString(AppState.serializer(), state)

    fun decode(text: String): AppState = json.decodeFromString(AppState.serializer(), text)

    override suspend fun readFrom(input: InputStream): AppState = try {
        decode(input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("unreadable settings", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("invalid settings", e)
    }

    override suspend fun writeTo(t: AppState, output: OutputStream) {
        output.write(encode(t).encodeToByteArray())
    }
}

class SettingsRepository internal constructor(
    private val store: DataStore<AppState>,
    private val backupFile: File,
) {
    val state: Flow<AppState> = store.data

    suspend fun current(): AppState = store.data.first().ensureNotificationSlots()

    suspend fun update(transform: (AppState) -> AppState): AppState {
        val updated = store.updateData { current -> transform(current).ensureNotificationSlots() }
        publishBackup(updated)
        return updated
    }

    private val backupLock = Any()

    /** Set once [publishBackup] has stored an update, so a late read of the previous file cannot replace it. */
    private var backupPublished = false

    private fun publishBackup(state: AppState) {
        synchronized(backupLock) {
            writeBackupFile(state)
            backupPublished = true
        }
    }

    /** First successful read only. Later saves go through [publishBackup]. */
    private fun backupInitialRead(state: AppState) {
        synchronized(backupLock) {
            if (backupPublished) return
            writeBackupFile(state)
        }
    }

    private fun writeBackupFile(state: AppState) {
        val text = AppStateSerializer.encode(state.ensureNotificationSlots())
        val dir = backupFile.parentFile
        if (dir != null && !dir.exists()) dir.mkdirs()
        val tmp = File(dir, backupFile.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(backupFile)) {
            backupFile.writeText(text)
            tmp.delete()
        }
    }

    companion object {
        fun backupFileFor(file: File): File = File(file.parentFile, file.name + ".good")

        fun create(file: File, scope: CoroutineScope): SettingsRepository {
            val backup = backupFileFor(file)
            val repository = SettingsRepository(
                DataStoreFactory.create(
                    serializer = AppStateSerializer,
                    corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler {
                        readBackup(backup) ?: AppState()
                    },
                    scope = scope,
                    produceFile = { file },
                ),
                backup,
            )
            scope.launch {
                repository.store.data.collect { raw ->
                    repository.backupInitialRead(raw)
                }
            }
            return repository
        }

        internal fun readBackup(backup: File): AppState? {
            if (!backup.isFile) return null
            return try {
                AppStateSerializer.decode(backup.readText()).ensureNotificationSlots()
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
