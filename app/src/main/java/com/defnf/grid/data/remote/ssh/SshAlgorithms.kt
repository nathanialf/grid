package com.defnf.grid.data.remote.ssh

import org.apache.sshd.client.ClientBuilder
import org.apache.sshd.client.SshClient
import org.apache.sshd.common.NamedFactory
import org.apache.sshd.common.cipher.BuiltinCiphers
import org.apache.sshd.common.cipher.Cipher
import org.apache.sshd.common.compression.BuiltinCompressions
import org.apache.sshd.common.compression.Compression
import org.apache.sshd.common.kex.BuiltinDHFactories
import org.apache.sshd.common.mac.BuiltinMacs
import org.apache.sshd.common.mac.Mac
import org.apache.sshd.common.signature.BuiltinSignatures
import org.apache.sshd.common.signature.Signature

/**
 * The client's algorithm proposals, ordered fastest-first.
 *
 * RFC 4253 s7.1: the negotiated algorithm is the first entry on the *client's* list that the server
 * also offers. Order here therefore decides how much CPU the handshake costs, which on a phone is the
 * difference between a sub-millisecond elliptic-curve exchange and a multi-second finite-field one.
 *
 * Every list is passed through SSHD's `ignoreUnsupported = true` filter so the client never advertises
 * something this device's JCE cannot actually perform. Advertising an unsupported algorithm does not
 * degrade gracefully: a server that selects it produces a hard key-exchange failure.
 */
internal object SshAlgorithms {

    /**
     * Key exchange, fastest first.
     *
     * Deliberately excluded:
     *  - `dhg1`, `dhg14`, `dhgex` — SHA-1.
     *  - `dhg15_512`, `dhg17_512`, `dhg18_512` — 3072/6144/8192-bit modular exponentiation. There are
     *    two per handshake, ART has no Montgomery-multiply intrinsic, and SSHD supplies no
     *    private-value length so the provider uses a full-width exponent. Seconds of CPU on a phone.
     *  - `curve448` — X448 is not available on Android.
     *  - `sntrup761x25519*` — pure-Java post-quantum KEM with large packets, and every server that
     *    offers it also offers curve25519-sha256, so dropping it costs nothing.
     *
     * Retained as a compatibility tail: `dhg14_256` (2048-bit), `dhg16_512` (4096-bit), `dhgex256`.
     * These are slow, but they can only be selected by a server that offers nothing better.
     */
    private val KEY_EXCHANGE = listOf(
        BuiltinDHFactories.curve25519,
        BuiltinDHFactories.curve25519_libssh,
        BuiltinDHFactories.ecdhp256,
        BuiltinDHFactories.ecdhp384,
        BuiltinDHFactories.ecdhp521,
        BuiltinDHFactories.dhg14_256,
        BuiltinDHFactories.dhg16_512,
        BuiltinDHFactories.dhgex256
    )

    /**
     * Ciphers. SSHD's own default puts ChaCha20-Poly1305 first, which is right for a desktop JVM and
     * wrong here: SSHD implements ChaCha20 in pure Java, whereas AES-GCM goes through
     * Conscrypt/BoringSSL and uses the ARMv8 AES and PMULL instructions. AEAD modes also make MAC
     * negotiation moot, which trims the handshake.
     */
    private val CIPHERS = listOf(
        BuiltinCiphers.aes128gcm,
        BuiltinCiphers.aes256gcm,
        BuiltinCiphers.aes128ctr,
        BuiltinCiphers.aes256ctr,
        BuiltinCiphers.cc20p1305_openssh
    )

    /** SHA-256 has an ARMv8 instruction; SHA-512 does not until ARMv8.2, so 256 leads. */
    private val MACS = listOf(
        BuiltinMacs.hmacsha256etm,
        BuiltinMacs.hmacsha512etm,
        BuiltinMacs.hmacsha256,
        BuiltinMacs.hmacsha512,
        BuiltinMacs.hmacsha1
    )

    /**
     * Host-key and client-public-key signature algorithms. Cheap either way; the ordering exists to
     * stop the legacy SHA-1 `ssh-rsa` winning over `rsa-sha2-*`, which modern servers reject outright.
     * `rsa` stays at the tail purely so OpenSSH older than 7.2 still works.
     */
    private val SIGNATURES = listOf(
        BuiltinSignatures.ed25519,
        BuiltinSignatures.nistp256,
        BuiltinSignatures.rsaSHA512,
        BuiltinSignatures.rsaSHA256,
        BuiltinSignatures.nistp384,
        BuiltinSignatures.nistp521,
        BuiltinSignatures.rsa
    )

    fun apply(client: SshClient) {
        // `true` is ignoreUnsupported: filter each list by isSupported(). The previous code passed
        // `false`, which advertised algorithms this device may not implement.
        client.keyExchangeFactories =
            NamedFactory.setUpTransformedFactories(true, KEY_EXCHANGE, ClientBuilder.DH2KEX)
        client.cipherFactories =
            ArrayList<NamedFactory<Cipher>>(NamedFactory.setUpBuiltinFactories(true, CIPHERS))
        client.macFactories =
            ArrayList<NamedFactory<Mac>>(NamedFactory.setUpBuiltinFactories(true, MACS))
        client.signatureFactories =
            ArrayList<NamedFactory<Signature>>(NamedFactory.setUpBuiltinFactories(true, SIGNATURES))
        // Already the ClientBuilder default; set explicitly so it cannot drift. Compression costs CPU
        // and buys nothing for the binary payloads this app moves.
        client.compressionFactories = ArrayList<NamedFactory<Compression>>(
            NamedFactory.setUpBuiltinFactories(true, listOf(BuiltinCompressions.none))
        )
    }

    /**
     * Whether X25519 is available here, which decides whether the fastest key exchange is reachable
     * at all or whether the client falls back to ECDH P-256.
     *
     * Only meaningful once [apply] has run. Reading it earlier would reach
     * `SecurityUtils.register()` — which is one-shot — before [SshEnvironment.initOnce] has disabled
     * the BouncyCastle registrar, silently pinning SSHD to Android's stripped platform provider.
     * Callers should go through `SshClientProvider.isX25519Supported()`, which enforces the order.
     */
    fun isX25519Supported(): Boolean = BuiltinDHFactories.curve25519.isSupported
}
