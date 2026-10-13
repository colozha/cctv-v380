package dev.cctv.v380.ui

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cctv.v380.control.Direction
import dev.cctv.v380.session.*
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivePlayerScreen(model: CameraViewModel, profile: CameraProfile, state: SessionState, error: String?,
    onBack: () -> Unit, onEdit: () -> Unit, onConnect: (CharArray?) -> Unit, onDiagnostics: () -> Unit) {
    var password by remember(profile.id) { mutableStateOf("") }
    var overrideSaved by remember(profile.id) { mutableStateOf(false) }
    var surface by remember { mutableStateOf<Surface?>(null) }
    val capabilities by model.session.capabilities.collectAsStateWithLifecycle()
    val busy = state is SessionState.Connecting || state is SessionState.Authenticating ||
        state is SessionState.Negotiating || state is SessionState.Reconnecting
    val streaming = state is SessionState.Streaming
    BackHandler(onBack = onBack)
    DisposableEffect(profile.id) { onDispose { password = ""; model.surfaceLost(surface) } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { password = "" }
    LaunchedEffect(surface, state) {
        if (surface?.isValid == true && state is SessionState.Ready) model.surfaceReady(surface!!, profile.id)
    }
    Scaffold(topBar = { TopAppBar(title = { Text(profile.name) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
        actions = {
            IconButton(onClick = onDiagnostics) { Icon(Icons.Default.Info, "Diagnostik LAN") }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit kamera") }
        }) }) { insets ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(insets)) {
        val videoHeight = minOf(maxWidth * 9f / 16f, maxHeight * .5f)
        Column(Modifier.fillMaxSize()) {
            // Keep the native Surface outside the scrolling controls, including in landscape.
            Box(Modifier.fillMaxWidth().height(videoHeight).background(Color.Black), contentAlignment = Alignment.Center) {
                AndroidView(factory = { context -> SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            surface = holder.surface
                            model.surfaceReady(holder.surface, profile.id)
                        }
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            val old = surface
                            surface = null
                            model.surfaceLost(old)
                        }
                    })
                } }, modifier = Modifier.width(videoHeight * 16f / 9f).fillMaxHeight())
                if (!streaming) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                    else Icon(CameraIcon, null, Modifier.size(40.dp), tint = Color.White.copy(alpha = .7f))
                    Spacer(Modifier.height(12.dp))
                    Text(when (state) {
                        SessionState.Connecting -> "Menghubungkan kamera…"
                        SessionState.Authenticating -> "Memverifikasi akses lokal…"
                        is SessionState.Ready -> "Kamera terautentikasi"
                        is SessionState.Negotiating -> "Menunggu video…"
                        is SessionState.Reconnecting -> "Menyambungkan kembali (${state.attempt}/3)…"
                        is SessionState.Error -> "Video belum tersedia"
                        else -> "Belum terhubung"
                    }, color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().widthIn(max = 680.dp).verticalScroll(rememberScrollState()).imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Koneksi lokal", style = MaterialTheme.typography.titleLarge)
                Text("${profile.host}:${profile.port}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                (state as? SessionState.Error)?.let { ErrorNotice(it.reason.userMessage()) }
                error?.let { ErrorNotice(it, model::dismissError) }
                if (!streaming && !busy) {
                    if (!profile.hasSavedPassword || overrideSaved) OutlinedTextField(password, { password = it.take(4096) },
                        label = { Text("Password lokal kamera") }, supportingText = { Text("Gunakan akses perangkat kamera, terpisah dari akun V380 dan Wi-Fi rumah.") },
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (profile.hasSavedPassword) TextButton(onClick = { overrideSaved = !overrideSaved; password = "" }) {
                        Text(if (overrideSaved) "Gunakan akses tersimpan" else "Masukkan password lain")
                    }
                    Button(onClick = {
                        val entered = if (profile.hasSavedPassword && !overrideSaved) null else password.toCharArray()
                        password = ""; onConnect(entered)
                    }, enabled = surface?.isValid == true, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Sambungkan")
                    }
                }
                if (state !is SessionState.Idle) OutlinedButton(onClick = model::disconnect,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (busy) "Batalkan koneksi" else "Putuskan koneksi") }
                HorizontalDivider()
                Text("Kualitas video", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Quality.entries.forEach { quality -> FilterChip(
                        selected = (state as? SessionState.Streaming)?.quality == quality || (state as? SessionState.Negotiating)?.quality == quality,
                        onClick = { model.changeQuality(quality) }, enabled = streaming && quality in capabilities?.qualities.orEmpty(),
                        label = { Text(quality.name) }) }
                }
                Text("Kontrol kamera", style = MaterialTheme.typography.titleMedium)
                val ptz = streaming && capabilities?.ptz == true
                if (!ptz) Text("Kontrol tersedia setelah video berjalan dan kemampuan PTZ dikonfirmasi kamera.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.align(Alignment.CenterHorizontally), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PtzButton(Icons.Default.KeyboardArrowUp, "Atas", Direction.UP, ptz && capabilities?.verticalPtz == true, model)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PtzButton(Icons.Default.KeyboardArrowLeft, "Kiri", Direction.LEFT, ptz && capabilities?.horizontalPtz == true, model)
                        val scope = rememberCoroutineScope()
                        OutlinedButton(onClick = { scope.launch { model.stopPtz() } }, enabled = ptz,
                            modifier = Modifier.sizeIn(minWidth = 72.dp, minHeight = 56.dp)) { Text("Stop") }
                        PtzButton(Icons.Default.KeyboardArrowRight, "Kanan", Direction.RIGHT, ptz && capabilities?.horizontalPtz == true, model)
                    }
                    PtzButton(Icons.Default.KeyboardArrowDown, "Bawah", Direction.DOWN, ptz && capabilities?.verticalPtz == true, model)
                }
            }
        }
        }
    }
}

@Composable
private fun PtzButton(icon: ImageVector, name: String, direction: Direction, enabled: Boolean, model: CameraViewModel) {
    val scope = rememberCoroutineScope()
    OutlinedIconButton(onClick = {
        scope.launch { try { model.move(direction); delay(200) } finally { model.stopPtz() } }
    }, enabled = enabled, modifier = Modifier.size(56.dp).pointerInput(enabled, direction) {
        if (enabled) detectTapGestures(onPress = {
            coroutineScope {
                var repeat: Job? = null
                try {
                    model.move(direction)
                    repeat = launch {
                        while (isActive) {
                            delay(200)
                            withContext(NonCancellable) { withTimeoutOrNull(500) { model.renewPtz() } }
                        }
                    }
                    tryAwaitRelease()
                } finally {
                    withContext(NonCancellable) { repeat?.cancelAndJoin(); model.stopPtz() }
                }
            }
        })
    }) { Icon(icon, name) }
}
