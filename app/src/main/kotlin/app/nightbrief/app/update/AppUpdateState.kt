package app.nightbrief.app.update

/** UI state for the Tonight update banner. */
data class AppUpdateUiState(
    val available: AvailableUpdate? = null,
    /** Null when idle; 0..1 while downloading. */
    val progress: Float? = null,
    val needsInstallPermission: Boolean = false,
    val downloadFailed: Boolean = false,
)
