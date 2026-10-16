package dev.cctv.v380.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import java.util.concurrent.atomic.AtomicBoolean

/** Talkback microphone front-end parity with the vendor `startSpeak` path: audio focus, communication
 *  routing and echo cancellation. One instance is owned by a single talkback attempt and must be closed on
 *  that attempt's cleanup path. See analysis/evidence/talkback-HSMediaPlayer-startSpeak.sanitized.json.
 *
 *  The vendor's own EchoCanceller is proprietary and is not reproduced; this uses the platform equivalents. */
interface TalkbackAudioSession : AutoCloseable {
    /** Best-effort focus/route request; a denial must not block capture. */
    fun acquire(): Boolean
    /** AudioRecord source: voice communication when platform echo cancellation exists, else plain mic. */
    fun recordSource(): Int
    /** Attaches platform echo cancellation to a concrete record session; no-op when unsupported. */
    fun attachEchoCanceller(audioSessionId: Int)
    override fun close()

    companion object {
        /** Mirrors MediaRecorder.AudioSource; kept literal so JVM tests never load Android classes. */
        const val SOURCE_MIC = 1
        const val SOURCE_VOICE_COMMUNICATION = 7

        /** Headless default: keeps the previous capture behaviour when the front-end is not enabled. */
        val NO_OP: TalkbackAudioSession = object : TalkbackAudioSession {
            override fun acquire() = false
            override fun recordSource() = SOURCE_MIC
            override fun attachEchoCanceller(audioSessionId: Int) = Unit
            override fun close() = Unit
        }

        fun android(context: Context): TalkbackAudioSession = AndroidTalkbackAudioSession(context)
    }
}

private class AndroidTalkbackAudioSession(context: Context) : TalkbackAudioSession {
    private val manager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val closed = AtomicBoolean(false)
    private var previousMode: Int? = null
    private var focusRequest: AudioFocusRequest? = null
    private var canceller: AcousticEchoCanceler? = null

    override fun acquire(): Boolean {
        val audio = manager ?: return false
        if (closed.get()) return false
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .build()
        val granted = audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        focusRequest = request
        previousMode = audio.mode
        runCatching { audio.mode = AudioManager.MODE_IN_COMMUNICATION }
        return granted
    }

    override fun recordSource(): Int =
        if (AcousticEchoCanceler.isAvailable()) MediaRecorder.AudioSource.VOICE_COMMUNICATION
        else MediaRecorder.AudioSource.MIC

    override fun attachEchoCanceller(audioSessionId: Int) {
        if (closed.get() || !AcousticEchoCanceler.isAvailable()) return
        canceller = runCatching { AcousticEchoCanceler.create(audioSessionId)?.also { it.enabled = true } }.getOrNull()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { canceller?.release() }
        canceller = null
        val audio = manager ?: return
        previousMode?.let { mode -> runCatching { audio.mode = mode } }
        previousMode = null
        focusRequest?.let { request -> runCatching { audio.abandonAudioFocusRequest(request) } }
        focusRequest = null
    }
}
