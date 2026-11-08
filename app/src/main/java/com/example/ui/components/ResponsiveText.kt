package com.example.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Responsive single-line text that never slices words "half up and half down" across lines.
 * If the text or currency amount is wider than its available container, it automatically
 * steps down the font size slightly (down to [minFontSizeSp]) so the entire word/amount fits cleanly.
 */
@Composable
fun AutoSizeSingleLineText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    textDecoration: TextDecoration? = null,
    minFontSizeSp: Float = 10f,
    stepFactor: Float = 0.90f
) {
    val initialFontSize = if (style.fontSize != TextUnit.Unspecified) style.fontSize.value else 14f
    var scaledFontSizeSp by remember(text, initialFontSize) { mutableFloatStateOf(initialFontSize) }
    var readyToDraw by remember(text, initialFontSize) { mutableStateOf(false) }

    Text(
        text = text,
        color = color,
        style = style.copy(fontSize = scaledFontSizeSp.sp),
        fontWeight = fontWeight ?: style.fontWeight,
        textAlign = textAlign,
        textDecoration = textDecoration,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.drawWithContent {
            if (readyToDraw) {
                drawContent()
            }
        },
        onTextLayout = { result ->
            if (result.didOverflowWidth && scaledFontSizeSp > minFontSizeSp) {
                val nextSize = (scaledFontSizeSp * stepFactor).coerceAtLeast(minFontSizeSp)
                if (nextSize < scaledFontSizeSp) {
                    scaledFontSizeSp = nextSize
                } else {
                    readyToDraw = true
                }
            } else {
                readyToDraw = true
            }
        }
    )
}
