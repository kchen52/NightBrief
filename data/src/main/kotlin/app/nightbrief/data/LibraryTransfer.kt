package app.nightbrief.data

import app.nightbrief.gear.GearKit
import app.nightbrief.sites.SiteBook
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

/** Sites and gear a user can save off-device. Digest settings and notification slots stay on the phone. */
@Serializable
data class LibraryFile(
    val sites: SiteBook = SiteBook(),
    val gear: GearKit = GearKit(),
)

open class LibraryFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

class EmptyLibraryException(message: String = "library must include at least one site", cause: Throwable? = null) :
    LibraryFormatException(message, cause)

object LibraryTransfer {
    /** Largest library document accepted on import, to bound memory on the caller. */
    const val MAX_LIBRARY_BYTES = 256 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(state: AppState): String =
        json.encodeToString(LibraryFile.serializer(), LibraryFile(state.sites, state.gear))

    fun decode(text: String): LibraryFile {
        try {
            val element = json.parseToJsonElement(text.trim())
            val objectKeys = (element as? JsonObject)?.jsonObject?.keys
                ?: throw LibraryFormatException("library must be a JSON object")
            if ("sites" !in objectKeys || "gear" !in objectKeys) {
                throw LibraryFormatException("library must include sites and gear")
            }
            val file = json.decodeFromJsonElement(LibraryFile.serializer(), element)
            file.sites.sites.size
            file.gear.bodies.size
            if (file.sites.sites.isEmpty()) throw EmptyLibraryException()
            return file
        } catch (e: LibraryFormatException) {
            throw e
        } catch (e: SerializationException) {
            throw LibraryFormatException("unreadable library", e)
        } catch (e: IllegalArgumentException) {
            throw LibraryFormatException("invalid library", e)
        }
    }

    fun apply(state: AppState, file: LibraryFile): AppState =
        state.copy(sites = file.sites, gear = file.gear).ensureNotificationSlots()

    /**
     * Reads [stream] as UTF-8, rejecting any document longer than [cap] bytes.
     * An exact-[cap] document is accepted; one more byte throws [LibraryFormatException].
     */
    fun readBounded(stream: InputStream, cap: Int = MAX_LIBRARY_BYTES): String {
        require(cap >= 0) { "cap must be non-negative" }
        val buffer = ByteArray(8192.coerceAtMost(cap.coerceAtLeast(1)))
        val out = ByteArrayOutputStream(cap.coerceAtMost(8192).coerceAtLeast(16))
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > cap) throw LibraryFormatException("library file is too large")
            out.write(buffer, 0, read)
        }
        // Charset overload of ByteArrayOutputStream.toString is API 33+; minSdk is 26.
        return String(out.toByteArray(), StandardCharsets.UTF_8)
    }
}
