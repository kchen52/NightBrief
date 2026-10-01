package app.nightbrief.sites

import kotlinx.serialization.Serializable

/**
 * The user's ordered list of saved sites with one designated primary site.
 * Immutable: every operation returns a new book. Invariant: when non-empty, [primaryId] refers to a site.
 */
@Serializable
data class SiteBook(
    val sites: List<Site> = emptyList(),
    val primaryId: String? = null,
) {
    init {
        require(sites.map { it.id }.toSet().size == sites.size) { "duplicate site ids" }
        require(sites.isEmpty() == (primaryId == null)) { "primary must be set iff sites exist" }
        require(primaryId == null || sites.any { it.id == primaryId }) { "unknown primary $primaryId" }
    }

    val primary: Site? get() = sites.firstOrNull { it.id == primaryId }
    val others: List<Site> get() = sites.filter { it.id != primaryId }

    operator fun get(id: String): Site? = sites.firstOrNull { it.id == id }

    /** Adds a site at the end. The first site added becomes primary. */
    fun add(site: Site, makePrimary: Boolean = false): SiteBook {
        require(get(site.id) == null) { "site ${site.id} already exists" }
        val newSites = sites + site
        return SiteBook(newSites, if (makePrimary || primaryId == null) site.id else primaryId)
    }

    fun update(site: Site): SiteBook {
        require(get(site.id) != null) { "unknown site ${site.id}" }
        return copy(sites = sites.map { if (it.id == site.id) site else it })
    }

    /** Removes a site. If it was primary, the first remaining site becomes primary. */
    fun delete(id: String): SiteBook {
        val remaining = sites.filter { it.id != id }
        val newPrimary = when {
            remaining.isEmpty() -> null
            id == primaryId -> remaining.first().id
            else -> primaryId
        }
        return SiteBook(remaining, newPrimary)
    }

    fun setPrimary(id: String): SiteBook {
        require(get(id) != null) { "unknown site $id" }
        return copy(primaryId = id)
    }

    fun move(fromIndex: Int, toIndex: Int): SiteBook {
        require(fromIndex in sites.indices && toIndex in sites.indices) { "index out of range" }
        val list = sites.toMutableList()
        list.add(toIndex, list.removeAt(fromIndex))
        return copy(sites = list)
    }

    /** Sites in display order with the primary first. */
    fun primaryFirst(): List<Site> = listOfNotNull(primary) + others
}
