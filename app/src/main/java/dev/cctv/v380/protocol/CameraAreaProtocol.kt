package dev.cctv.v380.protocol

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min
import kotlin.math.roundToInt

/** Only an uncropped, unrotated decoded image has a proven flat-grid mapping. */
data class VideoGeometry(val width: Int, val height: Int, val left: Int = 0, val top: Int = 0,
    val right: Int = width - 1, val bottom: Int = height - 1, val rotation: Int = 0) {
    val flat: Boolean get() = width in 1..8192 && height in 1..8192 && width.toLong() * height <= 33_554_432 &&
        left == 0 && top == 0 && right == width - 1 && bottom == height - 1 && rotation == 0
    fun copySize(): Pair<Int, Int> {
        require(flat)
        val scale = min(1.0, 1024.0 / maxOf(width, height))
        return maxOf(1, (width * scale).roundToInt()) to maxOf(1, (height * scale).roundToInt())
    }
}

data class AreaContext(val profileId: String, val deviceId: String, val revision: Long, val generation: Long,
    val orientation: Int)

/** Issued by the session after media has stopped. Identity is also checked by the session. */
class AreaReference internal constructor(val context: AreaContext, val baseline: CameraAlarmArea, val geometry: VideoGeometry) {
    fun matches(current: AreaContext, area: CameraAlarmArea): Boolean = context == current &&
        geometry.flat && CameraAreaProtocol.sameBaseline(baseline, area)
}

object CameraAreaProtocol {
    fun valid(area: CameraAlarmArea): Boolean = area.rows in 1..16 && area.columns in 1..16 &&
        area.cells.size == area.rows * area.columns && area.cells.all { it in 0..1 }

    // ponytail: exact metadata comparison may reject reordered JSON; normalize if firmware varies serialization.
    fun sameBaseline(a: CameraAlarmArea, b: CameraAlarmArea): Boolean = a.rows == b.rows && a.columns == b.columns &&
        a.cells == b.cells && a.source == b.source

    fun write(value: CameraAlarmArea, before: CameraAlarmArea): JSONObject {
        require(valid(value) && valid(before) && value.rows == before.rows && value.columns == before.columns)
        return JSONObject(before.source).put("alarm_area_row_cnt", before.rows).put("alarm_area_line_cnt", before.columns)
            .put("alarm_area", JSONArray(value.cells))
    }

    /** Image-local coordinates, independent of locale/RTL. Letterbox space is rejected by the caller. */
    fun cellAt(x: Float, y: Float, width: Float, height: Float, rows: Int, columns: Int): Int? {
        if (rows !in 1..16 || columns !in 1..16 || !x.isFinite() || !y.isFinite() || !width.isFinite() || !height.isFinite() ||
            width <= 0 || height <= 0 || x < 0 || y < 0 || x >= width || y >= height) return null
        return (y / height * rows).toInt().coerceAtMost(rows - 1) * columns +
            (x / width * columns).toInt().coerceAtMost(columns - 1)
    }
}
