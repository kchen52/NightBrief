package app.nightbrief.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    override suspend fun readFrom(input: InputStream): AppState = try {
        json.decodeFromString(AppState.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("unreadable settings", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("invalid settings", e)
    }

    override suspend fun writeTo(t: AppState, output: OutputStream) {
        output.write(json.encodeToString(AppState.serializer(), t).encodeToByteArray())
    }
}

class SettingsRepository internal constructor(private val store: DataStore<AppState>) {
    val state: Flow<AppState> = store.data

    suspend fun current(): AppState = store.data.first()

    suspend fun update(transform: (AppState) -> AppState): AppState = store.updateData(transform)

    companion object {
        fun create(file: File, scope: CoroutineScope): SettingsRepository = SettingsRepository(
            DataStoreFactory.create(
                serializer = AppStateSerializer,
                corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler { AppState() },
                scope = scope,
                produceFile = { file },
            ),
        )
    }
}
