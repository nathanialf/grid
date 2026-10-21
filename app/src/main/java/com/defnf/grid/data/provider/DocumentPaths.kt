package com.defnf.grid.data.provider

import com.defnf.grid.domain.model.Protocol

/**
 * Server path rules for the documents the provider hands out.
 *
 * Document IDs are `<connection id>:<server path>`, where the path is spelled the way the file
 * browser spells it: SFTP paths are absolute with `/`, SMB paths are share-relative with `\`, a
 * leading `\`, and `""` for the share's top level. Before 1.2.2 SMB IDs used `/` (with `/` as the
 * top level), so [normalize] still accepts those for URIs other apps were granted back then.
 */
internal object DocumentPaths {

    /** The folder a connection's root opens at: the SFTP starting directory, or the SMB share's top. */
    fun rootOf(protocol: Protocol, startingDirectory: String?): String = when (protocol) {
        Protocol.SMB -> ""
        Protocol.SFTP -> normalize(startingDirectory?.takeIf { it.isNotBlank() } ?: "/", protocol)
    }

    fun normalize(path: String, protocol: Protocol): String = when (protocol) {
        Protocol.SMB -> path.replace('/', '\\').trimEnd('\\').let {
            if (it.isEmpty() || it.startsWith('\\')) it else "\\$it"
        }
        Protocol.SFTP -> path.trimEnd('/').ifEmpty { "/" }
    }

    /** The folder holding [path], or null at the top of the server (or of a relative path). */
    fun parentOf(path: String, protocol: Protocol): String? {
        val normalized = normalize(path, protocol)
        return when (protocol) {
            Protocol.SMB -> if (normalized.isEmpty()) null else normalized.substringBeforeLast('\\')
            Protocol.SFTP -> when (val slash = normalized.lastIndexOf('/')) {
                -1 -> null
                0 -> if (normalized == "/") null else "/"
                else -> normalized.substring(0, slash)
            }
        }
    }

    fun nameOf(path: String, protocol: Protocol): String {
        val normalized = normalize(path, protocol)
        return when (protocol) {
            Protocol.SMB -> normalized.substringAfterLast('\\')
            Protocol.SFTP -> normalized.substringAfterLast('/')
        }
    }

    /** Whether [path] is [folder] or anywhere below it. SMB servers ignore case, so this does too. */
    fun isWithin(path: String, folder: String, protocol: Protocol): Boolean {
        val child = normalize(path, protocol)
        val parent = normalize(folder, protocol)
        return when (protocol) {
            Protocol.SMB -> parent.isEmpty() ||
                child.equals(parent, ignoreCase = true) ||
                child.startsWith("$parent\\", ignoreCase = true)
            Protocol.SFTP -> child == parent ||
                child.startsWith(if (parent == "/") "/" else "$parent/")
        }
    }
}
