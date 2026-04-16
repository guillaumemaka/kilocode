package ai.kilocode.client.session.ui

import ai.kilocode.client.session.model.MessageListModel
import ai.kilocode.client.session.model.MessageModelEvent
import ai.kilocode.rpc.dto.MessageDto
import com.intellij.openapi.Disposable
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.border.MatteBorder

/**
 * Scrollable panel displaying chat messages aligned to the top,
 * with an optional animated status indicator at the bottom.
 *
 * Passive view — all rendering is driven by [MessageModelEvent]s
 * from the [MessageListModel]. No public mutation methods.
 *
 * Inner panel uses [BoxLayout.Y_AXIS] for stacking, wrapped in a
 * [BorderLayout.NORTH] so messages stay top-aligned when the scroll
 * viewport is taller than the content.
 */
class MessageListUi(
    parent: Disposable,
    private val model: MessageListModel,
) : JPanel(BorderLayout()) {

    private val blocks = LinkedHashMap<String, MessageBlock>()

    private val inner = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = JBUI.Borders.empty(4, 8)
    }

    private val label = JBLabel().apply {
        foreground = UIUtil.getContextHelpForeground()
    }

    private val spinner = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
        isOpaque = false
        isVisible = false
        border = JBUI.Borders.empty(6, 0)
        alignmentX = LEFT_ALIGNMENT
        add(JBLabel(AnimatedIcon.Default()))
        add(label)
    }

    init {
        isOpaque = true
        background = UIUtil.getPanelBackground()
        inner.add(spinner)
        add(inner, BorderLayout.NORTH)

        model.addListener(parent) { event ->
            when (event) {
                is MessageModelEvent.MessageAdded -> onAdded(event.info)
                is MessageModelEvent.MessageRemoved -> onRemoved(event.id)
                is MessageModelEvent.PartText -> onPartText(event.messageId, event.partId, event.text)
                is MessageModelEvent.PartDelta -> onPartDelta(event.messageId, event.partId, event.delta)
                is MessageModelEvent.Error -> onError(event.message)
                is MessageModelEvent.StatusChanged -> onStatus(event.text)
                is MessageModelEvent.HistoryLoaded -> onHistory()
                is MessageModelEvent.Cleared -> onCleared()
            }
        }
    }

    private fun onAdded(info: MessageDto) {
        if (blocks.containsKey(info.id)) return
        val block = MessageBlock(info)
        blocks[info.id] = block
        inner.add(block, inner.componentCount - 1)
        refresh()
    }

    private fun onRemoved(id: String) {
        val block = blocks.remove(id) ?: return
        inner.remove(block)
        refresh()
    }

    private fun onPartText(messageId: String, partId: String, text: String) {
        blocks[messageId]?.setText(partId, text)
        refresh()
    }

    private fun onPartDelta(messageId: String, partId: String, delta: String) {
        blocks[messageId]?.appendDelta(partId, delta)
        refresh()
    }

    private fun onError(msg: String) {
        val err = JBLabel(msg).apply {
            foreground = JBColor.RED
            font = JBUI.Fonts.label()
            border = JBUI.Borders.empty(4, 0)
            alignmentX = LEFT_ALIGNMENT
        }
        inner.add(err, inner.componentCount - 1)
        refresh()
    }

    private fun onStatus(text: String?) {
        if (text != null) {
            label.text = text
            spinner.isVisible = true
        } else {
            spinner.isVisible = false
        }
        refresh()
    }

    private fun onHistory() {
        clear()
        for (entry in model.entries()) {
            val block = MessageBlock(entry.info)
            blocks[entry.info.id] = block
            inner.add(block, inner.componentCount - 1)
            for ((partId, text) in entry.parts) {
                if (text.isNotEmpty()) {
                    block.setText(partId, text.toString())
                }
            }
        }
        refresh()
    }

    private fun onCleared() {
        clear()
        refresh()
    }

    private fun clear() {
        blocks.clear()
        inner.removeAll()
        inner.add(spinner)
        spinner.isVisible = false
    }

    private fun refresh() {
        revalidate()
        repaint()
    }
}

/**
 * A single message block — text content only, no role header.
 * User messages get a thin top border as separator.
 */
private class MessageBlock(info: MessageDto) : JPanel() {
    private val parts = LinkedHashMap<String, JTextArea>()

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT

        border = if (info.role == "user") {
            JBUI.Borders.compound(
                MatteBorder(1, 0, 0, 0, JBColor.border()),
                JBUI.Borders.empty(8, 0, 4, 0),
            )
        } else {
            JBUI.Borders.empty(4, 0)
        }
    }

    fun setText(partID: String, text: String) {
        val area = parts.getOrPut(partID) { createArea().also { add(it) } }
        area.text = text
        revalidate()
    }

    fun appendDelta(partID: String, delta: String) {
        val area = parts.getOrPut(partID) { createArea().also { add(it) } }
        area.append(delta)
        revalidate()
    }

    private fun createArea() = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        isOpaque = false
        font = JBUI.Fonts.label()
        foreground = UIUtil.getLabelForeground()
        border = JBUI.Borders.empty()
        alignmentX = LEFT_ALIGNMENT
    }
}
