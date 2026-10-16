package dev.cctv.v380

import android.content.Context
import android.media.AudioManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.cctv.v380.media.TalkbackAudioSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic front-end coverage: no microphone, camera or network is used. */
@RunWith(AndroidJUnit4::class)
class TalkbackAudioFrontendTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun selectsValidSourceAndRestoresAudioModeOnClose() {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val before = manager.mode
        val session = TalkbackAudioSession.android(context)
        assertTrue(session.recordSource() in
            setOf(TalkbackAudioSession.SOURCE_MIC, TalkbackAudioSession.SOURCE_VOICE_COMMUNICATION))
        session.attachEchoCanceller(0)
        session.acquire()
        session.close()
        session.close()
        assertEquals(before, manager.mode)
    }
}
