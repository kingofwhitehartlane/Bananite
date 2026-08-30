// ui/components/MultiScriptText.kt
package ir.mums.stufood.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import ir.mums.stufood.ui.theme.GanjnamehFamily
import ir.mums.stufood.ui.theme.MontserratFamily

/**
 * Renders mixed Latin/Persian text with the correct font per script.
 * Persian (Arabic) codepoints get Ganjnameh; everything else gets Montserrat.
 */
@Composable
fun MultiScriptText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified
) {
    val annotated = buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val isPersian = isPersianChar(text[i])
            // Consume the full run of same-script characters
            val start = i
            while (i < text.length && isPersianChar(text[i]) == isPersian) i++

            withStyle(
                SpanStyle(fontFamily = if (isPersian) GanjnamehFamily else MontserratFamily)
            ) {
                append(text, start, i)
            }
        }
    }

    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        fontWeight = fontWeight,
        color = color,
        fontSize = fontSize
    )
}

private fun isPersianChar(c: Char): Boolean =
    c in '\u0600'..'\u06FF' ||   // Arabic block (covers Persian)
    c in '\uFB50'..'\uFDFF' ||   // Arabic Presentation Forms-A
    c in '\uFE70'..'\uFEFF' ||   // Arabic Presentation Forms-B
    c == '\u200C' ||              // ZWNJ (nim-fasele)
    c == '\u061F' ||              // Arabic question mark
    c in '\u06F0'..'\u06F9' ||   // Extended Arabic-Indic digits (۰-۹)
    c in '\u0660'..'\u0669'      // Arabic-Indic digits