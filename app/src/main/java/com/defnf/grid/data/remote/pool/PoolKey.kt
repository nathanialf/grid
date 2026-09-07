package com.defnf.grid.data.remote.pool

import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.Credential
import com.defnf.grid.domain.model.Protocol
import java.util.Objects

/**
 * Identity of a poolable session.
 *
 * Keyed on more than the connection id on purpose: folding the host, port, share and a fingerprint of
 * the credential into the key means that editing a server's address or rotating its password produces
 * a *different* key. A session opened under the old settings can then never be handed out, even if an
 * explicit eviction is missed somewhere — the stale entry simply idles out on its own.
 */
internal data class PoolKey(
    val connectionId: String,
    val protocol: Protocol,
    val hostname: String,
    val port: Int,
    val credentialId: String,
    val shareName: String?,
    val credentialFingerprint: Int
) {
    /**
     * Deliberately omits [credentialFingerprint]: it is derived from the password, and the generated
     * `data class` toString would otherwise print it into any log or crash report.
     */
    override fun toString(): String = "PoolKey($connectionId $protocol $hostname:$port)"
}

internal fun poolKeyOf(connection: Connection, credential: Credential): PoolKey = PoolKey(
    connectionId = connection.id,
    protocol = connection.protocol,
    hostname = connection.hostname,
    port = connection.effectivePort,
    credentialId = connection.credentialId,
    shareName = connection.shareName,
    credentialFingerprint = Objects.hash(
        credential.username,
        credential.password,
        credential.privateKey
    )
)
