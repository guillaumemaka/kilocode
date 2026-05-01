@file:Suppress("TooManyFunctions")

package ai.kilocode.client.session.views

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.model.Content
import ai.kilocode.client.session.model.Reasoning
import ai.kilocode.client.session.ui.SessionStyle
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.md.MdView
import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/** Renders reasoning as a VS Code-style collapsible block. */
class ReasoningView(reasoning: Reasoning) : PartView() {

    override val contentId: String = reasoning.id

    val md: MdView = MdView.html()

    private val arrow = JBLabel()
    private val body = JPanel(BorderLayout()).apply {
        isOpaque = true
        background = UiStyle.Colors.surface()
        border = UiStyle.Card.bodyInsets()
    }
    private val header = JPanel(UiStyle.Card.layout()).apply {
        isOpaque = true
        background = UiStyle.Colors.header()
        border = UiStyle.Card.headerInsets()
    }
    private val title = JBLabel(KiloBundle.message("session.part.reasoning")).apply {
        foreground = UiStyle.Colors.weak()
    }
    private val icon = JBLabel(AllIcons.General.InspectionsEye).apply {
        foreground = UiStyle.Colors.weak()
    }

    private var open = reasoning.content.isNotBlank()
    private var touched = false
    private var hover = false
    private var source = reasoning.content.toString()

    private val click = object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
            if (!canExpand()) return
            touched = true
            setOpen(!open)
        }
    }

    private val mouse = object : MouseAdapter() {
        override fun mouseEntered(e: MouseEvent) {
            setHover(true)
        }

        override fun mouseExited(e: MouseEvent) {
            if (inside(e)) return
            setHover(false)
        }
    }

    init {
        layout = BorderLayout()
        isOpaque = false
        border = UiStyle.Card.border()

        val left = JPanel(UiStyle.Card.layout()).apply {
            isOpaque = false
            add(icon, BorderLayout.WEST)
            add(title, BorderLayout.CENTER)
        }

        header.add(left, BorderLayout.CENTER)
        header.add(arrow, BorderLayout.EAST)
        listOf(header, left, title, icon, arrow).forEach {
            it.addMouseListener(click)
            it.addMouseListener(mouse)
        }

        applyStyle(SessionStyle.current())
        md.opaque = false
        body.add(md.component, BorderLayout.CENTER)

        add(header, BorderLayout.NORTH)
        setText(source)
        render()
    }

    override fun update(content: Content) {
        if (content !is Reasoning) return
        source = content.content.toString()
        setText(source)
        if (!touched) open = source.isNotBlank()
        render()
    }

    override fun appendDelta(delta: String) {
        source += delta
        md.append(delta)
        if (!touched) open = source.isNotBlank()
        render()
    }

    fun markdown(): String = source

    fun isExpanded(): Boolean = open

    fun hasToggle(): Boolean = arrow.isVisible

    fun headerText(): String = title.text

    internal fun headerFont() = title.font

    internal fun bodyVisible() = body.parent === this

    override fun applyStyle(style: SessionStyle) {
        title.font = style.smallEditorFont
        md.font = style.transcriptFont
        md.codeFont = style.editorFamily
        md.foreground = UiStyle.Colors.weak()
        revalidate()
        repaint()
    }

    fun toggle() {
        if (!canExpand()) return
        touched = true
        setOpen(!open)
    }

    private fun setOpen(value: Boolean) {
        open = value
        render()
    }

    private fun render() {
        val expand = canExpand()
        if (!expand) {
            touched = false
            open = false
        }
        arrow.isVisible = expand
        arrow.icon = if (open) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
        val cursor = if (expand) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
        listOf(header, title, icon, arrow).forEach { it.cursor = cursor }
        remove(body)
        if (open && expand) add(body, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    private fun setHover(value: Boolean) {
        if (hover == value) return
        hover = value
        header.background = if (hover) UiStyle.Colors.headerHover() else UiStyle.Colors.header()
        header.repaint()
    }

    private fun inside(e: MouseEvent): Boolean {
        val point = SwingUtilities.convertPoint(e.component, e.point, header)
        return header.contains(point)
    }

    private fun setText(text: String) {
        md.set(text)
    }

    private fun canExpand(): Boolean = source.isNotBlank()

    override fun dumpLabel() = "ReasoningView#$contentId(${if (open) "open" else "closed"})"
}
