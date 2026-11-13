package app.nightbrief.app.update

/**
 * Compares SemVer-ish tags the way GitHub release checks need.
 *
 * Numeric segment by segment so 1.0.10 beats 1.0.9. A leading `v` is stripped.
 * Build metadata after `+` is ignored. A pre-release suffix after `-` sorts
 * before the plain release, so 0.2.0 is later than 0.2.0-beta.1.
 */
object SemVer {
    fun normalise(tag: String): String {
        val trimmed = tag.trim()
        return if (trimmed.startsWith("v", ignoreCase = true)) trimmed.substring(1) else trimmed
    }

    /** True when [candidate] is a later version than [current]. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = segments(candidate)
        val b = segments(current)
        if (a.isEmpty()) return false
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return comparePreRelease(preRelease(candidate), preRelease(current)) > 0
    }

    private fun preRelease(version: String): String {
        val core = normalise(version).substringBefore('+')
        val dash = core.indexOf('-')
        return if (dash == -1) "" else core.substring(dash + 1)
    }

    private fun comparePreRelease(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return 1
        if (b.isEmpty()) return -1
        val left = a.split('.')
        val right = b.split('.')
        val n = maxOf(left.size, right.size)
        for (i in 0 until n) {
            if (i >= left.size) return -1
            if (i >= right.size) return 1
            val lNum = left[i].toIntOrNull()
            val rNum = right[i].toIntOrNull()
            when {
                lNum != null && rNum != null -> if (lNum != rNum) return if (lNum > rNum) 1 else -1
                lNum != null -> return -1
                rNum != null -> return 1
                else -> {
                    val c = left[i].compareTo(right[i])
                    if (c != 0) return if (c > 0) 1 else -1
                }
            }
        }
        return 0
    }

    private fun segments(version: String): List<Int> {
        val core = normalise(version).substringBefore('+').substringBefore('-')
        return core.split('.').mapNotNull { it.trim().toIntOrNull() }
    }
}
