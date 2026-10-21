package com.defnf.grid.presentation.screens.connections

/**
 * Spaces at either end of a hostname, username, share or folder are legal but rarely meant, and
 * invisible in the field. Returns a warning to show under the field, or null; it never blocks saving.
 */
internal fun outerSpaceWarning(value: String): String? {
    val leading = value.firstOrNull()?.isWhitespace() == true
    val trailing = value.lastOrNull()?.isWhitespace() == true
    val where = when {
        leading && trailing -> "Starts and ends"
        leading -> "Starts"
        trailing -> "Ends"
        else -> return null
    }
    return "$where with a space. Double-check that's intended."
}
