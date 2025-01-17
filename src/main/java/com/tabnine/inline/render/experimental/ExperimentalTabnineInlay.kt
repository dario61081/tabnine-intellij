package com.tabnine.inline.render.experimental

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.util.Disposer
import com.tabnine.general.Utils
import com.tabnine.inline.render.TabnineInlay
import com.tabnine.prediction.TabNineCompletion
import java.awt.Rectangle
import java.util.stream.Collectors

class ExperimentalTabnineInlay : TabnineInlay {
    private var beforeSuffixInlay: Inlay<*>? = null
    private var afterSuffixInlay: Inlay<*>? = null
    private var blockInlay: Inlay<*>? = null

    override val offset: Int?
        get() = beforeSuffixInlay?.offset ?: afterSuffixInlay?.offset ?: blockInlay?.offset

    override fun getBounds(): Rectangle? {
        val result = beforeSuffixInlay?.bounds?.let { Rectangle(it) }

        result?.bounds?.let {
            afterSuffixInlay?.bounds?.let { after -> result.add(after) }
            blockInlay?.bounds?.let { blockBounds -> result.add(blockBounds) }
        }

        return result
    }

    override val isEmpty: Boolean
        get() = beforeSuffixInlay == null && afterSuffixInlay == null && blockInlay == null

    override fun register(parent: Disposable) {
        beforeSuffixInlay?.let {
            Disposer.register(parent, it)
        }
        afterSuffixInlay?.let {
            Disposer.register(parent, it)
        }
        blockInlay?.let {
            Disposer.register(parent, it)
        }
    }

    override fun clear() {
        beforeSuffixInlay?.let {
            Disposer.dispose(it)
            beforeSuffixInlay = null
        }
        afterSuffixInlay?.let {
            Disposer.dispose(it)
            afterSuffixInlay = null
        }
        blockInlay?.let {
            Disposer.dispose(it)
            blockInlay = null
        }
    }

    override fun render(editor: Editor, suffix: String, completion: TabNineCompletion, offset: Int) {
        val lines = Utils.asLines(suffix)
        val firstLine = lines[0]
        val endIndex = firstLine.indexOf(completion.oldSuffix)

        val instructions = determineRendering(lines, completion.oldSuffix)

        when (instructions.firstLine) {
            FirstLineRendering.NoSuffix -> {
                renderNoSuffix(editor, firstLine, completion, offset)
            }
            FirstLineRendering.SuffixOnly -> {
                renderAfterSuffix(endIndex, completion, firstLine, editor, offset)
            }
            FirstLineRendering.BeforeAndAfterSuffix -> {
                renderBeforeSuffix(firstLine, endIndex, editor, completion, offset)
                renderAfterSuffix(endIndex, completion, firstLine, editor, offset)
            }
            FirstLineRendering.None -> {}
        }

        if (instructions.shouldRenderBlock) {
            val otherLines = lines.stream().skip(1).collect(Collectors.toList())
            renderBlock(otherLines, editor, completion, offset)
        }
    }

    private fun renderBlock(
        lines: List<String>,
        editor: Editor,
        completion: TabNineCompletion,
        offset: Int
    ) {
        val blockElementRenderer = BlockElementRenderer(editor, lines, completion.deprecated)
        blockInlay = editor
            .inlayModel
            .addBlockElement(
                offset,
                true,
                false,
                1,
                blockElementRenderer
            )
    }

    private fun renderAfterSuffix(
        endIndex: Int,
        completion: TabNineCompletion,
        firstLine: String,
        editor: Editor,
        offset: Int
    ) {
        val afterSuffixIndex = endIndex + completion.oldSuffix.length
        if (afterSuffixIndex < firstLine.length) {
            afterSuffixInlay = renderInline(
                editor,
                firstLine.substring(afterSuffixIndex),
                completion,
                offset + completion.oldSuffix.length
            )
        }
    }

    private fun renderBeforeSuffix(
        firstLine: String,
        endIndex: Int,
        editor: Editor,
        completion: TabNineCompletion,
        offset: Int
    ) {
        val beforeSuffix = firstLine.substring(0, endIndex)
        beforeSuffixInlay = renderInline(editor, beforeSuffix, completion, offset)
    }

    private fun renderNoSuffix(
        editor: Editor,
        firstLine: String,
        completion: TabNineCompletion,
        offset: Int
    ) {
        beforeSuffixInlay = renderInline(editor, firstLine, completion, offset)
    }

    private fun renderInline(
        editor: Editor,
        before: String,
        completion: TabNineCompletion,
        offset: Int
    ): Inlay<InlineElementRenderer>? {
        val inline = InlineElementRenderer(editor, before, completion.deprecated)
        return editor
            .inlayModel
            .addInlineElement(offset, true, inline)
    }
}
