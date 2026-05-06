package io.github.ltdat11.richinputkit

import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * RichTextState — trái tim của thư viện.
 * Lưu toàn bộ trạng thái: nội dung, vị trí con trỏ, các đoạn được format.
 *
 * Cách dùng:
 *   val state = rememberRichTextState()
 *   RichTextField(state = state)
 */
class RichTextState {

    // TextFieldValue chứa text + vị trí con trỏ + vùng được chọn
    var textFieldValue by mutableStateOf(TextFieldValue())
        internal set

    // Danh sách các đoạn được format (bold, italic, màu sắc...)
    var richSpans by mutableStateOf<List<RichSpan>>(emptyList())
        internal set

    // Text thuần không có format
    val plainText: String get() = textFieldValue.text

    // Vùng text đang được chọn (selection)
    val selection: TextRange get() = textFieldValue.selection

    // Có đang chọn text không?
    val hasSelection: Boolean get() = !selection.collapsed

    // ── Cập nhật nội dung ────────────────────────────────────────────────

    internal fun updateTextFieldValue(value: TextFieldValue) {
        textFieldValue = value
    }

    // ── Áp dụng format ───────────────────────────────────────────────────

    /**
     * Toggle bold cho vùng đang chọn.
     * Nếu đã bold → bỏ bold. Nếu chưa → thêm bold.
     */
    fun toggleBold() = toggleFormat(FormatType.BOLD)

    fun toggleItalic() = toggleFormat(FormatType.ITALIC)

    fun toggleUnderline() = toggleFormat(FormatType.UNDERLINE)

    fun toggleStrikethrough() = toggleFormat(FormatType.STRIKETHROUGH)

    fun applyColor(color: androidx.compose.ui.graphics.Color) {
        if (!hasSelection) return
        val newSpan = RichSpan(
            start  = selection.min,
            end    = selection.max,
            format = FormatType.COLOR,
            color  = color
        )
        richSpans = mergeSpan(richSpans, newSpan)
    }

    // ── Kiểm tra format hiện tại ─────────────────────────────────────────

    fun isBoldActive(): Boolean = isFormatActive(FormatType.BOLD)
    fun isItalicActive(): Boolean = isFormatActive(FormatType.ITALIC)
    fun isUnderlineActive(): Boolean = isFormatActive(FormatType.UNDERLINE)
    fun isStrikethroughActive(): Boolean = isFormatActive(FormatType.STRIKETHROUGH)

    // ── Xoá format ───────────────────────────────────────────────────────

    fun clearFormat() {
        if (!hasSelection) return
        richSpans = richSpans.filter { span ->
            // Giữ lại span không giao với vùng đang chọn
            span.end <= selection.min || span.start >= selection.max
        }
    }

    /**
     * Điều chỉnh vị trí các spans khi người dùng gõ thêm hoặc xoá text.
     * Ví dụ: gõ thêm 3 ký tự ở vị trí 5 → tất cả spans sau vị trí 5 dịch +3
     */
    internal fun adjustSpansAfterEdit(oldLength: Int, newValue: TextFieldValue) {
        val newLength = newValue.text.length
        val diff = newLength - oldLength

        if (diff == 0) return

        // Vị trí con trỏ sau khi edit
        val cursorPos = newValue.selection.min

        richSpans = richSpans.mapNotNull { span ->
            when {
                // Span hoàn toàn trước vị trí edit → không đổi
                span.end <= cursorPos - diff.coerceAtLeast(0) -> span

                // Span hoàn toàn sau vị trí edit → dịch chuyển
                span.start >= cursorPos - diff.coerceAtLeast(0) -> span.copy(
                    start = (span.start + diff).coerceAtLeast(0),
                    end   = (span.end + diff).coerceAtLeast(0)
                )

                // Span bị xoá một phần → thu hẹp hoặc xoá
                else -> {
                    val newEnd = (span.end + diff).coerceAtLeast(span.start)
                    if (newEnd <= span.start) null // Span bị xoá hoàn toàn
                    else span.copy(end = newEnd)
                }
            }
        }.filter { it.start < it.end } // Xoá spans rỗng
    }

    // ── Lấy nội dung ─────────────────────────────────────────────────────

    /**
     * Chuyển nội dung sang HTML đơn giản.
     * Ví dụ: "Hello **world**" → "<b>world</b>"
     */
    fun toHtml(): String {
        val text = plainText
        if (richSpans.isEmpty()) return text

        val sb = StringBuilder()
        var lastIndex = 0

        // Sắp xếp spans theo vị trí
        val sorted = richSpans.sortedBy { it.start }

        for (span in sorted) {
            if (span.start > lastIndex) {
                sb.append(text.substring(lastIndex, span.start))
            }
            val content = text.substring(span.start.coerceAtMost(text.length),
                span.end.coerceAtMost(text.length))
            when (span.format) {
                FormatType.BOLD          -> sb.append("<b>$content</b>")
                FormatType.ITALIC        -> sb.append("<i>$content</i>")
                FormatType.UNDERLINE     -> sb.append("<u>$content</u>")
                FormatType.STRIKETHROUGH -> sb.append("<s>$content</s>")
                FormatType.COLOR         -> {
                    val hex = span.color?.let {
                        "#%06X".format(0xFFFFFF and it.hashCode())
                    } ?: "#000000"
                    sb.append("<span style=\"color:$hex\">$content</span>")
                }
            }
            lastIndex = span.end
        }

        if (lastIndex < text.length) sb.append(text.substring(lastIndex))
        return sb.toString()
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private fun toggleFormat(type: FormatType) {
        if (!hasSelection) return
        if (isFormatActive(type)) {
            // Bỏ format: xoá spans của type trong vùng chọn
            richSpans = richSpans.filter { span ->
                span.format != type ||
                        span.end <= selection.min ||
                        span.start >= selection.max
            }
        } else {
            // Thêm format mới
            val newSpan = RichSpan(
                start  = selection.min,
                end    = selection.max,
                format = type
            )
            richSpans = mergeSpan(richSpans, newSpan)
        }
    }

    private fun isFormatActive(type: FormatType): Boolean {
        if (!hasSelection) return false
        return richSpans.any { span ->
            span.format == type &&
                    span.start <= selection.min &&
                    span.end >= selection.max
        }
    }

    private fun mergeSpan(existing: List<RichSpan>, new: RichSpan): List<RichSpan> {
        // Xoá spans cũ cùng loại bị overlap, thêm span mới
        val filtered = existing.filter { span ->
            span.format != new.format ||
                    span.end <= new.start ||
                    span.start >= new.end
        }
        return filtered + new
    }
}

// ── Các kiểu format được hỗ trợ ─────────────────────────────────────────────

enum class FormatType {
    BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, COLOR
}

// ── Dữ liệu một đoạn được format ────────────────────────────────────────────

data class RichSpan(
    val start  : Int,
    val end    : Int,
    val format : FormatType,
    val color  : androidx.compose.ui.graphics.Color? = null
)

// ── Helper function để dùng trong Compose ────────────────────────────────────

@Composable
fun rememberRichTextState(): RichTextState {
    return remember { RichTextState() }
}