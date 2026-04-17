package ai.kilocode.client.ui.md

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Color
import java.awt.Font

/**
 * Tests for [MdView] created via [MdView.html] factory.
 *
 * Uses [BasePlatformTestCase] to get a real IntelliJ Application so that
 * Swing/HTMLEditorKit initialisation works correctly.
 */
class MdViewTest : BasePlatformTestCase() {

    private lateinit var view: MdView

    override fun setUp() {
        super.setUp()
        view = MdView.html()
    }

    // ---- set ----

    fun `test set stores source`() {
        view.set("hello **world**")
        assertEquals("hello **world**", view.markdown())
    }

    fun `test set replaces previous content`() {
        view.set("first")
        view.set("second")
        assertEquals("second", view.markdown())
    }

    fun `test set renders bold`() {
        view.set("hello **world**")
        val html = view.html()
        assertTrue("Expected <strong> in rendered HTML", html.contains("<strong>"))
        assertTrue("Expected 'world' inside strong", html.contains("world"))
    }

    fun `test set renders italic`() {
        view.set("hello *world*")
        val html = view.html()
        assertTrue("Expected <em> in rendered HTML", html.contains("<em>"))
    }

    fun `test set renders inline code`() {
        view.set("use `foo()` here")
        val html = view.html()
        assertTrue("Expected <code> in rendered HTML", html.contains("<code>"))
        assertTrue("Expected foo() in code", html.contains("foo()"))
    }

    fun `test set renders fenced code block`() {
        view.set("```kotlin\nval x = 1\n```")
        val html = view.html()
        assertTrue("Expected <pre> in rendered HTML", html.contains("<pre>"))
        assertTrue("Expected <code in rendered HTML", html.contains("<code"))
    }

    fun `test set renders links`() {
        view.set("[click](https://example.com)")
        val html = view.html()
        assertTrue("Expected <a in rendered HTML", html.contains("<a"))
        assertTrue("Expected href", html.contains("https://example.com"))
    }

    fun `test set renders headings`() {
        view.set("# Title")
        val html = view.html()
        assertTrue("Expected <h1> in rendered HTML", html.contains("<h1>"))
    }

    fun `test set renders unordered list`() {
        view.set("- one\n- two\n- three")
        val html = view.html()
        assertTrue("Expected <ul> in rendered HTML", html.contains("<ul>"))
        assertTrue("Expected <li> in rendered HTML", html.contains("<li>"))
    }

    fun `test set renders ordered list`() {
        view.set("1. one\n2. two\n3. three")
        val html = view.html()
        assertTrue("Expected <ol> in rendered HTML", html.contains("<ol>"))
    }

    fun `test set renders blockquote`() {
        view.set("> quoted text")
        val html = view.html()
        assertTrue("Expected <blockquote> in rendered HTML", html.contains("<blockquote>"))
    }

    fun `test set renders strikethrough`() {
        view.set("~~deleted~~")
        val html = view.html()
        assertTrue("Expected <del> in rendered HTML", html.contains("<del>"))
    }

    fun `test set renders table`() {
        view.set("| a | b |\n|---|---|\n| 1 | 2 |")
        val html = view.html()
        assertTrue("Expected <table> in rendered HTML", html.contains("<table>"))
        assertTrue("Expected <th> in rendered HTML", html.contains("<th>"))
        assertTrue("Expected <td> in rendered HTML", html.contains("<td>"))
    }

    fun `test set renders autolink`() {
        view.set("Visit https://example.com for details")
        val html = view.html()
        assertTrue("Expected autolinked URL", html.contains("<a"))
        assertTrue("Expected href", html.contains("https://example.com"))
    }

    // ---- append ----

    fun `test append accumulates source`() {
        view.append("hello ")
        view.append("**world**")
        assertEquals("hello **world**", view.markdown())
    }

    fun `test append renders accumulated content`() {
        view.append("hello ")
        view.append("**world**")
        val html = view.html()
        assertTrue("Expected <strong> after append", html.contains("<strong>"))
    }

    fun `test append after set extends content`() {
        view.set("first ")
        view.append("second")
        assertEquals("first second", view.markdown())
    }

    // ---- clear ----

    fun `test clear resets source`() {
        view.set("some content")
        view.clear()
        assertEquals("", view.markdown())
    }

    fun `test clear resets rendered html`() {
        view.set("some content")
        view.clear()
        val html = view.html()
        assertFalse("Expected no 'some content' after clear", html.contains("some content"))
    }

    // ---- link listener ----

    fun `test link listener receives event on activation`() {
        val received = mutableListOf<MdView.LinkEvent>()
        view.addLinkListener { received.add(it) }
        view.set("[click](https://example.com)")
        view.simulateLink("https://example.com")
        assertEquals(1, received.size)
        assertEquals("https://example.com", received[0].href)
    }

    fun `test multiple link listeners all receive event`() {
        val first = mutableListOf<MdView.LinkEvent>()
        val second = mutableListOf<MdView.LinkEvent>()
        view.addLinkListener { first.add(it) }
        view.addLinkListener { second.add(it) }
        view.simulateLink("https://a.com")
        assertEquals(1, first.size)
        assertEquals(1, second.size)
    }

    fun `test removed listener does not receive events`() {
        val received = mutableListOf<MdView.LinkEvent>()
        val listener = MdView.LinkListener { received.add(it) }
        view.addLinkListener(listener)
        view.removeLinkListener(listener)
        view.simulateLink("https://example.com")
        assertTrue("Removed listener should not fire", received.isEmpty())
    }

    // ---- component ----

    fun `test component is not null`() {
        assertNotNull(view.component)
    }

    // ---- complex markdown ----

    fun `test complex markdown with mixed elements`() {
        val md = """
            # Heading
            
            Some **bold** and *italic* text with `code`.
            
            - item one
            - item two
            
            ```
            code block
            ```
            
            > blockquote
            
            [link](https://example.com)
        """.trimIndent()

        view.set(md)
        val html = view.html()
        assertTrue(html.contains("<h1>"))
        assertTrue(html.contains("<strong>"))
        assertTrue(html.contains("<em>"))
        assertTrue(html.contains("<code>"))
        assertTrue(html.contains("<ul>"))
        assertTrue(html.contains("<pre>"))
        assertTrue(html.contains("<blockquote>"))
        assertTrue(html.contains("<a"))
    }

    fun `test streaming simulation appends tokens`() {
        val tokens = listOf("Hello ", "**wor", "ld**", "\n\n", "Done.")
        for (token in tokens) {
            view.append(token)
        }
        assertEquals("Hello **world**\n\nDone.", view.markdown())
        val html = view.html()
        assertTrue(html.contains("<strong>"))
        assertTrue(html.contains("Done."))
    }

    // ---- styling ----

    fun `test foreground color appears in CSS`() {
        view.foreground = Color(0xAA, 0xBB, 0xCC)
        view.set("text")
        assertTrue(view.styledHtml().contains("#aabbcc"))
    }

    fun `test background color appears in CSS`() {
        view.background = Color(0x11, 0x22, 0x33)
        view.set("text")
        assertTrue(view.styledHtml().contains("#112233"))
    }

    fun `test link color appears in CSS`() {
        view.linkColor = Color(0xFF, 0x00, 0x77)
        view.set("[a](https://x.com)")
        assertTrue(view.styledHtml().contains("#ff0077"))
    }

    fun `test code background appears in CSS`() {
        view.codeBg = Color(0x10, 0x20, 0x30)
        view.set("`code`")
        assertTrue(view.styledHtml().contains("#102030"))
    }

    fun `test pre background and foreground appear in CSS`() {
        view.preBg = Color(0x0A, 0x0B, 0x0C)
        view.preFg = Color(0xD0, 0xE0, 0xF0)
        view.set("```\ncode\n```")
        val css = view.styledHtml()
        assertTrue(css.contains("#0a0b0c"))
        assertTrue(css.contains("#d0e0f0"))
    }

    fun `test code font family appears in CSS`() {
        view.codeFont = "Fira Code"
        view.set("`x`")
        assertTrue(view.styledHtml().contains("Fira Code"))
    }

    fun `test blockquote colors appear in CSS`() {
        view.quoteBorder = Color(0xAA, 0x00, 0x00)
        view.quoteFg = Color(0x00, 0xBB, 0x00)
        view.set("> quote")
        val css = view.styledHtml()
        assertTrue(css.contains("#aa0000"))
        assertTrue(css.contains("#00bb00"))
    }

    fun `test table border color appears in CSS`() {
        view.tableBorder = Color(0x12, 0x34, 0x56)
        view.set("| a |\n|---|\n| 1 |")
        assertTrue(view.styledHtml().contains("#123456"))
    }

    fun `test font family appears in CSS`() {
        view.font = Font("Courier New", Font.PLAIN, 14)
        view.set("text")
        assertTrue(view.styledHtml().contains("Courier New"))
    }

    fun `test font size appears in CSS`() {
        view.font = Font("Arial", Font.PLAIN, 18)
        view.set("text")
        assertTrue(view.styledHtml().contains("18pt"))
    }

    fun `test style change re-renders existing content`() {
        view.set("hello")
        view.foreground = Color(0xDE, 0xAD, 0x00)
        assertTrue(view.styledHtml().contains("#dead00"))
        assertTrue(view.styledHtml().contains("hello"))
    }

    fun `test style change without content does not crash`() {
        view.foreground = Color.RED
        view.linkColor = Color.BLUE
        view.codeFont = "Monospaced"
        // no content set — should not throw
        assertEquals("", view.markdown())
    }

    // ---- opaque / transparent ----

    fun `test opaque true includes background in CSS`() {
        view.opaque = true
        view.background = Color(0x11, 0x22, 0x33)
        view.set("text")
        assertTrue(view.styledHtml().contains("background: #112233"))
    }

    fun `test opaque false omits background from body CSS`() {
        view.opaque = false
        view.background = Color(0x11, 0x22, 0x33)
        view.set("text")
        assertFalse(view.styledHtml().contains("background: #112233"))
    }

    fun `test opaque false does not affect pre background`() {
        view.opaque = false
        view.preBg = Color(0x0A, 0x0B, 0x0C)
        view.set("```\ncode\n```")
        assertTrue(view.styledHtml().contains("#0a0b0c"))
    }

    fun `test opaque toggle re-renders`() {
        view.background = Color(0xFE, 0xFE, 0xFE)
        view.set("hello")
        view.opaque = false
        assertFalse(view.styledHtml().contains("background: #fefefe"))
        view.opaque = true
        assertTrue(view.styledHtml().contains("background: #fefefe"))
    }

    fun `test component is not opaque when opaque is false`() {
        view.opaque = false
        assertFalse(view.component.isOpaque)
    }

    fun `test component is opaque when opaque is true`() {
        view.opaque = true
        assertTrue(view.component.isOpaque)
    }
}
