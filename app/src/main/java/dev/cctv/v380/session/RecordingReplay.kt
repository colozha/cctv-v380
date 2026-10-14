package dev.cctv.v380.session

import dev.cctv.v380.protocol.*
import dev.cctv.v380.media.RecordingExportException
import dev.cctv.v380.media.RecordingTimestampException
import dev.cctv.v380.transport.TcpChannel
import dev.cctv.v380.transport.deadlineAfterMillis
import kotlinx.coroutines.*
import org.json.JSONObject

/** The shared playback/download wire loop, also exercised by in-memory encrypted transcripts. */
internal class RecordingReplay(private val diagnostics: Diagnostics, private val nextRequestId: () -> Int) {
    suspend fun consume(channel: TcpChannel, session: IoTSession, key: ByteArray,
        recording: RecordingSegment, positionMs: Long, download: Boolean, isCurrent: () -> Boolean,
        sample: suspend (IoTMediaFrame, IoTVideoInfo) -> Unit) {
        val initial = nextRequestId()
        val pending = mutableSetOf(initial)
        var media: IoTMediaInfo? = null
        var confirmed = false
        var received = 0
        var receivedBytes = 0L
        var detail = DiagnosticDetail.REPLAY_START
        var command: Int? = null
        var packetBytes: Int? = null
        var timeId: Int? = null
        val stage = if (download) DiagnosticStage.DOWNLOAD else DiagnosticStage.PLAYBACK
        fun record(event: DiagnosticDetail, status: DiagnosticStatus = DiagnosticStatus.OK, error: Exception? = null) {
            diagnostics.record(stage, status, reason = error?.let(CameraSession::reason),
                code = generateSequence(error as Throwable?) { it.cause }.filterIsInstance<IoTProtocolException>().firstOrNull()?.deviceCode,
                detail = event, command = command, bytes = packetBytes,
                framesReceived = received, downloadBytes = receivedBytes, timeId = timeId)
        }
        suspend fun send(id: Int, params: JSONObject, method: Int = RecordingProtocol.CONTROL) {
            val request = IoTCommonProtocol.encodeRequest(methodId = method, requestId = id,
                params = params, secureType = 2, sessionId = session.sessionId, key = key)
            try { channel.write(request) } finally { request.fill(0) }
        }
        val early = ArrayList<IoTMediaFrame>()
        suspend fun deliver(frame: IoTMediaFrame) {
            detail = DiagnosticDetail.REPLAY_FRAME
            timeId = frame.format
            if (frame.format != 1 || frame.channel != 0 || frame.audio) throw IoTProtocolException(IoTFailure.MalformedPacket)
            val info = media ?: throw IoTProtocolException(IoTFailure.MalformedPacket)
            sample(frame, info.views.getOrNull(0) ?: info.video)
            received++
            receivedBytes = Math.addExact(receivedBytes, frame.bytes.size.toLong())
            if (received % 90 == 0) {
                detail = DiagnosticDetail.REPLAY_REFILL
                if (pending.size > 2) throw IoTProtocolException(IoTFailure.MalformedPacket)
                val id = nextRequestId(); pending.add(id)
                send(id, RecordingProtocol.nextFramesParams())
                record(DiagnosticDetail.REPLAY_REFILL)
            }
        }
        try {
            record(DiagnosticDetail.REPLAY_START, DiagnosticStatus.STARTED)
            send(initial, RecordingProtocol.startParams(recording, positionMs, download))
            while (currentCoroutineContext().isActive && isCurrent()) {
                val deadline = deadlineAfterMillis(15000)
                detail = DiagnosticDetail.READ_HEADER
                val header = IoTCommonProtocol.parseHeader(channel.readExact(16, deadline))
                command = header.command; packetBytes = header.bodySize
                if (header.secureType !in 1..2 || header.sessionId !in setOf(0, session.sessionId))
                    throw IoTProtocolException(IoTFailure.MalformedPacket)
                detail = DiagnosticDetail.READ_BODY
                val bytes = channel.readExact(header.bodySize, deadline)
                try {
                    if (header.command == IoTCommonProtocol.COMMAND_MEDIA) {
                        detail = DiagnosticDetail.REPLAY_FRAME
                        val frame = IoTMediaProtocol.parseFrame(header, bytes, key)
                        if (confirmed && media != null) deliver(frame) else {
                            if (early.size >= 3) throw IoTProtocolException(IoTFailure.MalformedPacket)
                            early.add(frame)
                        }
                    } else if (header.command in setOf(IoTCommonProtocol.COMMAND_IOT, IoTCommonProtocol.COMMAND_NOTIFY)) {
                        detail = DiagnosticDetail.REPLAY_CONTROL
                        val json = IoTCommonProtocol.decodeJson(header, bytes, key)
                        when (json.optInt("method_id", -1)) {
                            RecordingProtocol.CONTROL_RESPONSE -> {
                                if (header.secureType != 2) throw IoTProtocolException(IoTFailure.MalformedPacket)
                                val id = IoTCommonProtocol.exactInt(json, "id")
                                if (!pending.remove(id)) throw IoTProtocolException(IoTFailure.MalformedPacket)
                                IoTCommonProtocol.validateResponse(json, id, methodId = RecordingProtocol.CONTROL_RESPONSE).requireSuccess()
                                if (id == initial) confirmed = true
                            }
                            RecordingProtocol.INFORMATION -> {
                                detail = DiagnosticDetail.REPLAY_METADATA
                                val info = RecordingProtocol.information(json)
                                timeId = info.timeId
                                if (info.timeId != 1) throw IoTProtocolException(IoTFailure.MalformedPacket)
                                if (info.infoId == 1) { media = info.media; record(DiagnosticDetail.REPLAY_METADATA) }
                                if (info.infoId == 3) {
                                    detail = DiagnosticDetail.REPLAY_END
                                    if (!confirmed || received == 0 || early.isNotEmpty()) throw IoTProtocolException(IoTFailure.MalformedPacket)
                                    record(DiagnosticDetail.REPLAY_END)
                                    return
                                }
                            }
                            IoTCommonProtocol.DECODE_FEEDBACK -> {
                                if (header.command == IoTCommonProtocol.COMMAND_NOTIFY) {
                                    val params = IoTControlProtocol.decodeFeedbackParams(json)
                                    if (params != null) send(nextRequestId(), params, IoTCommonProtocol.DECODE_FEEDBACK)
                                }
                            }
                        }
                        if (confirmed && media != null && early.isNotEmpty()) {
                            for (frame in early) deliver(frame)
                            early.clear()
                        }
                    } else throw java.io.EOFException("Replay ended without completion notification")
                } finally { bytes.fill(0) }
            }
            throw CancellationException("Recording superseded")
        } catch (cancelled: CancellationException) {
            record(detail, DiagnosticStatus.CANCELLED)
            throw cancelled
        } catch (error: Exception) {
            record(when (error) {
                is RecordingTimestampException -> DiagnosticDetail.REPLAY_TIMESTAMP
                is RecordingExportException -> error.detail
                else -> detail
            }, DiagnosticStatus.FAILED, error)
            throw error
        }
    }
}

internal fun recordingTimestampDelta(timestamp: Long, firstTimestamp: Long, recording: RecordingSegment): Long {
    val delta = timestamp - firstTimestamp
    if (delta < 0 || delta > recording.durationMs + 60000) throw RecordingTimestampException()
    return delta
}
