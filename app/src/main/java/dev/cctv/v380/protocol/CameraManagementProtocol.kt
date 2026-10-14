package dev.cctv.v380.protocol

import dev.cctv.v380.session.CameraSettings
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Boundaries are client budgets; firmware limits must also be satisfied. */
enum class ManagementGroup(val label: String) { ALARM("Alarm dan deteksi gerakan"), LIGHT("Jadwal lampu"), MOTOR("Preset dan patroli"), DEVICE("Perangkat") }
data class ManagementAbilities(val presets: Int? = null, val patrol: Boolean? = null, val calibration: Boolean? = null,
    val restart: Boolean? = null, val lightSchedule: Boolean? = null, val motion: Boolean? = null, val motorControl: Int? = null,
    val lensType: Int? = null, val viewCount: Int? = null) {
    val flatArea: Boolean get() = lensType == 0 && viewCount == 1
}
data class ManagementSnapshot(val profileId: String, val revision: Long, val group: ManagementGroup, val settings: CameraSettings, val deviceId: String = "")
data class CameraTimeWindow(val start: Int, val end: Int, val enabled: Boolean, val mode: Int? = null,
    val source: String = "{}", val origin: Int? = null)
data class CameraSchedule(val days: Map<String, List<CameraTimeWindow>>, val source: String,
    val maximums: Map<String, Int?> = emptyMap())
data class CameraLightSchedule(val defaultAction: Int, val capability: Int, val total: Int,
    val windows: List<CameraTimeWindow>, val source: String)
data class CameraAlarmArea(val rows: Int, val columns: Int, val cells: List<Int>, val source: String)
data class CameraPresets(val used: List<Int>)
data class CameraPatrol(val enabled: Boolean, val timerEnabled: Boolean, val maxRoutes: Int, val maxPoints: Int,
    val routes: List<CameraPatrolRoute>, val source: String)
data class CameraPatrolRoute(val start: Int, val end: Int, val points: List<CameraPatrolPoint>, val source: String = "{}")
data class CameraPatrolPoint(val id: Int, val staySeconds: Int, val source: String = "{}")

object CameraManagementProtocol {
    const val ALARM_MAIN = 655616
    const val ALARM_SOUND = 655617
    const val MOTION_SENSITIVITY = 655619
    const val MOTION = 655620
    const val ALARM_SCHEDULE = 655621
    const val ALARM_AREA = 655622
    const val LIGHT_SCHEDULE = 327940
    const val PRESETS = 393472
    const val PATROL = 393473
    const val MODEL = 65796
    const val SOFTWARE = 65793
    const val MODULE = 65794
    const val HARDWARE = 65795
    const val TIMEZONE = 196864
    const val REBOOT = 66050
    const val PRESET_ACTION = 393729
    const val CALIBRATE = 393732
    // No terminal format enum or motor-running notification has been recovered. Never infer one.
    const val FORMAT_TERMINAL_VERIFIED = CameraSettingsProtocol.FORMAT_STATUS_VERIFIED
    const val PATROL_MOTOR_STATUS_VERIFIED = false
    const val MOTION_SENSITIVITY_VERIFIED = false
    // Vendor TimeBarInfo.tryCoverTimeBarInfo caps edits at five. Firmware acceptance remains untested.
    const val ALARM_EDITOR_LIMIT = 5
    const val MOTION_EVENT = 1
    val dayNames = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
    val dayLabels = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
    private val bools = setOf(ALARM_MAIN, ALARM_SOUND, MOTION)
    private val versionIds = setOf(MODEL, SOFTWARE, MODULE, HARDWARE)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")
    fun ids(group: ManagementGroup): List<Int> = when (group) {
        ManagementGroup.ALARM -> listOf(ALARM_MAIN, MOTION, ALARM_SOUND, ALARM_SCHEDULE, ALARM_AREA)
        ManagementGroup.LIGHT -> listOf(CameraSettingsProtocol.LIGHT_SENSITIVITY, LIGHT_SCHEDULE)
        ManagementGroup.MOTOR -> listOf(PRESETS, PATROL)
        ManagementGroup.DEVICE -> listOf(MODEL, SOFTWARE, MODULE, HARDWARE)
    }
    fun getParams(group: ManagementGroup): JSONObject = JSONObject().put("dplist", JSONArray(ids(group)))

    fun abilities(data: JSONObject): ManagementAbilities {
        fun entry(array: JSONArray?, index: Int): Int? {
            if (array == null || array.length() <= index) return null
            return integer(JSONObject().put("v", array.opt(index)), "v").also { if (it < 0) malformed() }
        }
        if (data.has("msc") && data.opt("msc") !is JSONArray) malformed()
        val views = data.optJSONArray("msc")
        val ctrl = if (views != null && views.length() > 0) {
            if (views.length() > 4) malformed()
            (views.optJSONObject(0) ?: malformed()).optJSONArray("ctrl")
        } else data.optJSONArray("ctrl")
        return ManagementAbilities(entry(ctrl, 1)?.also { if (it > 256) malformed() },
            entry(ctrl, 2)?.let { it > 0 }, entry(ctrl, 0)?.let { it and 16 != 0 },
            entry(data.optJSONArray("misc"), 6)?.let { it and 1 != 0 },
            entry(data.optJSONArray("light"), 3)?.let { it > 0 },
            entry(data.optJSONArray("alert"), 2)?.let { it and 1 != 0 }, entry(ctrl, 0),
            entry(data.optJSONArray("misc"), 2), views?.length()?.takeIf { it > 0 })
    }

    fun parse(data: JSONObject, group: ManagementGroup, abilities: ManagementAbilities): CameraSettings {
        val requested = ids(group)
        val list = data.optJSONArray("dplist_result") ?: malformed()
        if (list.length() > requested.size) malformed()
        val values = linkedMapOf<Int, Any>(); val rejected = linkedMapOf<Int, Int>(); val seen = mutableSetOf<Int>()
        repeat(list.length()) { index ->
            val row = list.optJSONObject(index) ?: malformed()
            val id = integer(row, "dpid")
            if (id !in requested || !seen.add(id)) malformed()
            if (row.has("vs") && integer(row, "vs") !in -1..0) {
                // Another view cannot authorize this client's controls. No device error code is invented.
                return@repeat
            }
            val code = integer(row, "code")
            if (code != 1000) { rejected[id] = code; return@repeat }
            val value: Any = when (id) {
                in bools -> boolean(row.opt("value"))
                in versionIds -> (row.opt("value") as? String)?.takeIf { it.length in 1..512 && it.none(Char::isISOControl) } ?: malformed()
                ALARM_SCHEDULE -> alarmSchedule(obj(row))
                ALARM_AREA -> alarmArea(obj(row))
                LIGHT_SCHEDULE -> lightSchedule(obj(row))
                PRESETS -> presets(obj(row), abilities.presets)
                PATROL -> patrol(obj(row), abilities.presets)
                else -> integer(row, "value")
            }
            values[id] = value
        }
        return CameraSettings(values.toMap(), rejected.toMap())
    }

    fun setParams(changes: Map<Int, Any>, before: CameraSettings, abilities: ManagementAbilities): JSONObject {
        require(changes.isNotEmpty() && changes.size <= 10)
        if (changes[ALARM_MAIN] == true && (before.values[ALARM_SCHEDULE] as? CameraSchedule)?.days?.values?.all { it.isEmpty() } == true &&
            ALARM_SCHEDULE !in changes) throw IllegalArgumentException("Empty alarm schedule")
        val rows = JSONArray()
        changes.forEach { (id, value) ->
            require(id in before.values)
            val wire: Any = when (id) {
                in bools -> { require(value is Boolean); value }
                CameraSettingsProtocol.LIGHT_SENSITIVITY -> { require(value is Int && value in 1..3); value }
                ALARM_SCHEDULE -> {
                    require(value is CameraSchedule && before.values[id] is CameraSchedule)
                    require(alarmScheduleError(value, before.values[id] as CameraSchedule,
                        changes[ALARM_MAIN] as? Boolean ?: before.bool(ALARM_MAIN),
                        changes[MOTION] as? Boolean ?: before.bool(MOTION), abilities.motion) == null)
                    scheduleWire(value, before.values[id] as CameraSchedule)
                }
                LIGHT_SCHEDULE -> {
                    require(abilities.lightSchedule == true && value is CameraLightSchedule)
                    val old = before.values[id] as? CameraLightSchedule ?: throw IllegalArgumentException()
                    require(value.capability == old.capability && value.defaultAction in 0..2)
                    // Vendor UI creates one slot. Preserve larger existing configurations without inventing capacity.
                    require(value.windows.size <= maxOf(1, old.windows.size) && value.total == value.windows.size)
                    validateWindows(value.windows)
                    JSONObject(old.source).put("defaultAction", value.defaultAction).put("total", value.windows.size)
                        .put("time_schedule", JSONArray(value.windows.map { w ->
                            windowWire(w.copy(source = old.windows.firstOrNull { it.source == w.source }?.source ?: "{}"), true)
                        }))
                }
                ALARM_AREA -> {
                    require(abilities.flatArea && value is CameraAlarmArea)
                    CameraAreaProtocol.write(value, before.values[id] as? CameraAlarmArea ?: throw IllegalArgumentException())
                }
                PATROL -> {
                    require(abilities.patrol == true && value is CameraPatrol && !value.enabled)
                    val old = before.values[id] as? CameraPatrol ?: throw IllegalArgumentException()
                    require(!old.enabled && !old.timerEnabled && !value.timerEnabled && value.maxRoutes == old.maxRoutes && value.maxPoints == old.maxPoints)
                    require(value.routes.size <= old.maxRoutes)
                    val slots = before.values[PRESETS] as? CameraPresets ?: throw IllegalArgumentException()
                    val routes = value.routes.mapIndexed { index, route ->
                        require(route.points.size in 1..old.maxPoints && route.start < route.end)
                        require(route.points.map { it.id }.distinct().size == route.points.size)
                        route.points.forEach { point ->
                            val unchanged = old.routes.getOrNull(index)?.points?.any { it.id == point.id && it.staySeconds == point.staySeconds } == true
                            require(point.id in slots.used && (unchanged || point.staySeconds in 60..3600 && point.staySeconds % 60 == 0))
                        }
                        JSONObject(old.routes.getOrNull(index)?.source ?: "{}").put("s_time", time(route.start)).put("e_time", time(route.end))
                            .put("pos_cnt", route.points.size).put("auto_position", JSONArray(route.points.map {
                                JSONObject(old.routes.getOrNull(index)?.points?.firstOrNull { p -> p.id == it.id }?.source ?: "{}")
                                    .put("id", it.id).put("stay_sec", it.staySeconds)
                            }))
                    }
                    JSONObject(old.source).put("ptz_auto_route_cnt", routes.size).put("ptz_auto_route_info", JSONArray(routes))
                }
                else -> throw IllegalArgumentException("Property is read only or not verified")
            }
            rows.put(JSONObject().put("dpid", id).put("value", wire))
        }
        return JSONObject().put("dplist", rows)
    }

    /** Always preserve unowned mode bits and unknown vendor object fields. */
    private fun scheduleWire(value: CameraSchedule, old: CameraSchedule): JSONObject {
        val result = JSONObject(old.source)
        dayNames.forEach { day ->
            val windows = value.days.getValue(day); val previous = old.days.getValue(day)
            result.put(day, result.getJSONObject(day).put("cnt", windows.size)
                .put("sche", JSONArray(windows.map { w ->
                    windowWire(w.copy(source = w.origin?.let { previous[it].source } ?: "{}"), false)
                })))
        }
        return result
    }

    /** max has no vendor UI consumer: use it only as an additional conservative ceiling. */
    fun alarmIntervalLimit(before: CameraSchedule, day: String): Int =
        minOf(ALARM_EDITOR_LIMIT, before.maximums[day] ?: before.days.getValue(day).size)

    // createAlarmConfigPidList proves full-day, enabled, motion-only defaults. No metadata is cloned.
    fun newAlarmInterval() = CameraTimeWindow(0, 86399, true, MOTION_EVENT)

    fun alarmWindowsError(windows: List<CameraTimeWindow>): String? = when {
        windows.any { it.start !in 0..86399 || it.end !in 0..86399 || it.start >= it.end } ->
            "Waktu mulai harus sebelum selesai. Jadwal lintas tengah malam belum didukung."
        windows.filter { it.enabled }.sortedBy { it.start }.zipWithNext().any { (a, b) -> a.end > b.start } ->
            "Interval aktif bertumpang tindih. Ubah waktunya sebelum menyimpan."
        else -> null
    }

    fun alarmScheduleError(value: CameraSchedule, before: CameraSchedule, main: Boolean?, motion: Boolean?, supportedMotion: Boolean?): String? {
        if (value.days.keys != dayNames.toSet() || value.maximums != before.maximums || value.source != before.source)
            return "Nilai jadwal kamera berubah. Muat ulang atau batalkan draf."
        for (day in dayNames) {
            val windows = value.days.getValue(day); val old = before.days.getValue(day)
            val origins = windows.mapNotNull { it.origin }
            if (origins.distinct().size != origins.size || windows.any {
                    it.origin?.let { index -> old.getOrNull(index).let { original -> original == null || original.mode != it.mode } }
                        ?: (it.mode != MOTION_EVENT)
                }) return "Jenis peristiwa interval berubah. Batalkan draf dan muat ulang jadwal."
            val hasNew = windows.any { it.origin == null }
            if (windows.size > 32 || windows.size > alarmIntervalLimit(before, day) && (hasNew || windows.size > old.size))
                return "Jumlah interval melebihi batas editor atau nilai yang dilaporkan kamera."
            if (hasNew && (motion != true || supportedMotion != true))
                return "Aktifkan deteksi gerakan yang didukung kamera sebelum menambah interval."
            alarmWindowsError(windows)?.let { return "${dayLabels[dayNames.indexOf(day)]}: $it" }
        }
        if (value.days.values.all { it.isEmpty() } && main != false)
            return "Jadwal seluruh minggu kosong. Matikan Alarm utama atau tambahkan interval sebelum menyimpan."
        return null
    }

    fun sameBaseline(id: Int, a: Any?, b: Any?): Boolean = same(id, a, b) && when (id) {
        ALARM_SCHEDULE -> a is CameraSchedule && b is CameraSchedule && a.maximums == b.maximums && a.source == b.source
        ALARM_AREA -> a is CameraAlarmArea && b is CameraAlarmArea && CameraAreaProtocol.sameBaseline(a, b)
        else -> true
    }
    fun presetParams(slot: Int, operation: Int, capacity: Int): JSONObject {
        require(capacity in 1..256 && slot in 0 until capacity && operation in 1..3)
        return IoTControlProtocol.actionParams(PRESET_ACTION, JSONObject().put("ctrl_flag", operation)
            .put("pos_mark_id_cnt", 1).put("pos_mark_id", JSONArray().put(slot)), view = 0)
    }
    fun calibrateParams(): JSONObject = IoTControlProtocol.actionParams(CALIBRATE, view = 0)
    fun restartParams(): JSONObject = IoTControlProtocol.actionParams(REBOOT)
    fun same(id: Int, a: Any?, b: Any?): Boolean = when {
        a == null || b == null -> false
        id == ALARM_SCHEDULE && a is CameraSchedule && b is CameraSchedule -> a.days.mapValues { alarmWindowKeys(it.value) } == b.days.mapValues { alarmWindowKeys(it.value) }
        id == LIGHT_SCHEDULE && a is CameraLightSchedule && b is CameraLightSchedule -> a.defaultAction == b.defaultAction && a.windows.map(::windowKey) == b.windows.map(::windowKey)
        id == ALARM_AREA && a is CameraAlarmArea && b is CameraAlarmArea -> a.rows == b.rows && a.columns == b.columns && a.cells == b.cells
        id == PATROL && a is CameraPatrol && b is CameraPatrol -> a.enabled == b.enabled && a.routes.map(::routeKey) == b.routes.map(::routeKey)
        else -> a == b
    }
    private fun windowKey(w: CameraTimeWindow) = listOf(w.start, w.end, w.enabled, w.mode)
    private fun alarmWindowKeys(windows: List<CameraTimeWindow>) = windows
        .sortedWith(compareBy({ it.start }, { it.end }, { it.enabled }, { it.mode })).map(::windowKey)
    private fun routeKey(r: CameraPatrolRoute) = listOf(r.start, r.end, r.points.map { it.id to it.staySeconds })
    fun parseTime(value: String): Int {
        require(value.matches(Regex("[0-9]{1,2}:[0-9]{1,2}:[0-9]{1,2}")))
        val p = value.split(':').map(String::toInt)
        return LocalTime.of(p[0], p[1], p[2]).toSecondOfDay()
    }
    fun time(seconds: Int): String = LocalTime.ofSecondOfDay(seconds.toLong()).format(timeFormat)
    fun validateWindows(windows: List<CameraTimeWindow>) {
        require(windows.size <= 32)
        windows.forEach { require(it.start in 0..86399 && it.end in 0..86399 && (!it.enabled || it.start < it.end)) }
        windows.filter { it.enabled }.sortedBy { it.start }.zipWithNext().forEach { (a, b) -> require(a.end <= b.start) }
    }
    private fun window(value: JSONObject, light: Boolean): CameraTimeWindow = CameraTimeWindow(
        parseTime(value.getString("s_time")), parseTime(value.getString("e_time")),
        if (light) integer(value, "enable").let { if (it !in 0..1) malformed(); it == 1 } else boolean(value.opt("enable")),
        if (light) null else integer(value, "mode"), bounded(value))
    private fun windowWire(w: CameraTimeWindow, light: Boolean): JSONObject = JSONObject(w.source)
        .put("s_time", time(w.start)).put("e_time", time(w.end))
        .put("enable", if (light) if (w.enabled) 1 else 0 else w.enabled)
        .also { if (!light) it.put("mode", w.mode) }
    private fun alarmSchedule(value: JSONObject): CameraSchedule = CameraSchedule(dayNames.associateWith { day ->
        val d = value.optJSONObject(day) ?: malformed(); val a = d.optJSONArray("sche") ?: malformed()
        if (a.length() > 32 || integer(d, "cnt") != a.length()) malformed()
        List(a.length()) { window(a.optJSONObject(it) ?: malformed(), false).copy(origin = it) }.also(::validateWindows)
    }, bounded(value), dayNames.associateWith { day ->
        val d = value.getJSONObject(day)
        if (d.has("max")) integer(d, "max").also { if (it !in 0..32) malformed() } else null
    })
    private fun lightSchedule(value: JSONObject): CameraLightSchedule {
        val a = value.optJSONArray("time_schedule") ?: malformed()
        if (a.length() > 32 || integer(value, "total") != a.length()) malformed()
        val windows = List(a.length()) { window(a.optJSONObject(it) ?: malformed(), true) }.also(::validateWindows)
        return CameraLightSchedule(integer(value, "defaultAction"), integer(value, "capability"), integer(value, "total"), windows, bounded(value))
    }
    private fun alarmArea(value: JSONObject): CameraAlarmArea {
        val r = integer(value, "alarm_area_row_cnt"); val c = integer(value, "alarm_area_line_cnt")
        val a = value.optJSONArray("alarm_area") ?: malformed()
        if (r !in 1..16 || c !in 1..16 || a.length() != r * c) malformed()
        val cells = List(a.length()) { integer(JSONObject().put("v", a.opt(it)), "v").also { v -> if (v !in 0..1) malformed() } }
        return CameraAlarmArea(r, c, cells, bounded(value))
    }
    private fun presets(value: JSONObject, capacity: Int?): CameraPresets {
        val a = value.optJSONArray("pos_mark_details") ?: malformed()
        if (a.length() > 256 || integer(value, "pos_mark_cnt") != a.length()) malformed()
        val used = List(a.length()) { integer(JSONObject().put("v", a.opt(it)), "v") }
        if (used.distinct().size != used.size || used.any { it !in 0 until (capacity ?: 256) }) malformed()
        return CameraPresets(used)
    }
    private fun patrol(value: JSONObject, capacity: Int?): CameraPatrol {
        val a = value.optJSONArray("ptz_auto_route_info") ?: malformed()
        val max = integer(value, "ptz_cruise_route_total_cnt"); val points = integer(value, "ptz_cruise_route_position_max")
        if (max !in 0..32 || points !in 0..256 || a.length() > max || integer(value, "ptz_auto_route_cnt") != a.length()) malformed()
        val routes = List(a.length()) { i ->
            val route = a.optJSONObject(i) ?: malformed(); val list = route.optJSONArray("auto_position") ?: malformed()
            if (list.length() !in 1..points || integer(route, "pos_cnt") != list.length()) malformed()
            CameraPatrolRoute(parseTime(route.getString("s_time")), parseTime(route.getString("e_time")), List(list.length()) { j ->
                val p = list.optJSONObject(j) ?: malformed()
                CameraPatrolPoint(integer(p, "id").also { if (it !in 0 until (capacity ?: 256)) malformed() },
                    integer(p, "stay_sec").also { if (it !in 1..86400) malformed() }, bounded(p))
            }, bounded(route))
        }
        return CameraPatrol(boolean(value.opt("ptz_auto_enable")), boolean(value.opt("ptz_timer_enable")), max, points, routes, bounded(value))
    }
    private fun obj(row: JSONObject) = row.optJSONObject("value") ?: malformed()
    private fun bounded(obj: JSONObject): String = obj.toString().also { if (it.length > 16384) malformed() }
    private fun boolean(raw: Any?): Boolean = when (raw) {
        is Boolean -> raw
        is Number -> if (raw.toDouble() == 0.0) false else if (raw.toDouble() == 1.0) true else malformed()
        else -> malformed()
    }
    private fun integer(obj: JSONObject, field: String) = IoTCommonProtocol.exactInt(obj, field)
    private fun malformed(): Nothing = throw IoTProtocolException(IoTFailure.MalformedPacket)
}
