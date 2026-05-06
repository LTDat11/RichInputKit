package io.github.ltdat11.richinputkit

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit

/**
 * RichTextField — ô nhập liệu hỗ trợ định dạng văn bản.
 *
 * Cách dùng cơ bản:
 * ```kotlin
 * val state = rememberRichTextState()
 * RichTextField(
 *     state    = state,
 *     modifier = Modifier.fillMaxWidth()
 * )
 * ```
 */
@Composable
fun RichTextField(
    state          : RichTextState,
    modifier       : Modifier        = Modifier,
    hint           : String          = "",
    textColor      : Color           = Color.Black,
    hintColor      : Color           = Color.Gray,
    fontSize       : TextUnit        = TextUnit.Unspecified,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    decorationBox  : @Composable (innerTextField: @Composable () -> Unit) -> Unit =
        { it() }
) {
    // Xây dựng AnnotatedString từ text + spans
    val annotatedString = remember(state.plainText, state.richSpans) {
        buildAnnotatedString {
            append(state.plainText)
            // Áp dụng từng RichSpan lên AnnotatedString
            for (span in state.richSpans) {
                val start = span.start.coerceIn(0, state.plainText.length)
                val end   = span.end.coerceIn(start, state.plainText.length)
                if (start >= end) continue

                addStyle(
                    style = span.toSpanStyle(),
                    start = start,
                    end   = end
                )
            }
        }
    }

    // Giữ TextFieldValue đồng bộ với annotatedString
    val textFieldValue = remember(annotatedString, state.selection) {
        TextFieldValue(
            annotatedString = annotatedString,
            selection       = state.selection
        )
    }

    BasicTextField(
        value          = textFieldValue,
        onValueChange  = { newValue ->
            // Khi user gõ/xoá: cập nhật state và điều chỉnh spans
            state.updateTextFieldValue(
                TextFieldValue(
                    text      = newValue.text,
                    selection = newValue.selection
                )
            )
            // Điều chỉnh spans khi text thay đổi độ dài
            state.adjustSpansAfterEdit(
                oldLength = state.plainText.length,
                newValue  = newValue
            )
        },
        modifier       = modifier,
        textStyle      = androidx.compose.ui.text.TextStyle(
            color    = textColor,
            fontSize = fontSize
        ),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        cursorBrush    = SolidColor(textColor),
        decorationBox  = decorationBox
    )
}

// ── Extension: RichSpan → SpanStyle cho Compose ──────────────────────────────

internal fun RichSpan.toSpanStyle(): SpanStyle = when (format) {
    FormatType.BOLD          -> SpanStyle(fontWeight = FontWeight.Bold)
    FormatType.ITALIC        -> SpanStyle(fontStyle  = FontStyle.Italic)
    FormatType.UNDERLINE     -> SpanStyle(textDecoration = TextDecoration.Underline)
    FormatType.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    FormatType.COLOR         -> SpanStyle(color = color ?: Color.Black)
}