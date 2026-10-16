package dev.cctv.v380.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Build
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.cctv.v380.protocol.*
import dev.cctv.v380.session.*
import dev.cctv.v380.R

@Composable
fun CameraManagementGroup(model: CameraViewModel, profile: CameraProfile, feature: CameraFeatureState,
    group: ManagementGroup, live: Boolean = false, embedded: Boolean = false,
    expanded: Boolean = true, unfinished: Boolean = false) {
    var confirmation by remember(profile.id, group) { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val authenticated = model.session.isAuthenticated(profile.id)
    val caps by model.session.capabilities.collectAsState()
    val ability = caps?.management ?: ManagementAbilities()
    val snapshot = feature.management[group]
    val values = snapshot?.settings?.values.orEmpty()
    val enabled = authenticated && !feature.busy && feature.pendingAp == null && !model.managementBlocked()
    fun value(id: Int): Any? = feature.managementDraft[id] ?: values[id]
    fun change(id: Int, v: Any) = model.draftManagement(group, id, v)
    val body: @Composable ColumnScope.() -> Unit = {
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
        if (!live || values[CameraManagementProtocol.PRESETS] !is CameraPresets || (ability.presets ?: 0) <= 0)
            OutlinedButton(onClick = { model.loadManagement(group) }, enabled = authenticated && !feature.busy && feature.pendingAp == null &&
            (group != ManagementGroup.ALARM || feature.areaPhase != AreaCapturePhase.EDITING),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (live)
                stringResource(R.string.load_presets_and_patrol) else stringResource(R.string.load_group, localizedCopy(group.label).lowercase())) }
        if (live) {
            if (feature.managementLoading == group) LinearProgressIndicator(Modifier.fillMaxWidth())
            feature.managementErrors[group]?.let { ErrorNotice(it) }
        }
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
                        SettingChoices("Sensitivitas lampu", sensitivity,
                            listOf(1 to "Rendah", 2 to "Normal", 3 to "Tinggi"), enabled) {
                            change(CameraSettingsProtocol.LIGHT_SENSITIVITY, it)
                        }
                    } else Text("Sensitivitas lampu belum dikenali.")
                }
                val schedule = value(CameraManagementProtocol.LIGHT_SCHEDULE) as? CameraLightSchedule
                Text("Zona waktu kamera belum dikonfirmasi.", style = MaterialTheme.typography.bodySmall)
                Text("Jadwal dijalankan kamera dan tetap berlaku saat aplikasi ditutup.", style = MaterialTheme.typography.bodySmall)
                if (schedule != null) {
                    SettingChoices("Lampu di luar jadwal", schedule.defaultAction,
                        listOf(0 to "Otomatis", 1 to "Hidup", 2 to "Mati"), enabled && ability.lightSchedule == true) {
                        change(CameraManagementProtocol.LIGHT_SCHEDULE, schedule.copy(defaultAction = it))
                    }
                    if (schedule.windows.isEmpty()) Text("Belum ada interval jadwal. Tambahkan waktu agar lampu mengikuti jadwal.")
                    schedule.windows.forEachIndexed { i, window ->
                        ManagementTimeWindow(window, enabled && ability.lightSchedule == true,
                            stringResource(R.string.schedule_number, i + 1)) { updated ->
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
                if (live && slots != null && ability.presets != null && ability.presets!! > 0) CameraPanel {
                    var slot by remember(profile.id) { mutableIntStateOf(0) }
                    var menu by remember { mutableStateOf(false) }
                    val overwriteQuestion = stringResource(R.string.overwrite_preset_question, slot + 1)
                    val deleteQuestion = stringResource(R.string.delete_preset_question, slot + 1)
                    Text(stringResource(R.string.preset_count, slots.used.size, ability.presets ?: 0), style = MaterialTheme.typography.bodyMedium)
                    Box {
                        OutlinedButton(onClick = { menu = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.slot_status, slot + 1, stringResource(if (slot in slots.used) R.string.occupied else R.string.empty)))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            repeat(ability.presets!!.coerceAtMost(256)) { id -> DropdownMenuItem(text = { Text(if (id in slots.used)
                                stringResource(R.string.slot_status, id + 1, stringResource(R.string.occupied)) else stringResource(R.string.slot_status_empty, id + 1)) },
                                onClick = { slot = id; menu = false }) }
                        }
                    }
                    OutlinedButton(onClick = { model.loadManagement(group) }, enabled = enabled,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Muat preset dan patroli") }
                    OutlinedButton(onClick = {
                        if (slot in slots.used) confirmation = overwriteQuestion to { model.presetAction(slot, 1) }
                        else model.presetAction(slot, 1)
                    }, enabled = enabled && stoppedConfiguration, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(SaveIcon, null); Spacer(Modifier.width(8.dp)); Text("Simpan posisi") }
                    OutlinedButton(onClick = { model.presetAction(slot, 2) }, enabled = enabled && stoppedConfiguration && slot in slots.used,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Ke posisi") }
                    TextButton(onClick = { confirmation = deleteQuestion to { model.presetAction(slot, 3) } },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        enabled = enabled && stoppedConfiguration && slot in slots.used, modifier = Modifier.heightIn(min = 48.dp)) { Text("Hapus preset") }
                } else if (live) Text("Daftar preset atau kemampuan slot belum dikonfirmasi kamera.")
                if (patrol != null) {
                    val patrolContent: @Composable ColumnScope.() -> Unit = {
                    val patrolStatus = stringResource(if (patrol.enabled || patrol.timerEnabled)
                        R.string.patrol_active_status else R.string.patrol_inactive_status)
                    val unavailableStatus = stringResource(R.string.patrol_control_unavailable)
                    if (live) {
                        Text(patrolStatus)
                        Text(unavailableStatus, style = MaterialTheme.typography.bodySmall)
                    } else SettingsNotice("$patrolStatus\n$unavailableStatus", error = true)
                    patrol.routes.forEachIndexed { index, route ->
                        Text(stringResource(R.string.route_number, index + 1), style = MaterialTheme.typography.titleSmall)
                        val editable = enabled && ability.patrol == true && !patrol.enabled && !patrol.timerEnabled
                        fun updateRoute(updated: CameraPatrolRoute) = change(CameraManagementProtocol.PATROL,
                            patrol.copy(routes = patrol.routes.toMutableList().also { it[index] = updated }))
                ManagementTimeWindow(CameraTimeWindow(route.start, route.end, true), editable,
                    stringResource(R.string.patrol_time), toggle = false) {
                            updateRoute(route.copy(start = it.start, end = it.end))
                        }
                        route.points.forEachIndexed { pointIndex, point ->
                            Text(stringResource(R.string.route_position_order, pointIndex + 1, point.id + 1, point.staySeconds / 60), style = MaterialTheme.typography.bodyMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    val points = route.points.toMutableList()
                                    val previous = points[pointIndex - 1]; points[pointIndex - 1] = point; points[pointIndex] = previous
                                    updateRoute(route.copy(points = points))
                                }, enabled = editable && pointIndex > 0, modifier = Modifier.heightIn(min = 48.dp)) { Text("Naikkan urutan") }
                                TextButton(onClick = { updateRoute(route.copy(points = route.points.filterIndexed { i, _ -> i != pointIndex })) },
                                    enabled = editable && route.points.size > 1, modifier = Modifier.heightIn(min = 48.dp)) { Text("Hapus posisi") }
                            }
                            if (point.staySeconds in 60..3600 && point.staySeconds % 60 == 0) {
                                Text(stringResource(R.string.stop_duration_minutes, point.staySeconds / 60), style = MaterialTheme.typography.bodySmall)
                                Slider(value = point.staySeconds / 60f, onValueChange = { minutes ->
                                    val seconds = kotlin.math.round(minutes).toInt().coerceIn(1, 60) * 60
                                    updateRoute(route.copy(points = route.points.toMutableList().also { it[pointIndex] = point.copy(staySeconds = seconds) }))
                                }, valueRange = 1f..60f, steps = 58, enabled = editable,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
                            } else Text(stringResource(R.string.camera_stop_duration, point.staySeconds), style = MaterialTheme.typography.bodySmall)
                        }
                        val nextSlot = slots?.used?.firstOrNull { id -> route.points.none { it.id == id } }
                        OutlinedButton(onClick = { nextSlot?.let { updateRoute(route.copy(points = route.points + CameraPatrolPoint(it, 60))) } },
                            enabled = editable && nextSlot != null && route.points.size < patrol.maxPoints,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(nextSlot?.let {
                                stringResource(R.string.add_position_slot, it + 1)
                            } ?: stringResource(R.string.add_position)) }
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
                    if (live) CameraPanel(content = patrolContent)
                    else Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = patrolContent)
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
                    SettingsValue(label, values[id] as? String ?: "Tidak tersedia pada kamera ini")
                }
                val restartQuestion = stringResource(R.string.camera_restart_question, profile.name)
                OutlinedButton(onClick = { confirmation = restartQuestion to { model.restartCamera(profile) } },
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
            Text(stringResource(if (code == 1109) R.string.setting_not_allowed else R.string.setting_not_available, localizedCopy(label).lowercase()), style = MaterialTheme.typography.bodySmall)
        }
        if (snapshot != null && values.isEmpty()) Text("Pengaturan grup ini tidak tersedia atau tidak diizinkan pada kamera ini.")
        if (snapshot != null && group != ManagementGroup.ALARM && CameraManagementProtocol.ids(group).any { it in feature.managementDraft }) {
            Button(onClick = { model.saveManagement(group) }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.save_group, localizedCopy(group.label).lowercase())) }
        }
        if (live && feature.managementLoading == group && feature.busy) Text("Sedang memeriksa kamera…", style = MaterialTheme.typography.bodySmall)
    }
    val status: @Composable () -> Unit = {
        feature.managementErrors[group]?.let { SettingsNotice(it, error = true) }
        if (feature.managementLoading == group && feature.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Sedang memeriksa kamera…", style = MaterialTheme.typography.bodySmall)
        }
        if (CameraManagementProtocol.ids(group).any { it in feature.managementDraft }) SettingsNotice("Perubahan belum disimpan")
        if (group == ManagementGroup.DEVICE && feature.pendingRestart != null)
            SettingsNotice("Hasil restart belum diketahui. Buka kartu untuk memeriksa kamera; perintah tidak dikirim ulang.", error = true)
        if (group == ManagementGroup.MOTOR && feature.calibrationNeedsCheck)
            SettingsNotice("Status kalibrasi belum terverifikasi. Periksa bahwa gerak kamera telah berhenti.", error = true)
    }
    if (live) Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = body)
    else if (embedded) {
        status()
        if (expanded) {
            Text(localizedCopy(group.label), style = MaterialTheme.typography.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = body)
        }
    } else {
        val patrol = values[CameraManagementProtocol.PATROL] as? CameraPatrol
        val summary = when (group) {
            ManagementGroup.DEVICE -> listOfNotNull(
                (values[CameraManagementProtocol.MODEL] as? String)?.let { stringResource(R.string.device_model_summary, it) },
                (values[CameraManagementProtocol.SOFTWARE] as? String)?.let { stringResource(R.string.device_firmware_summary, it) }).joinToString("\n").ifEmpty { "Belum diperiksa" }
            ManagementGroup.MOTOR -> when {
                patrol == null -> "Belum diperiksa"
                patrol.enabled || patrol.timerEnabled -> "Konfigurasi patroli aktif; status gerak belum diketahui."
                else -> "Konfigurasi patroli tidak aktif."
            }
            else -> if (snapshot == null) "Belum diperiksa" else "Nilai kamera telah dibaca"
        }
        SettingsCard(profile.id, localizedCopy(group.label), if (unfinished) Icons.Default.Build else when (group) {
            ManagementGroup.LIGHT -> LightIcon
            ManagementGroup.ALARM -> MotionIcon
            ManagementGroup.MOTOR -> PatrolIcon
            ManagementGroup.DEVICE -> Icons.Default.Settings
        }, if (unfinished && snapshot == null) null else localizedCopy(summary),
            flat = unfinished, badge = if (unfinished) stringResource(R.string.settings_not_working) else null,
            canCollapse = group != ManagementGroup.ALARM ||
                feature.areaPhase !in setOf(AreaCapturePhase.CAPTURING, AreaCapturePhase.EDITING),
            status = { status() }, content = body)
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
                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.schedule_day, localizedCopy(dayName))) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    p.dayNames.forEachIndexed { index, name ->
                        DropdownMenuItem(text = { Text(stringResource(R.string.day_interval_count, localizedCopy(names[index]), schedule.days.getValue(name).size)) },
                            onClick = { day = name; menu = false })
                    }
                }
            }
            key(day) {
                val windows = schedule.days.getValue(day)
                val limit = p.alarmIntervalLimit(original, day)
                fun update(w: List<CameraTimeWindow>) = onChange(p.ALARM_SCHEDULE, schedule.copy(days = schedule.days + (day to w)))
                Text(stringResource(R.string.interval_limit, windows.size, limit), style = MaterialTheme.typography.labelLarge)
                if (windows.isEmpty()) Text(stringResource(R.string.no_intervals_day, localizedCopy(dayName)))
                windows.forEachIndexed { index, window ->
                    key(window.origin ?: -index - 1) {
                        Column(Modifier.fillMaxWidth().testTag("alarm-interval-$index"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ManagementTimeWindow(window, enabled, stringResource(R.string.interval_number, index + 1), showError = false) { updated ->
                                update(windows.toMutableList().also { it[index] = updated })
                            }
                            TextButton(onClick = { update(windows.filterIndexed { i, _ -> i != index }) }, enabled = enabled,
                                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.delete_interval, index + 1)) }
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
            else Text(stringResource(R.string.selected_detection_cells, it.rows, it.columns, it.cells.count { cell -> cell != 0 }))
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
            Text(stringResource(R.string.start_time, CameraManagementProtocol.time(window.start)))
        }
        OutlinedButton(onClick = { editingStart = false }, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Text(stringResource(R.string.end_time, CameraManagementProtocol.time(window.end)))
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

