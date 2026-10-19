package com.defnf.grid.domain.model

import java.io.IOException

/**
 * The SFTP server presented a different host key than the one trusted on earlier connections.
 *
 * Either the server was reinstalled or rekeyed, or something on the network is impersonating it.
 * The connection is refused until the user explicitly trusts [fingerprint].
 */
class HostKeyChangedException(
    val host: String,
    val port: Int,
    val fingerprint: String
) : IOException(
    "The server's identity has changed. If the server wasn't reinstalled, someone may be " +
        "intercepting the connection. New fingerprint: $fingerprint"
)

/** Finds a [HostKeyChangedException] anywhere in this throwable's cause chain. */
fun Throwable.findHostKeyChange(): HostKeyChangedException? =
    generateSequence(this) { it.cause }.take(8).filterIsInstance<HostKeyChangedException>().firstOrNull()
