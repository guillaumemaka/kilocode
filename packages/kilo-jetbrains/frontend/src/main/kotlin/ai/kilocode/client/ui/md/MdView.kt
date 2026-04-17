package ai.kilocode.client.ui.md

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.awt.Color
import java.awt.Font
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLEditorKit

/**
 * Markdown rendering component that hides the concrete rendering strategy
 * (HTML, StyledDocument, etc.) behind a uniform API.
 *
 * Callers interact only with markdown source text and semantic events —
 * never with underlying widget types.
 *
 * Create instances via [MdView.html].
 * All public methods must be called on the EDT.
 */
abstract class MdView private constructor() {

    /** The Swing component to embed in layouts. */
    abstract val component: JComponent

    /** Replace the entire content with new markdown source. */
    abstract fun set(text: String)

    /** Append a streaming delta to the current content. */
    abstract fun append(delta: String)

    /** Reset to empty. */
    abstract fun clear()

    abstract fun addLinkListener(listener: LinkListener)

    abstract fun removeLinkListener(listener: LinkListener)

    // -- styling ----------------------------------------------------------

    /** Body text font family and size. */
    abstract var font: Font

    /** Body text foreground color. */
    abstract var foreground: Color

    /** Overall background color. */
    abstract var background: Color

    /** Link foreground color. */
    abstract var linkColor: Color

    /** Inline code background color. */
    abstract var codeBg: Color

    /** Code block (pre) background color. */
    abstract var preBg: Color

    /** Code block (pre) foreground color. */
    abstract var preFg: Color

    /** Code / pre font family. */
    abstract var codeFont: String

    /** Blockquote left-border and text color. */
    abstract var quoteBorder: Color
    abstract var quoteFg: Color

    /** Table / th / td border color. */
    abstract var tableBorder: Color

    /**
     * Whether the component paints its own background.
     * When `false`, the body background CSS is omitted and the underlying
     * Swing component is set to non-opaque so the parent's background
     * shows through.
     */
    abstract var opaque: Boolean

    /**
     * Semantic event fired when a user clicks a link inside rendered markdown.
     */
    data class LinkEvent(
        val href: String,
        val point: Point? = null,
    )

    /**
     * Callback for link activations inside a [MdView].
     */
    fun interface LinkListener {
        fun onLink(event: LinkEvent)
    }

    // -- test helpers (package-visible) ------------------------------------

    /** Returns the current raw markdown source. */
    internal abstract fun markdown(): String

    /** Returns the rendered HTML body (not Swing-rewritten). */
    internal abstract fun html(): String

    /** Returns the full wrapped HTML including CSS. */
    internal abstract fun styledHtml(): String

    /** Fire a synthetic link activation for testing. */
    internal abstract fun simulateLink(href: String)

    companion object {
        /** Create a [MdView] backed by commonmark-java → HTML → JEditorPane. */
        fun html(): MdView = HtmlImpl()
    }

    // -- private HTML implementation ---------------------------------------

    private class HtmlImpl : MdView() {

        private val listeners = mutableListOf<LinkListener>()
        private val source = StringBuilder()
        private var rendered = ""
        private var wrapped = ""
        private var dirty = false

        private val extensions = listOf(
            AutolinkExtension.create(),
            TablesExtension.create(),
            StrikethroughExtension.create(),
        )

        private val parser: Parser = Parser.builder()
            .extensions(extensions)
            .build()

        private val renderer: HtmlRenderer = HtmlRenderer.builder()
            .extensions(extensions)
            .build()

        private val pane: JEditorPane = JEditorPane().apply {
            isEditable = false
            contentType = "text/html"
            editorKit = HTMLEditorKit()
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
            border = JBUI.Borders.empty()

            addHyperlinkListener { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    val href = e.description ?: return@addHyperlinkListener
                    val pt = e.inputEvent?.let { Point(it.component.x, it.component.y) }
                    val event = LinkEvent(href, pt)
                    for (l in listeners) {
                        l.onLink(event)
                    }
                }
            }
        }

        // -- style fields with defaults -----------------------------------

        override var font: Font = JBUI.Fonts.label()
            set(value) { field = value; markDirty() }

        override var foreground: Color = UIUtil.getLabelForeground()
            set(value) { field = value; pane.foreground = value; markDirty() }

        override var background: Color = UIUtil.getPanelBackground()
            set(value) { field = value; pane.background = value; markDirty() }

        override var linkColor: Color = Color(0x58, 0x9D, 0xF6)
            set(value) { field = value; markDirty() }

        override var codeBg: Color = Color(0x3C, 0x3F, 0x41)
            set(value) { field = value; markDirty() }

        override var preBg: Color = Color(0x2B, 0x2B, 0x2B)
            set(value) { field = value; markDirty() }

        override var preFg: Color = Color(0xA9, 0xB7, 0xC6)
            set(value) { field = value; markDirty() }

        override var codeFont: String = "JetBrains Mono"
            set(value) { field = value; markDirty() }

        override var quoteBorder: Color = Color(0x55, 0x55, 0x55)
            set(value) { field = value; markDirty() }

        override var quoteFg: Color = Color(0x99, 0x99, 0x99)
            set(value) { field = value; markDirty() }

        override var tableBorder: Color = Color(0x55, 0x55, 0x55)
            set(value) { field = value; markDirty() }

        override var opaque: Boolean = true
            set(value) {
                field = value
                pane.isOpaque = value
                markDirty()
            }

        init {
            pane.font = font
            pane.foreground = foreground
            pane.background = background
        }

        override val component: JComponent get() = pane

        override fun set(text: String) {
            source.clear()
            source.append(text)
            render()
        }

        override fun append(delta: String) {
            source.append(delta)
            render()
        }

        override fun clear() {
            source.clear()
            rendered = ""
            wrapped = ""
            pane.text = ""
        }

        override fun addLinkListener(listener: LinkListener) {
            listeners.add(listener)
        }

        override fun removeLinkListener(listener: LinkListener) {
            listeners.remove(listener)
        }

        override fun markdown(): String = source.toString()

        override fun html(): String = rendered

        override fun styledHtml(): String = wrapped

        override fun simulateLink(href: String) {
            val event = LinkEvent(href)
            for (l in listeners) {
                l.onLink(event)
            }
        }

        private fun markDirty() {
            dirty = true
            if (source.isNotEmpty()) render()
        }

        private fun render() {
            dirty = false
            val md = source.toString()
            val body = renderer.render(parser.parse(md))
            rendered = body
            wrapped = wrap(body)
            pane.text = wrapped
            pane.caretPosition = 0
        }

        private fun wrap(body: String): String {
            val fg = hex(foreground)
            val size = font.size
            val family = font.family
            val codeSize = (size - 1).coerceAtLeast(1)
            val bgCss = if (opaque) "background: ${hex(background)}; " else ""
            return """
                <html>
                <head><style>
                body { font-family: '$family', sans-serif; font-size: ${size}pt;
                       color: $fg; ${bgCss}margin: 0; padding: 0; }
                a { color: ${hex(linkColor)}; }
                pre { background: ${hex(preBg)}; color: ${hex(preFg)}; padding: 8px;
                      border-radius: 4px; overflow-x: auto; }
                code { font-family: '$codeFont', monospace; font-size: ${codeSize}pt; }
                p code { background: ${hex(codeBg)}; padding: 1px 4px; border-radius: 3px; }
                table { border-collapse: collapse; }
                th, td { border: 1px solid ${hex(tableBorder)}; padding: 4px 8px; }
                blockquote { border-left: 3px solid ${hex(quoteBorder)}; margin-left: 0;
                             padding-left: 8px; color: ${hex(quoteFg)}; }
                </style></head>
                <body>$body</body>
                </html>
            """.trimIndent()
        }

        companion object {
            private fun hex(c: Color): String =
                String.format("#%02x%02x%02x", c.red, c.green, c.blue)
        }
    }
}
