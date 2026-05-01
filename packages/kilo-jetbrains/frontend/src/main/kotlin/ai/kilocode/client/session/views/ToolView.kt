@file:Suppress("TooManyFunctions")

package ai.kilocode.client.session.views

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.model.Content
import ai.kilocode.client.session.model.Tool
import ai.kilocode.client.session.model.ToolExecState
import ai.kilocode.client.session.ui.SessionStyle
import ai.kilocode.client.ui.UiStyle
import com.intellij.icons.AllIcons
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

/** Renders tool calls with VS Code-inspired rows/cards. */
class ToolView(tool: Tool) : PartView() {

    override val contentId: String = tool.id

    private var item = tool
    private var open = tool.name == "bash" && body(tool).isNotBlank()
    private var mode = tool.name
    private var touched = false
    private var hover = false
    private var box: JComponent? = null

    private val root = JPanel(BorderLayout()).apply {
        isOpaque = true
        background = UiStyle.Colors.surface()
        border = UiStyle.Card.border()
    }
    private val header = JPanel(UiStyle.Card.layout()).apply {
        isOpaque = true
        background = UiStyle.Colors.header()
        border = UiStyle.Card.headerInsets()
    }
    private val glyph = JBLabel()
    private val title = JBLabel()
    private val sub = JBLabel().apply {
        foreground = UiStyle.Colors.weak()
    }
    private val state = JBLabel().apply {
        foreground = UiStyle.Colors.weak()
    }
    private val arrow = JBLabel()
    private val copy = JButton(AllIcons.Actions.Copy).apply {
        UiStyle.Buttons.icon(this)
        toolTipText = KiloBundle.message("session.part.tool.copy")
        addActionListener { copyShell() }
    }
    private val text = JBTextArea().apply {
        isEditable = false
        lineWrap = false
        wrapStyleWord = false
        foreground = UiStyle.Colors.fg()
        background = UiStyle.Colors.surface()
        border = UiStyle.Card.bodyInsets()
    }
    private val scroll = JBScrollPane(text).apply {
        border = UiStyle.Card.divider()
        isOpaque = true
        background = UiStyle.Colors.surface()
        viewport.background = UiStyle.Colors.surface()
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
    }

    private val click = object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
            if (!canExpand(item)) return
            touched = true
            open = !open
            render()
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
        listOf(header, glyph, title, sub, state, arrow).forEach {
            bind(it)
            it.addMouseListener(click)
        }
        copy.addMouseListener(mouse)
        applyStyle(SessionStyle.current())
        add(root, BorderLayout.CENTER)
        render()
    }

    override fun update(content: Content) {
        if (content !is Tool) return
        val was = mode
        item = content
        mode = content.name
        if (was != mode) {
            touched = false
            open = mode == "bash" && body(content).isNotBlank()
        }
        render()
    }

    fun labelText(): String = listOf(title.text, sub.text, state.text).filter { it.isNotBlank() }.joinToString(" ")

    fun commandText(): String = command(item)

    fun outputText(): String = output(item)

    fun bodyText(): String = body(item)

    fun copyText(): String = if (mode == "bash") shellCopy(item) else plainBody(item)

    fun isExpanded(): Boolean = open

    fun hasToggle(): Boolean = arrow.isVisible

    internal fun bodyFont() = text.font

    internal fun titleFont() = title.font

    internal fun subtitleFont() = sub.font

    internal fun stateFont() = state.font

    internal fun bodyEditable() = text.isEditable

    internal fun bodyVisible() = scroll.parent === root

    internal fun controlCount() = box?.components?.count { it === copy || it === arrow } ?: 0

    override fun applyStyle(style: SessionStyle) {
        title.font = style.boldEditorFont
        sub.font = style.smallEditorFont
        state.font = style.smallEditorFont
        text.font = style.transcriptFont
        revalidate()
        repaint()
    }

    fun toggle() {
        if (!canExpand(item)) return
        touched = true
        open = !open
        render()
    }

    private fun render() {
        root.removeAll()
        header.removeAll()
        box = null
        val expand = canExpand(item)
        syncOpen(expand)
        syncState(expand)

        when (mode) {
            "read" -> renderRead(expand)
            "bash" -> renderShell(expand)
            else -> renderGeneric(expand)
        }

        root.add(header, BorderLayout.NORTH)
        if (open && expand) root.add(scroll, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    private fun syncOpen(expand: Boolean) {
        if (!expand) {
            touched = false
            open = false
            return
        }
        if (expand && !touched && mode == "bash") open = true
    }

    private fun syncState(expand: Boolean) {
        val cursor = if (expand) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
        listOf(header, glyph, title, sub, state, arrow).forEach { it.cursor = cursor }
        arrow.isVisible = expand
        arrow.icon = if (open) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
        glyph.icon = icon(item)
        glyph.foreground = color(item)
        title.foreground = if (item.state == ToolExecState.ERROR) UiStyle.Colors.error() else UiStyle.Colors.fg()
        state.text = stateText(item)
        state.foreground = color(item)
        text.text = body(item)
        text.foreground = if (item.state == ToolExecState.ERROR) UiStyle.Colors.error() else UiStyle.Colors.fg()
        syncHeader()
    }

    private fun renderRead(expand: Boolean) {
        title.text = KiloBundle.message("session.part.tool.read")
        sub.text = readPath(item)
        addHeader(center(), end(expand))
    }

    private fun renderShell(expand: Boolean) {
        title.text = KiloBundle.message("session.part.tool.shell")
        sub.text = shellTitle(item)
        addHeader(center(), end(expand))
    }

    private fun renderGeneric(expand: Boolean) {
        title.text = toolTitle(item)
        sub.text = toolSubtitle(item)
        addHeader(center(), end(expand))
    }

    private fun addHeader(center: Component, end: Component) {
        header.add(glyph, BorderLayout.WEST)
        header.add(center, BorderLayout.CENTER)
        header.add(end, BorderLayout.EAST)
    }

    private fun center() = JPanel(UiStyle.Card.layout()).apply {
        isOpaque = false
        addMouseListener(click)
        bind(this)
        add(title, BorderLayout.WEST)
        add(sub, BorderLayout.CENTER)
    }

    private fun end(expand: Boolean): Component = controls(expand) ?: state

    private fun controls(expand: Boolean): JComponent? {
        val copied = copyText().isNotBlank()
        if (!copied && !expand) return null
        val view = Box.createHorizontalBox()
        view.addMouseListener(click)
        bind(view)
        if (copied) view.add(copy)
        if (copied && expand) view.add(Box.createHorizontalStrut(UiStyle.Card.controlGap()))
        if (expand) view.add(arrow)
        box = view
        return view
    }

    private fun setHover(value: Boolean) {
        if (hover == value) return
        hover = value
        syncHeader()
        header.repaint()
    }

    private fun syncHeader() {
        header.background = if (hover) UiStyle.Colors.headerHover() else UiStyle.Colors.header()
    }

    private fun inside(e: MouseEvent): Boolean {
        val point = SwingUtilities.convertPoint(e.component, e.point, header)
        return header.contains(point)
    }

    private fun bind(component: Component) {
        component.addMouseListener(mouse)
    }

    private fun copyShell() {
        val value = copyText()
        if (value.isBlank()) return
        CopyPasteManager.getInstance().setContents(StringSelection(value))
    }

    override fun dumpLabel() = "ToolView#$contentId(${labelText()})"
}

private fun icon(tool: Tool) = when (tool.name) {
    "read" -> AllIcons.Actions.Preview
    "bash" -> AllIcons.Debugger.Console
    else -> when (tool.state) {
        ToolExecState.PENDING -> AllIcons.Process.Step_1
        ToolExecState.RUNNING -> AllIcons.Process.Step_2
        ToolExecState.COMPLETED -> AllIcons.Actions.Checked
        ToolExecState.ERROR -> AllIcons.General.Error
    }
}

private fun color(tool: Tool) = when (tool.state) {
    ToolExecState.PENDING -> UiStyle.Colors.weak()
    ToolExecState.RUNNING -> UiStyle.Colors.running()
    ToolExecState.COMPLETED -> UiStyle.Colors.weak()
    ToolExecState.ERROR -> UiStyle.Colors.error()
}

private fun stateText(tool: Tool) = when (tool.state) {
    ToolExecState.PENDING -> KiloBundle.message("session.part.tool.pending")
    ToolExecState.RUNNING -> KiloBundle.message("session.part.tool.running")
    ToolExecState.COMPLETED -> ""
    ToolExecState.ERROR -> KiloBundle.message("session.part.tool.error")
}

private fun readPath(tool: Tool): String {
    val path = tool.input["filePath"] ?: tool.input["path"] ?: tool.title ?: return tool.name
    return tail(path).ifBlank { path }
}

private fun shellTitle(tool: Tool): String =
    tool.input["description"]?.takeIf { it.isNotBlank() }
        ?: tool.metadata["description"]?.takeIf { it.isNotBlank() }
        ?: tool.title?.takeIf { it.isNotBlank() }
        ?: command(tool).lineSequence().firstOrNull { it.isNotBlank() }
        ?: ""

private fun command(tool: Tool): String =
    tool.input["command"]?.takeIf { it.isNotBlank() }
        ?: tool.metadata["command"]?.takeIf { it.isNotBlank() }
        ?: ""

private fun output(tool: Tool): String =
    tool.output?.takeIf { it.isNotBlank() }
        ?: tool.metadata["output"]?.takeIf { it.isNotBlank() }
        ?: ""

private fun body(tool: Tool): String = if (tool.name == "bash") shellBody(tool) else plainBody(tool)

private fun shellBody(tool: Tool): String {
    val cmd = command(tool)
    val out = output(tool)
    val err = tool.error?.takeIf { it.isNotBlank() }
    return buildString {
        if (cmd.isNotBlank()) append("$ ").append(cmd)
        if (out.isNotBlank()) {
            if (isNotEmpty()) append("\n\n")
            append(out)
        }
        if (err != null) {
            if (isNotEmpty()) append("\n\n")
            append(err)
        }
    }
}

private fun shellCopy(tool: Tool): String {
    val cmd = command(tool)
    val out = output(tool)
    val err = tool.error?.takeIf { it.isNotBlank() }
    return listOf(cmd, out, err).filter { !it.isNullOrBlank() }.joinToString("\n\n")
}

private fun plainBody(tool: Tool): String {
    val out = output(tool)
    val err = tool.error?.takeIf { it.isNotBlank() }
    return listOf(out, err).filter { !it.isNullOrBlank() }.joinToString("\n\n")
}

private fun canExpand(tool: Tool): Boolean = body(tool).isNotBlank()

private fun toolTitle(tool: Tool): String =
    tool.title?.takeIf { it.isNotBlank() }
        ?: tool.name.replace('_', ' ').replaceFirstChar { it.titlecase() }

private fun toolSubtitle(tool: Tool): String {
    val base = listOf("description", "query", "url", "filePath", "path", "name")
        .mapNotNull { tool.input[it]?.takeIf { value -> value.isNotBlank() } }
        .firstOrNull()
    val args = listOf("pattern", "include", "offset", "limit")
        .mapNotNull { key -> tool.input[key]?.takeIf { it.isNotBlank() }?.let { "$key=$it" } }
    return listOfNotNull(base).plus(args).joinToString(" ")
}

private fun tail(path: String): String {
    val value = path.trimEnd('/', '\\')
    val index = maxOf(value.lastIndexOf('/'), value.lastIndexOf('\\'))
    if (index < 0) return value
    return value.substring(index + 1)
}
