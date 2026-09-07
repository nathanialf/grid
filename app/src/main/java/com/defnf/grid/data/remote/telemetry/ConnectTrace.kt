package com.defnf.grid.data.remote.telemetry

import android.os.SystemClock
import com.defnf.grid.util.GridLog

/** The distinct round-trip phases of opening a remote connection. */
enum class ConnectPhase {
    /** Hostname to address. Free when the user configured a literal IP. */
    RESOLVE,

    /** TCP handshake, and for SMB the protocol negotiate that rides on it. */
    TCP,

    /** SSH key exchange. The phase that unordered algorithm lists make expensive. */
    KEX,

    /** Authentication. For SMB this is the two NTLM round trips. */
    AUTH,

    /** SFTP subsystem open. */
    SUBSYSTEM,

    /** SMB tree connect. */
    SHARE
}

/**
 * Per-phase timing for one connect attempt.
 *
 * Without this, every claim about connect latency is guesswork: a slow open could be DNS, a distant
 * server, an expensive key exchange, or a serial share probe, and the fixes for those are unrelated.
 * The negotiated-algorithm notes matter just as much as the timings — they are what turns "we think
 * curve25519 was selected" into something you can read off a log line for a specific device and
 * server.
 *
 * Uses [SystemClock.elapsedRealtimeNanos] rather than [System.nanoTime] so measurements survive doze,
 * and rather than `System.currentTimeMillis()` so they cannot go backwards on a clock adjustment.
 *
 * Not thread-safe; one instance belongs to one connect attempt.
 */
class ConnectTrace private constructor(private val label: String) {

    private val startNs = SystemClock.elapsedRealtimeNanos()
    private var lastNs = startNs
    private val phases = LinkedHashMap<String, Long>()
    private val notes = LinkedHashMap<String, String>()

    fun mark(phase: ConnectPhase) = mark(phase.name)

    fun mark(name: String) {
        val now = SystemClock.elapsedRealtimeNanos()
        phases[name] = (now - lastNs) / NANOS_PER_MILLI
        lastNs = now
    }

    /**
     * Records a non-timing fact about the connection, such as the negotiated cipher. Never pass
     * anything credential-derived: these lines ship in release builds.
     */
    fun note(key: String, value: String?) {
        if (!value.isNullOrEmpty()) notes[key] = value
    }

    fun finish(outcome: String) {
        val totalMs = (SystemClock.elapsedRealtimeNanos() - startNs) / NANOS_PER_MILLI
        GridLog.i(
            GridLog.TAG_CONNECT,
            buildString {
                append(label).append(' ').append(outcome)
                append(" total=").append(totalMs).append("ms")
                phases.forEach { (name, ms) ->
                    append(' ').append(name.lowercase()).append('=').append(ms).append("ms")
                }
                notes.forEach { (key, value) -> append(' ').append(key).append('=').append(value) }
            }
        )
    }

    companion object {
        private const val NANOS_PER_MILLI = 1_000_000L

        fun forSftp(): ConnectTrace = ConnectTrace("sftp")
        fun forSmb(): ConnectTrace = ConnectTrace("smb")
        fun forWarmup(): ConnectTrace = ConnectTrace("warmup")
    }
}
