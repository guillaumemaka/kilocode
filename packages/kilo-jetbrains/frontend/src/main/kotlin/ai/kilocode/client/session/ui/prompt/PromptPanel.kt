package ai.kilocode.client.session.ui.prompt

import ai.kilocode.client.actions.SendPromptAction
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.ui.ReasoningPicker
import ai.kilocode.client.session.ui.SessionStyle
import ai.kilocode.client.session.ui.SessionStyleTarget
import ai.kilocode.client.session.ui.mode.ModePicker
import ai.kilocode.client.session.ui.model.ModelPicker
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.log.ChatLogSummary
import ai.kilocode.log.KiloLog
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants

/**
 * Prompt input panel with borderless IntelliJ editor text field and
 * mode/model controls grouped inside one rounded editor-background shell.
 */
class PromptPanel(
    private val project: Project,
    private val onSend: (String) -> Unit,
    private val onAbort: () -> Unit,
) : BorderLayoutPanel(), SessionStyleTarget, SendPromptContext {

    companion object {
        private val LOG = KiloLog.create(PromptPanel::class.java)
        private val SEND_ICON: Icon = IconLoader.getIcon("/icons/send.svg", PromptPanel::class.java)
        private val STOP_ICON: Icon = IconLoader.getIcon("/icons/stop.svg", PromptPanel::class.java)
        private const val ARC = 6
        private const val FOCUS = 2
    }

    val mode = ModePicker()
    val model = ModelPicker()
    val reasoning = ReasoningPicker()
    var onReset: () -> Unit = {}
    private var style = SessionStyle.current()
    private val shell = PromptShell()

    private val editor = PromptEditorTextField(project, this).apply {
        border = JBUI.Borders.empty()
        setFontInheritedFromLAF(false)
        setPlaceholder(KiloBundle.message("prompt.placeholder"))
        setShowPlaceholderWhenFocused(true)
        setOneLineMode(false)
        addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                syncButton()
            }
        })
        addSettingsProvider { ed ->
            style.applyToEditor(ed)
            ed.setBorder(JBUI.Borders.empty())
            ed.scrollPane.border = JBUI.Borders.empty()
            ed.scrollPane.viewportBorder = JBUI.Borders.empty()
            ed.backgroundColor = style.editorScheme.defaultBackground
            ed.scrollPane.background = style.editorScheme.defaultBackground
            ed.scrollPane.viewport.background = style.editorScheme.defaultBackground
            ed.settings.isUseSoftWraps = true
            ed.settings.isAdditionalPageAtBottom = false
            ed.scrollPane.horizontalScrollBarPolicy =
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            ed.contentComponent.addFocusListener(object : FocusAdapter() {
                override fun focusGained(e: FocusEvent) {
                    shell.repaint()
                }

                override fun focusLost(e: FocusEvent) {
                    shell.repaint()
                }
            })
        }
    }

    private val button: SendButton = SendButton().apply {
        icon = SEND_ICON
        isFocusPainted = false
        toolTipText = KiloBundle.message("prompt.button.send")
        addActionListener {
            if (!button.active) return@addActionListener
            if (busy) {
                onAbort()
                return@addActionListener
            }
            val action = ActionManager.getInstance().getAction(SendPromptAction.ID)
                ?: return@addActionListener
            ActionManager.getInstance().tryToExecute(
                action,
                null,
                editor,
                ActionPlaces.UNKNOWN,
                true,
            )
        }
    }

    private val reset = UiStyle.Buttons.HoverIcon().apply {
        icon = AllIcons.Actions.Cancel
        toolTipText = KiloBundle.message("model.picker.reset")
        accessibleContext.accessibleName = KiloBundle.message("model.picker.reset")
        isVisible = false
        addActionListener { onReset() }
    }

    @Volatile
    private var busy = false
    private var ready = false

    override val isSendEnabled: Boolean
        get() = ready && !busy && text().isNotEmpty()

    init {
        border = JBUI.Borders.compound(
            JBUI.Borders.customLineTop(JBUI.CurrentTheme.ToolWindow.borderColor()),
            UiStyle.Insets.prompt(),
        )

        applyStyle(style)
        shell.add(editor, BorderLayout.CENTER)

        val bar = BorderLayoutPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            border = JBUI.Borders.emptyTop(UiStyle.Space.SM)
        }
        bar.add(mode)
        bar.add(Box.createHorizontalStrut(UiStyle.Gap.small()))
        bar.add(model)
        bar.add(Box.createHorizontalStrut(UiStyle.Gap.small()))
        bar.add(reasoning)
        bar.add(Box.createHorizontalStrut(UiStyle.Gap.small()))
        bar.add(reset)
        bar.add(Box.createHorizontalGlue())
        bar.add(button)
        shell.add(bar, BorderLayout.SOUTH)
        add(shell, BorderLayout.CENTER)
    }

    fun setReady(value: Boolean) {
        ready = value
        syncButton()
    }

    fun setBusy(value: Boolean) {
        busy = value
        button.icon = if (value) STOP_ICON else SEND_ICON
        button.toolTipText = if (value) {
            KiloBundle.message("prompt.button.stop")
        } else {
            KiloBundle.message("prompt.button.send")
        }
        syncButton()
    }

    fun setResetVisible(value: Boolean) {
        reset.isVisible = value
        revalidate()
        repaint()
    }

    fun text(): String = editor.text.trim()

    override fun send() {
        submit("action")
    }

    internal fun inputFont() = editor.font

    internal fun resetVisibleForTest() = reset.isVisible

    internal fun resetForTest(): JComponent = reset

    internal fun shellForTest(): JComponent = shell

    internal val defaultFocusedComponent: JComponent get() = editor

    override fun applyStyle(style: SessionStyle) {
        this.style = style
        editor.font = style.transcriptFont
        editor.getEditor(false)?.let(style::applyToEditor)
        editor.background = style.editorScheme.defaultBackground
        val height = style.transcriptFont.size * UiStyle.Size.LINES + JBUI.scale(
            UiStyle.Size.CHROME)
        editor.preferredSize = JBDimension(0, height)
        editor.minimumSize = JBDimension(0, height)
        revalidate()
        repaint()
    }

    fun clear() {
        editor.text = ""
        syncButton()
    }

    fun focus() {
        editor.requestFocusInWindow()
    }

    private fun submit(src: String) {
        if (!isSendEnabled) return
        val txt = text()
        LOG.debug { "${ChatLogSummary.prompt(txt)} src=$src busy=$busy" }
        if (txt.isNotEmpty()) {
            onSend(txt)
        }
    }

    private fun syncButton() {
        button.active = busy || isSendEnabled
    }

    private inner class SendButton : JButton() {
        private var over = false
        var active = false
            set(value) {
                if (field == value) return
                field = value
                repaint()
            }

        init {
            UiStyle.Buttons.icon(this)
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    sync(true)
                }

                override fun mouseExited(e: MouseEvent) {
                    sync(false)
                }
            })
        }

        override fun getPreferredSize() = JBUI.size(UiStyle.Size.BUTTON, UiStyle.Size.BUTTON)

        override fun getMinimumSize() = preferredSize

        override fun getMaximumSize() = preferredSize

        override fun paintComponent(g: Graphics) {
            if (over) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = JBUI.CurrentTheme.ActionButton.hoverBackground()
                    val arc = JBUI.scale(JBUI.getInt("Button.arc", ARC))
                    g2.fillRoundRect(0, 0, width, height, arc, arc)
                } finally {
                    g2.dispose()
                }
            }
            super.paintComponent(g)
        }

        private fun sync(value: Boolean) {
            if (over == value) return
            over = value
            repaint()
        }
    }

    private inner class PromptShell : BorderLayoutPanel() {
        private val arc = JBValue.UIInteger("Button.arc", ARC)
        private val focus = JBValue.UIInteger("Component.focusWidth", FOCUS)

        init {
            isOpaque = false
            border = JBUI.Borders.empty(UiStyle.Space.MD, UiStyle.Space.LG)
        }

        override fun updateUI() {
            super.updateUI()
            border = JBUI.Borders.empty(UiStyle.Space.MD, UiStyle.Space.LG)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON,
                )
                g2.color = style.editorScheme.defaultBackground
                val size = arc.get()
                g2.fillRoundRect(0, 0, width, height, size, size)
                val active = UIUtil.isFocusAncestor(editor)
                g2.color = if (active) {
                    JBUI.CurrentTheme.Focus.focusColor()
                } else {
                    UiStyle.Colors.line()
                }
                val bw = if (active) focus.get() else JBUI.scale(1)
                for (idx in 0 until bw) {
                    val inset = idx
                    val w = width - inset * 2 - 1
                    val h = height - inset * 2 - 1
                    if (w > 0 && h > 0) {
                        g2.drawRoundRect(inset, inset, w, h, size, size)
                    }
                }
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }
    }
}
