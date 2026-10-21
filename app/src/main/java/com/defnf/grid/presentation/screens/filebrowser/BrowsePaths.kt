package com.defnf.grid.presentation.screens.filebrowser

/** Path rules for moving up through a connection's folders. */
internal object BrowsePaths {

    /**
     * The folder above [path], or null when [path] is the connection's [root] (or somehow outside
     * it), so neither back nor the ".." row can climb above the root.
     *
     * SMB paths are share-relative with `\` separators and `""` as the share's top level. SFTP paths
     * use `/`; a root typed with a trailing slash matches the same folder without one.
     */
    fun parentWithinRoot(path: String, root: String, isSmb: Boolean): String? {
        if (isSmb) {
            if (path.isEmpty()) return null
            return if (path.contains('\\')) path.substringBeforeLast('\\') else ""
        }

        val current = normalize(path)
        val top = normalize(root)
        if (current == top) return null
        val prefix = if (top == "/") "/" else "$top/"
        if (!current.startsWith(prefix)) return null
        return current.substringBeforeLast('/').ifEmpty { "/" }
    }

    /** Drops trailing slashes (but keeps `/` itself) so `/a/` and `/a` are the same folder. */
    private fun normalize(path: String): String = path.trimEnd('/').ifEmpty { "/" }
}
