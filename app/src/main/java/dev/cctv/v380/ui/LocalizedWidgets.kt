package dev.cctv.v380.ui

import android.annotation.SuppressLint
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import dev.cctv.v380.R
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val copyIds = ConcurrentHashMap<String, Int>()
private val weekdayPrefixes = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")

private fun copyResourceName(copy: String): String {
    val slug = Normalizer.normalize(copy, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_').take(48).trimEnd('_')
        .ifEmpty { "text" }
    val hash = copy.hashCode().toUInt().toString(16).padStart(8, '0')
    return "copy_${slug}_$hash"
}

@Composable
@SuppressLint("LocalContextResourcesRead") // getIdentifier is configuration independent; stringResource resolves the active locale.
internal fun localizedCopy(copy: String): String {
    Regex("^Kartu SD hampir penuh — (\\d+)% terpakai$").matchEntire(copy)?.let {
        return stringResource(R.string.sd_card_almost_full, it.groupValues[1].toInt())
    }
    weekdayPrefixes.firstOrNull { copy.startsWith("$it: ") }?.let { day ->
        return stringResource(R.string.alarm_day_error, localizedCopy(day), localizedCopy(copy.removePrefix("$day: ")))
    }
    val context = LocalContext.current
    val id = copyIds.getOrPut(copy) {
        context.resources.getIdentifier(
            copyResourceName(copy),
            "string",
            context.packageName,
        )
    }
    return if (id == 0) copy else stringResource(id)
}

@Composable
internal fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    MaterialText(
        text = localizedCopy(text), modifier = modifier, color = color, fontSize = fontSize,
        fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines,
        minLines = minLines, onTextLayout = onTextLayout, style = style,
    )
}

@Composable
internal fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    MaterialText(
        text = text, modifier = modifier, color = color, fontSize = fontSize,
        fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines,
        minLines = minLines, onTextLayout = onTextLayout, style = style,
    )
}

@Composable
internal fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = androidx.compose.material3.LocalContentColor.current,
) {
    MaterialIcon(imageVector, contentDescription?.let { localizedCopy(it) }, modifier, tint)
}
