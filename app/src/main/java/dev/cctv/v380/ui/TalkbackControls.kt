package dev.cctv.v380.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cctv.v380.R
import dev.cctv.v380.media.TalkbackError
import dev.cctv.v380.session.TalkbackState

@Composable
internal fun TalkbackControls(state: TalkbackState, blocked: TalkbackError?, microphoneGranted: Boolean,
    permissionDenied: Boolean, onStart: () -> Unit, onStop: () -> Unit, onPermission: () -> Unit,
    onPermissionSettings: () -> Unit) {
    val active = state is TalkbackState.Starting || state is TalkbackState.Sending
    val stopping = state is TalkbackState.Stopping
    CameraPanel {
        PanelHeading(Icons.Default.PlayArrow, stringResource(R.string.talkback_title))
        Text(stringResource(when (state) {
            TalkbackState.Starting -> R.string.talkback_starting
            TalkbackState.Sending -> R.string.talkback_sending
            TalkbackState.Stopping -> R.string.talkback_stopping
            else -> R.string.talkback_idle
        }), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
        if (!active && !stopping) {
            val error = blocked ?: (state as? TalkbackState.Error)?.reason
            if (error != null) Text(stringResource(talkbackMessage(error)), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else if (!microphoneGranted) Text(stringResource(if (permissionDenied) R.string.talkback_permission_denied else R.string.talkback_permission_needed),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Text(stringResource(R.string.talkback_permission_ready), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = { if (active) onStop() else if (!microphoneGranted) onPermission() else onStart() },
            enabled = active || !stopping && blocked == null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(when (state) {
                TalkbackState.Starting -> R.string.talkback_cancel
                TalkbackState.Sending -> R.string.talkback_stop
                TalkbackState.Stopping -> R.string.talkback_stopping
                else -> R.string.talkback_start
            }))
        }
        if (!active && !stopping && permissionDenied && !microphoneGranted && blocked == null)
            TextButton(onClick = onPermissionSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.talkback_permission_settings))
            }
    }
}

internal fun talkbackMessage(error: TalkbackError): Int = when (error) {
    TalkbackError.NOT_READY -> R.string.talkback_not_ready
    TalkbackError.BUSY -> R.string.talkback_busy
    TalkbackError.CAPABILITY_UNKNOWN -> R.string.talkback_capability_unknown
    TalkbackError.UNSUPPORTED -> R.string.talkback_unsupported
    TalkbackError.METADATA_MISSING -> R.string.talkback_metadata_missing
    TalkbackError.METADATA_ABSENT -> R.string.talkback_metadata_absent
    TalkbackError.METADATA_INVALID -> R.string.talkback_metadata_invalid
    TalkbackError.METADATA_UNTRUSTED -> R.string.talkback_metadata_untrusted
    TalkbackError.METADATA_COMMAND -> R.string.talkback_metadata_command
    TalkbackError.METADATA_CIPHER -> R.string.talkback_metadata_cipher
    TalkbackError.UNSUPPORTED_AUDIO -> R.string.talkback_unsupported_audio
    TalkbackError.FORMAT_UNVERIFIED -> R.string.talkback_format_unverified
    TalkbackError.MICROPHONE_PERMISSION -> R.string.talkback_permission_denied
    TalkbackError.MICROPHONE_UNAVAILABLE -> R.string.talkback_microphone_unavailable
    TalkbackError.CAPTURE_FAILED -> R.string.talkback_capture_failed
    TalkbackError.BUFFER_FULL -> R.string.talkback_buffer_full
    TalkbackError.CAMERA_PERMISSION -> R.string.talkback_camera_permission
    TalkbackError.DEVICE_REJECTED -> R.string.talkback_device_rejected
    TalkbackError.NETWORK -> R.string.talkback_network_error
    TalkbackError.TIMEOUT -> R.string.talkback_timeout
    TalkbackError.MALFORMED -> R.string.talkback_malformed
}
