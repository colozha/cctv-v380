package dev.cctv.v380.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun CameraPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun SectionIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    Icon(icon, null, modifier.size(40.dp)
        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50)).padding(10.dp),
        tint = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun CameraBadge(modifier: Modifier = Modifier) {
    Icon(CameraIcon, null, modifier.size(56.dp)
        .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(16.dp)).padding(14.dp),
        tint = MaterialTheme.colorScheme.onTertiary)
}

@Composable
internal fun StatusLabel(text: String, confirmed: Boolean, modifier: Modifier = Modifier) {
    val color = if (confirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Spacer(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
        Text(text, color = color, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun PanelHeading(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionIcon(icon)
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

// Small geometric symbols complement material-icons-core without shipping the extended icon library.
internal val WifiIcon = ImageVector.Builder("Wifi", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(1f, 8f); quadTo(12f, -1f, 23f, 8f); lineTo(20.5f, 10.5f)
        quadTo(12f, 3.5f, 3.5f, 10.5f); close()
        moveTo(5f, 12f); quadTo(12f, 6f, 19f, 12f); lineTo(16.5f, 14.5f)
        quadTo(12f, 10.5f, 7.5f, 14.5f); close()
        moveTo(9f, 16f); quadTo(12f, 13f, 15f, 16f); lineTo(12f, 19f); close()
        moveTo(10.5f, 21f); arcTo(1.5f, 1.5f, 0f, true, true, 13.5f, 21f)
        arcTo(1.5f, 1.5f, 0f, true, true, 10.5f, 21f); close()
    }
}.build()

internal val BookmarkIcon = ImageVector.Builder("Bookmark", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(5f, 2f); lineTo(19f, 2f); lineTo(19f, 22f); lineTo(12f, 17f); lineTo(5f, 22f); close()
        moveTo(7f, 4f); lineTo(7f, 18f); lineTo(12f, 14.5f); lineTo(17f, 18f); lineTo(17f, 4f); close()
    }
}.build()

internal val SaveIcon = ImageVector.Builder("Save", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(3f, 2f); lineTo(17f, 2f); lineTo(22f, 7f); lineTo(22f, 22f); lineTo(2f, 22f); lineTo(2f, 3f); close()
        moveTo(4f, 4f); lineTo(4f, 20f); lineTo(6f, 20f); lineTo(6f, 12f); lineTo(18f, 12f); lineTo(18f, 20f)
        lineTo(20f, 20f); lineTo(20f, 8f); lineTo(16f, 4f); lineTo(16f, 10f); lineTo(6f, 10f); lineTo(6f, 4f); close()
        moveTo(8f, 4f); lineTo(8f, 8f); lineTo(14f, 8f); lineTo(14f, 4f); close()
        moveTo(8f, 14f); lineTo(8f, 20f); lineTo(16f, 20f); lineTo(16f, 14f); close()
    }
}.build()

internal val StopIcon = ImageVector.Builder("Stop", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 20f); lineTo(4f, 20f); close()
    }
}.build()

internal val EyeIcon = ImageVector.Builder("Visibility", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
        moveTo(2f, 12f); quadTo(12f, -1f, 22f, 12f); quadTo(12f, 25f, 2f, 12f); close()
        moveTo(8f, 12f); arcTo(4f, 4f, 0f, true, true, 16f, 12f); arcTo(4f, 4f, 0f, true, true, 8f, 12f); close()
    }
}.build()

internal val EyeOffIcon = ImageVector.Builder("VisibilityOff", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
        moveTo(3f, 3f); lineTo(21f, 21f)
        moveTo(9f, 6f); quadTo(16f, 3f, 22f, 12f); quadTo(20f, 15f, 18f, 16f)
        moveTo(6f, 8f); quadTo(4f, 9f, 2f, 12f); quadTo(8f, 21f, 16f, 18f)
        moveTo(9f, 10f); quadTo(6f, 15f, 14f, 16f)
    }
}.build()

internal val LightIcon = ImageVector.Builder("Lampu", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(8f, 17f); curveTo(8f, 13f, 5f, 13f, 5f, 8f)
        arcTo(7f, 7f, 0f, true, true, 19f, 8f); curveTo(19f, 13f, 16f, 13f, 16f, 17f); close()
        moveTo(8f, 19f); lineTo(16f, 19f); lineTo(16f, 21f); lineTo(8f, 21f); close()
        moveTo(10f, 22f); lineTo(14f, 22f); lineTo(13f, 24f); lineTo(11f, 24f); close()
    }
}.build()

internal val MotionIcon = ImageVector.Builder("Gerakan", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(14f, 3f); arcTo(2.5f, 2.5f, 0f, true, true, 19f, 3f); arcTo(2.5f, 2.5f, 0f, true, true, 14f, 3f)
        moveTo(9f, 6f); lineTo(14f, 6f); lineTo(17f, 10f); lineTo(21f, 10f); lineTo(21f, 13f)
        lineTo(15f, 13f); lineTo(13f, 10f); lineTo(12f, 14f); lineTo(16f, 17f); lineTo(16f, 23f)
        lineTo(13f, 23f); lineTo(13f, 19f); lineTo(9f, 16f); lineTo(7f, 21f); lineTo(2f, 21f)
        lineTo(2f, 18f); lineTo(5f, 18f); lineTo(8f, 10f); lineTo(5f, 12f); lineTo(3f, 10f); close()
    }
}.build()

internal val PatrolIcon = ImageVector.Builder("Patroli", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.5f, strokeLineCap = StrokeCap.Round) {
        moveTo(10f, 15f); lineTo(7f, 18f); arcTo(4f, 4f, 0f, true, true, 2f, 12f); lineTo(5f, 9f)
        moveTo(14f, 9f); lineTo(17f, 6f); arcTo(4f, 4f, 0f, true, true, 22f, 12f); lineTo(19f, 15f)
        moveTo(8f, 16f); lineTo(16f, 8f)
    }
}.build()

internal val PictureIcon = ImageVector.Builder("Gambar", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round) {
        moveTo(3f, 3f); lineTo(21f, 3f); lineTo(21f, 21f); lineTo(3f, 21f); close()
        moveTo(3f, 17f); lineTo(9f, 11f); lineTo(13f, 15f); lineTo(17f, 10f); lineTo(21f, 14f)
        moveTo(7f, 7f); lineTo(8f, 7f)
    }
}.build()
