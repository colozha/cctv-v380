package dev.cctv.v380.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.cctv.v380.protocol.*
import dev.cctv.v380.session.*

@Composable
fun CameraManagementGroup(model: CameraViewModel, profile: CameraProfile, feature: CameraFeatureState,
    group: ManagementGroup, live: Boolean = false) {
    var expanded by remember(profile.id, group) { mutableStateOf(false) }
    var confirmation by remember(profile.id, group) { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val authenticated = model.session.isAuthenticated(profile.id)
    val caps by model.session.capabilities.collectAsState()
    val ability = caps?.management ?: ManagementAbilities()
    val snapshot = feature.management[group]
    val values = snapshot?.settings?.values.orEmpty()
    val enabled = authenticated && !feature.busy && feature.pendingAp == null && !model.managementBlocked()
    fun value(id: Int): Any? = feature.managementDraft[id] ?: values[id]
    fun change(id: Int, v: Any) = model.draftManagement(group, id, v)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = { expanded = !expanded },
            enabled = group != ManagementGroup.ALARM || feature.areaPhase !in setOf(AreaCapturePhase.CAPTURING, AreaCapturePhase.EDITING),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (expanded) "${group.label} · Tutup" else "${group.label} · Buka", style = MaterialTheme.typography.titleMedium)
        }
        if (expanded) {
            if (group == ManagementGroup.DEVICE && feature.pendingRestart != null) {
                Text(if (feature.pendingRestart.profileId == profile.id) "Hasil restart sebelumnya belum diketahui. Pemeriksaan tidak mengirim restart lagi."
                    else "Ada pemeriksaan restart pada kamera lain. Buka kamera tersebut untuk memulihkan status.")
                OutlinedButton(onClick = model::verifyPendingRestart, enabled = authenticated && !feature.busy && feature.pendingRestart.profileId == profile.id,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("Periksa kamera") }
                TextButton(onClick = { confirmation = "Tutup catatan lokal restart? Hasil kamera tetap belum diketahui. Tidak ada perintah yang dikirim atau dibatalkan." to model::closePendingRestart },
                    enabled = !feature.busy && feature.pendingRestart.profileId == profile.id, modifier = Modifier.heightIn(min = 48.dp)) { Text("Tutup catatan restart") }
            }
            if (group == ManagementGroup.MOTOR && feature.calibrationNeedsCheck) {
                Text(if (feature.pendingCalibration?.profileId == profile.id) "Status kalibrasi belum terverifikasi. Keluar layar tidak membatalkan gerak kamera."
                    else "Ada pemeriksaan kalibrasi pada kamera lain. Buka kamera tersebut sebelum menutup catatan gerak.")
                OutlinedButton(onClick = { confirmation = "Saya sudah memeriksa bahwa gerak kamera telah berhenti." to model::acknowledgeCalibration },
                    enabled = !feature.busy && feature.pendingCalibration?.profileId == profile.id, modifier = Modifier.heightIn(min = 48.dp)) { Text("Gerak kamera sudah berhenti") }
            }
            OutlinedButton(onClick = { model.loadManagement(group) }, enabled = authenticated && !feature.busy && feature.pendingAp == null &&
                (group != ManagementGroup.ALARM || feature.areaPhase != AreaCapturePhase.EDITING),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Muat ${group.label.lowercase()}") }
            if (feature.managementLoading == group) LinearProgressIndicator(Modifier.fillMaxWidth())
            feature.managementErrors[group]?.let { ErrorNotice(it) }
            if (snapshot == null) Text(if (authenticated) "Muat nilai dari kamera untuk melihat pengaturan yang tersedia."
                else "Sambungkan kamera terlebih dahulu.", style = MaterialTheme.typography.bodyMedium)
            else when (group) {
                ManagementGroup.ALARM -> {
                    val area = value(CameraManagementProtocol.ALARM_AREA) as? CameraAlarmArea
                    if (area != null && feature.areaPhase in setOf(AreaCapturePhase.CAPTURING, AreaCapturePhase.EDITING))
                        CameraAreaControls(model, profile, feature, area, enabled)
                    else CameraAlarmControls(snapshot.settings, feature.managementDraft, ability, enabled,
                        onChange = { id, value -> change(id, value) }, onSave = { model.saveManagement(group) },
                        onDiscard = { model.discardManagement(group) }, areaDraftReady = model.areaDraftReady(),
                        areaContent = { CameraAreaControls(model, profile, feature, it, enabled) })
                }
                ManagementGroup.LIGHT -> {
                    (value(CameraSettingsProtocol.LIGHT_SENSITIVITY) as? Int)?.let { sensitivity ->
                        if (sensitivity in 1..3) {
                            Text("Sensitivitas lampu", style = MaterialTheme.typography.titleSmall)
                            listOf(1 to "Rendah", 2 to "Normal", 3 to "Tinggi").forEach { (id, label) ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(sensitivity == id, enabled = enabled,
                                    role = Role.RadioButton, onValueChange = { change(CameraSettingsProtocol.LIGHT_SENSITIVITY, id) }), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = sensitivity == id, onClick = null, enabled = enabled)
                                    Text(label)
                                }
                            }
                        } else Text("Sensitivitas lampu belum dikenali.")
                    }
                    val schedule = value(CameraManagementProtocol.LIGHT_SCHEDULE) as? CameraLightSchedule
                    Text("Zona waktu kamera belum dikonfirmasi.", style = MaterialTheme.typography.bodySmall)
                    Text("Jadwal dijalankan kamera dan tetap berlaku saat aplikasi ditutup.", style = MaterialTheme.typography.bodySmall)
                    if (schedule != null) {
                        Text("Lampu di luar jadwal", style = MaterialTheme.typography.titleSmall)
                        listOf(0 to "Otomatis", 1 to "Hidup", 2 to "Mati").forEach { (mode, label) ->
                            FilterChip(selected = schedule.defaultAction == mode, onClick = {
                                change(CameraManagementProtocol.LIGHT_SCHEDULE, schedule.copy(defaultAction = mode))
                            }, enabled = enabled && ability.lightSchedule == true, label = { Text(label) })
                        }
                        if (schedule.windows.isEmpty()) Text("Belum ada interval jadwal. Tambahkan waktu agar lampu mengikuti jadwal.")
                        schedule.windows.forEachIndexed { i, window ->
                            ManagementTimeWindow(window, enabled && ability.lightSchedule == true, "Jadwal ${i + 1}") { updated ->
                                change(CameraManagementProtocol.LIGHT_SCHEDULE, schedule.copy(windows = schedule.windows.toMutableList().also { it[i] = updated }))
                            }
                        }
                        val original = values[CameraManagementProtocol.LIGHT_SCHEDULE] as? CameraLightSchedule
                        if (schedule.windows.size < maxOf(1, original?.windows?.size ?: 0)) OutlinedButton(onClick = {
                            val windows = schedule.windows + CameraTimeWindow(64800, 82800, true)
                            change(CameraManagementProtocol.LIGHT_SCHEDULE, schedule.copy(windows = windows, total = windows.size))
                        }, enabled = enabled && ability.lightSchedule == true, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Tambah interval jadwal") }
                        if (schedule.windows.isNotEmpty()) TextButton(onClick = {
                            val windows = schedule.windows.dropLast(1)
                            change(CameraManagementProtocol.LIGHT_SCHEDULE, schedule.copy(windows = windows, total = windows.size))
                        }, enabled = enabled && ability.lightSchedule == true, modifier = Modifier.heightIn(min = 48.dp)) { Text("Hapus interval terakhir") }
                        Text("Mode manual lampu dapat memengaruhi jadwal; periksa nilai lampu dasar setelah menyimpan.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                ManagementGroup.MOTOR -> {
                    val slots = values[CameraManagementProtocol.PRESETS] as? CameraPresets
                    val patrol = value(CameraManagementProtocol.PATROL) as? CameraPatrol
                    val stoppedConfiguration = ability.patrol == false || patrol != null && !patrol.enabled && !patrol.timerEnabled
                    if (live && slots != null && ability.presets != null && ability.presets!! > 0) {
                        var slot by remember(profile.id) { mutableIntStateOf(0) }
                        var menu by remember { mutableStateOf(false) }
                        Text("Preset tersimpan: ${slots.used.size}/${ability.presets}", style = MaterialTheme.typography.bodyMedium)
                        Box {
                            OutlinedButton(onClick = { menu = true }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text("Slot ${slot + 1}${if (slot in slots.used) " · Terisi" else " · Kosong"}") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                repeat(ability.presets!!.coerceAtMost(256)) { id -> DropdownMenuItem(text = { Text("Slot ${id + 1}${if (id in slots.used) " · Terisi" else ""}") },
                                    onClick = { slot = id; menu = false }) }
                            }
                        }
                        OutlinedButton(onClick = {
                            if (slot in slots.used) confirmation = "Timpa posisi yang tersimpan pada slot ${slot + 1}?" to { model.presetAction(slot, 1) }
                            else model.presetAction(slot, 1)
                        }, enabled = enabled && stoppedConfiguration, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Simpan posisi") }
                        OutlinedButton(onClick = { model.presetAction(slot, 2) }, enabled = enabled && stoppedConfiguration && slot in slots.used,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Ke posisi") }
                        TextButton(onClick = { confirmation = "Hapus preset pada slot ${slot + 1}?" to { model.presetAction(slot, 3) } },
                            enabled = enabled && stoppedConfiguration && slot in slots.used, modifier = Modifier.heightIn(min = 48.dp)) { Text("Hapus preset") }
                    } else if (live) Text("Daftar preset atau kemampuan slot belum dikonfirmasi kamera.")
                    if (patrol != null) {
                        Text(if (patrol.enabled || patrol.timerEnabled) "Konfigurasi patroli kamera aktif. Status gerak motor belum diketahui." else "Konfigurasi patroli kamera tidak aktif.")
                        Text("Aktivasi dan penghentian patroli belum tersedia sampai status motor dapat diverifikasi.", style = MaterialTheme.typography.bodySmall)
                        patrol.routes.forEachIndexed { index, route ->
                            Text("Rute ${index + 1}", style = MaterialTheme.typography.titleSmall)
                            val editable = enabled && ability.patrol == true && !patrol.enabled && !patrol.timerEnabled
                            fun updateRoute(updated: CameraPatrolRoute) = change(CameraManagementProtocol.PATROL,
                                patrol.copy(routes = patrol.routes.toMutableList().also { it[index] = updated }))
                            ManagementTimeWindow(CameraTimeWindow(route.start, route.end, true), editable, "Waktu patroli", toggle = false) {
                                updateRoute(route.copy(start = it.start, end = it.end))
                            }
                            route.points.forEachIndexed { pointIndex, point ->
                                Text("Urutan ${pointIndex + 1} · Slot ${point.id + 1} · ${point.staySeconds / 60} menit", style = MaterialTheme.typography.bodyMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    TextButton(onClick = {
                                        val points = route.points.toMutableList()
                                        val previous = points[pointIndex - 1]; points[pointIndex - 1] = point; points[pointIndex] = previous
                                        updateRoute(route.copy(points = points))
                                    }, enabled = editable && pointIndex > 0, modifier = Modifier.heightIn(min = 48.dp)) { Text("Naikkan urutan") }
                                    TextButton(onClick = { updateRoute(route.copy(points = route.points.filterIndexed { i, _ -> i != pointIndex })) },
                                        enabled = editable && route.points.size > 1, modifier = Modifier.heightIn(min = 48.dp)) { Text("Hapus posisi") }
                                }
                                if (point.staySeconds in 60..3600 && point.staySeconds % 60 == 0) {
                                    Text("Waktu berhenti: ${point.staySeconds / 60} menit", style = MaterialTheme.typography.bodySmall)
                                    Slider(value = point.staySeconds / 60f, onValueChange = { minutes ->
                                        val seconds = kotlin.math.round(minutes).toInt().coerceIn(1, 60) * 60
                                        updateRoute(route.copy(points = route.points.toMutableList().also { it[pointIndex] = point.copy(staySeconds = seconds) }))
                                    }, valueRange = 1f..60f, steps = 58, enabled = editable,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
                                } else Text("Waktu berhenti dari kamera: ${point.staySeconds} detik. Nilai ini dipertahankan.", style = MaterialTheme.typography.bodySmall)
                            }
                            val nextSlot = slots?.used?.firstOrNull { id -> route.points.none { it.id == id } }
                            OutlinedButton(onClick = { nextSlot?.let { updateRoute(route.copy(points = route.points + CameraPatrolPoint(it, 60))) } },
                                enabled = editable && nextSlot != null && route.points.size < patrol.maxPoints,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Tambah posisi${nextSlot?.let { " · Slot ${it + 1}" }.orEmpty()}") }
                            TextButton(onClick = { change(CameraManagementProtocol.PATROL, patrol.copy(routes = patrol.routes.filterIndexed { i, _ -> i != index })) },
                                enabled = editable, modifier = Modifier.heightIn(min = 48.dp)) { Text("Hapus rute") }
                        }
                        val firstSlot = slots?.used?.firstOrNull()
                        OutlinedButton(onClick = { firstSlot?.let {
                            change(CameraManagementProtocol.PATROL, patrol.copy(routes = patrol.routes + CameraPatrolRoute(0, 86399, listOf(CameraPatrolPoint(it, 60)))))
                        } }, enabled = enabled && ability.patrol == true && !patrol.enabled && !patrol.timerEnabled &&
                            firstSlot != null && patrol.routes.size < patrol.maxRoutes,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Tambah rute patroli") }
                        Text("Waktu rute mengikuti jam kamera; zona waktu belum dikonfirmasi. Mengubah rute tidak mengaktifkan patroli. Simpan preset terlebih dahulu untuk menambah posisi.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (!live) {
                        OutlinedButton(onClick = { confirmation = "Kalibrasi akan menggerakkan lensa kamera. Status akhir belum dapat dibaca; periksa geraknya sebelum melakukan aksi lain." to model::calibrateCamera },
                            enabled = enabled && ability.calibration == true && stoppedConfiguration, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Kalibrasi PTZ") }
                        if (ability.calibration != true) Text("Kemampuan kalibrasi belum dikonfirmasi kamera.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                ManagementGroup.DEVICE -> {
                    listOf(CameraManagementProtocol.MODEL to "Model", CameraManagementProtocol.SOFTWARE to "Software / firmware",
                        CameraManagementProtocol.HARDWARE to "Hardware", CameraManagementProtocol.MODULE to "Module").forEach { (id, label) ->
                        Text(label, style = MaterialTheme.typography.labelLarge)
                        Text(values[id] as? String ?: "Tidak tersedia pada kamera ini", style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedButton(onClick = { confirmation = "Restart kamera ${profile.name}? Tayangan dan proses perekaman aktif akan terputus sementara." to { model.restartCamera(profile) } },
                        enabled = enabled && ability.restart == true && feature.draft.isEmpty() && feature.managementDraft.isEmpty(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Restart kamera") }
                    if (ability.restart != true) Text("Kemampuan restart belum dikonfirmasi kamera.", style = MaterialTheme.typography.bodySmall)
                }
            }
            snapshot?.settings?.rejected?.forEach { (id, code) ->
                val label = when (id) {
                    CameraManagementProtocol.ALARM_MAIN -> "Alarm utama"
                    CameraManagementProtocol.MOTION -> "Deteksi gerakan"
                    CameraManagementProtocol.ALARM_SOUND -> "Suara alarm"
                    CameraManagementProtocol.ALARM_SCHEDULE -> "Jadwal alarm"
                    CameraManagementProtocol.ALARM_AREA -> "Area deteksi"
                    CameraManagementProtocol.LIGHT_SCHEDULE -> "Jadwal lampu"
                    CameraManagementProtocol.PRESETS -> "Daftar preset"
                    CameraManagementProtocol.PATROL -> "Patroli"
                    else -> "Sebagian pengaturan"
                }
                Text(if (code == 1109) "$label tidak diizinkan untuk akses kamera ini." else "$label tidak tersedia pada kamera ini.", style = MaterialTheme.typography.bodySmall)
            }
            if (snapshot != null && values.isEmpty()) Text("Pengaturan grup ini tidak tersedia atau tidak diizinkan pada kamera ini.")
            if (snapshot != null && group != ManagementGroup.ALARM && CameraManagementProtocol.ids(group).any { it in feature.managementDraft }) {
                Button(onClick = { model.saveManagement(group) }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Simpan ${group.label.lowercase()}") }
            }
            if (feature.managementLoading == group && feature.busy) Text("Sedang memeriksa kamera…", style = MaterialTheme.typography.bodySmall)
        }
    }
    confirmation?.let { (text, action) ->
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text("Konfirmasi tindakan") }, text = { Text(text) },
            confirmButton = { TextButton(onClick = { confirmation = null; action() }) { Text("Lanjutkan") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Batal") } })
    }
}

/** Concrete values/callbacks keep the form independent of authentication and transport. */
@Composable
fun CameraAlarmControls(before: CameraSettings, draft: Map<Int, Any>, ability: ManagementAbilities, enabled: Boolean,
    onChange: (Int, Any) -> Unit, onSave: () -> Unit, onDiscard: () -> Unit, areaDraftReady: Boolean = false,
    areaContent: (@Composable (CameraAlarmArea) -> Unit)? = null) {
    val p = CameraManagementProtocol
    fun value(id: Int): Any? = draft[id] ?: before.values[id]
    val schedule = value(p.ALARM_SCHEDULE) as? CameraSchedule
    val original = before.values[p.ALARM_SCHEDULE] as? CameraSchedule
    var day by remember(before) { mutableStateOf("mon") }
    val dirty = p.ids(ManagementGroup.ALARM).any { it in draft }
    val error = if (schedule != null && original != null && dirty)
        p.alarmScheduleError(schedule, original, value(p.ALARM_MAIN) as? Boolean, value(p.MOTION) as? Boolean, ability.motion) else null
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(p.ALARM_MAIN to "Alarm utama", p.MOTION to "Deteksi gerakan", p.ALARM_SOUND to "Suara alarm kamera").forEach { (id, label) ->
            (value(id) as? Boolean)?.let { ManagementSwitch(label, it, enabled) { v -> onChange(id, v) } }
        }
        Text("Deteksi gerakan dan alarm tidak mengubah pilihan Rekam peristiwa.", style = MaterialTheme.typography.bodySmall)
        if (schedule != null && original != null) {
            Text("Jadwal alarm", style = MaterialTheme.typography.titleSmall)
            Text("Mengikuti waktu kamera. Zona waktu kamera belum dikonfirmasi.", style = MaterialTheme.typography.bodySmall)
            var menu by remember { mutableStateOf(false) }
            val names = p.dayLabels
            val dayName = names[p.dayNames.indexOf(day)]
            Box {
                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Hari: $dayName") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    p.dayNames.forEachIndexed { index, name ->
                        DropdownMenuItem(text = { Text("${names[index]} · ${schedule.days.getValue(name).size} interval") },
                            onClick = { day = name; menu = false })
                    }
                }
            }
            key(day) {
                val windows = schedule.days.getValue(day)
                val limit = p.alarmIntervalLimit(original, day)
                fun update(w: List<CameraTimeWindow>) = onChange(p.ALARM_SCHEDULE, schedule.copy(days = schedule.days + (day to w)))
                Text("${windows.size} interval · Batas editor $limit", style = MaterialTheme.typography.labelLarge)
                if (windows.isEmpty()) Text("Belum ada interval pada $dayName.")
                windows.forEachIndexed { index, window ->
                    key(window.origin ?: -index - 1) {
                        Column(Modifier.fillMaxWidth().testTag("alarm-interval-$index"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ManagementTimeWindow(window, enabled, "Interval ${index + 1}", showError = false) { updated ->
                                update(windows.toMutableList().also { it[index] = updated })
                            }
                            TextButton(onClick = { update(windows.filterIndexed { i, _ -> i != index }) }, enabled = enabled,
                                modifier = Modifier.heightIn(min = 48.dp)) { Text("Hapus interval ${index + 1}") }
                        }
                    }
                }
                p.alarmWindowsError(windows)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                OutlinedButton(onClick = { update(windows + p.newAlarmInterval()) },
                    enabled = enabled && windows.size < limit && value(p.MOTION) == true && ability.motion == true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Tambah interval") }
                if (value(p.MOTION) != true || ability.motion != true)
                    Text("Aktifkan deteksi gerakan yang didukung kamera untuk menambah interval.", style = MaterialTheme.typography.bodySmall)
                if (original.maximums[day] == null)
                    Text("Batas kamera belum dilaporkan. Jumlah interval tidak diperluas melampaui nilai awal.", style = MaterialTheme.typography.bodySmall)
                Text("Interval baru berlaku sepanjang hari untuk gerakan. Sesuaikan waktunya sebelum menyimpan. Jenis peristiwa interval lama dipertahankan.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        (value(p.ALARM_AREA) as? CameraAlarmArea)?.let {
            if (areaContent != null) areaContent(it)
            else Text("Area deteksi: ${it.rows} × ${it.columns}, ${it.cells.count { cell -> cell != 0 }} sel dipilih")
        }
        Text("Sensitivitas gerakan belum tersedia melalui kontrak pengaturan lokal yang terverifikasi.", style = MaterialTheme.typography.bodySmall)
        if (dirty) {
            val areaInvalid = p.ALARM_AREA in draft && !areaDraftReady
            if (areaInvalid) ErrorNotice("Ambil gambar referensi area yang baru sebelum menyimpan.")
            val visibleDayError = schedule?.days?.get(day)?.let { p.alarmWindowsError(it) }
            if (error != null && error != "${p.dayLabels[p.dayNames.indexOf(day)]}: $visibleDayError") ErrorNotice(error)
            Button(onClick = onSave, enabled = enabled && error == null && !areaInvalid,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("alarm-save")) { Text("Simpan alarm dan deteksi gerakan") }
            TextButton(onClick = onDiscard, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag("alarm-discard")) { Text("Batalkan perubahan alarm") }
        }
    }
}

@Composable
private fun ManagementSwitch(label: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f)); Switch(checked, null, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagementTimeWindow(window: CameraTimeWindow, enabled: Boolean, label: String, toggle: Boolean = true,
    showError: Boolean = true, change: (CameraTimeWindow) -> Unit) {
    var editingStart by remember { mutableStateOf<Boolean?>(null) }
    if (toggle) ManagementSwitch(label, window.enabled, enabled) { change(window.copy(enabled = it)) }
    else Text(label, style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { editingStart = true }, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Text("Mulai ${CameraManagementProtocol.time(window.start)}")
        }
        OutlinedButton(onClick = { editingStart = false }, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Text("Selesai ${CameraManagementProtocol.time(window.end)}")
        }
    }
    if (showError && window.start >= window.end) Text("Waktu mulai harus sebelum selesai. Jadwal lintas tengah malam belum didukung.", color = MaterialTheme.colorScheme.error)
    editingStart?.let { start ->
        val seconds = if (start) window.start else window.end
        val state = rememberTimePickerState(initialHour = seconds / 3600, initialMinute = seconds % 3600 / 60, is24Hour = true)
        AlertDialog(onDismissRequest = { editingStart = null }, title = { Text(if (start) "Waktu mulai" else "Waktu selesai") },
            text = { TimeInput(state) }, confirmButton = { TextButton(onClick = {
                val selected = state.hour * 3600 + state.minute * 60 + seconds % 60
                change(if (start) window.copy(start = selected) else window.copy(end = selected)); editingStart = null
            }) { Text("Pilih waktu") } }, dismissButton = { TextButton(onClick = { editingStart = null }) { Text("Batal") } })
    }
}

