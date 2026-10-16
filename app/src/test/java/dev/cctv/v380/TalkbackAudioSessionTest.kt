package dev.cctv.v380

import dev.cctv.v380.media.TalkbackAudioSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TalkbackAudioSessionTest {
    @Test fun noOpFrontendKeepsPlainMicAndToleratesRepeatedClose() {
        val noOp = TalkbackAudioSession.NO_OP
        assertFalse(noOp.acquire())
        assertEquals(TalkbackAudioSession.SOURCE_MIC, noOp.recordSource())
        noOp.attachEchoCanceller(0)
        noOp.close()
        noOp.close()
    }
}
