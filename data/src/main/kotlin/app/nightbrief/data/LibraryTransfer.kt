package app.nightbrief.data

import app.nightbrief.gear.GearKit
import app.nightbrief.sites.SiteBook
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Sites and gear a user can save off-device. Digest settings and notification slots stay on the phone. */
@Serializable
data class LibraryFile(
    val sites: SiteBook = SiteBook(),
    val gear: GearKit = GearKit(),
)

class LibraryFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

object LibraryTransfer {
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
}
