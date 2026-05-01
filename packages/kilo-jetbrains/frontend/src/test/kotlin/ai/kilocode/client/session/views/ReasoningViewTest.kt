package ai.kilocode.client.session.views

import ai.kilocode.client.session.model.Reasoning
import ai.kilocode.client.session.ui.SessionStyle
import com.intellij.testFramework.fixtures.BasePlatformTestCase

@Suppress("UnstableApiUsage")
class ReasoningViewTest : BasePlatformTestCase() {

    fun `test completed reasoning is expanded by default`() {
        val view = ReasoningView(reasoning("p1", done = true, text = "one\ntwo\nthree\nfour"))

        assertTrue(view.isExpanded())
        assertEquals("Reasoning", view.headerText())
        assertEquals("one\ntwo\nthree\nfour", view.markdown())
        assertTrue(view.hasToggle())
        assertTrue(view.bodyVisible())
    }

    fun `test short completed reasoning is collapsible`() {
        val view = ReasoningView(reasoning("p1", done = true, text = "one\ntwo\nthree"))

        assertTrue(view.isExpanded())
        assertTrue(view.hasToggle())
        view.toggle()
        assertFalse(view.isExpanded())
        assertFalse(view.bodyVisible())
    }

    fun `test streaming reasoning is expanded by default`() {
        val view = ReasoningView(reasoning("p1", done = false, text = "one\ntwo\nthree\nfour"))

        assertTrue(view.isExpanded())
        assertTrue(view.hasToggle())
    }

    fun `test update preserves automatic open reasoning`() {
        val view = ReasoningView(reasoning("p1", done = false, text = "one\ntwo\nthree\nfour"))

        view.update(reasoning("p1", done = true, text = "one\ntwo\nthree\nfour"))

        assertTrue(view.isExpanded())
        assertEquals("one\ntwo\nthree\nfour", view.markdown())
    }

    fun `test toggle opens and closes reasoning`() {
        val view = ReasoningView(reasoning("p1", done = true, text = "one\ntwo\nthree\nfour"))

        assertTrue(view.isExpanded())
        view.toggle()
        assertFalse(view.isExpanded())
        view.toggle()
        assertTrue(view.isExpanded())
    }

    fun `test collapsed reasoning stays collapsed on update`() {
        val view = ReasoningView(reasoning("p1", done = false, text = "one\ntwo"))

        view.toggle()
        view.update(reasoning("p1", done = true, text = "one\ntwo\nthree"))

        assertFalse(view.isExpanded())
        assertEquals("one\ntwo\nthree", view.markdown())
    }

    fun `test appendDelta preserves markdown`() {
        val view = ReasoningView(reasoning("p1", done = false, text = "a"))

        view.appendDelta("b")

        assertEquals("ab", view.markdown())
    }

    fun `test blank reasoning has no toggle`() {
        val view = ReasoningView(reasoning("p1", done = true, text = ""))

        assertFalse(view.isExpanded())
        assertFalse(view.hasToggle())
    }

    fun `test reasoning markdown uses editor font settings`() {
        val style = SessionStyle.current()
        val view = ReasoningView(reasoning("p1", done = true, text = "one\ntwo\nthree\nfour"))

        assertEditorSheet(view.md.overrideSheet(), style)
    }

    fun `test reasoning header uses smaller editor-derived font`() {
        val style = SessionStyle.current()
        val view = ReasoningView(reasoning("p1", done = true, text = "one"))
        val font = view.headerFont()

        assertEquals(style.editorFamily, font.name)
        assertTrue(font.size < style.editorSize)
    }

    fun `test applyStyle updates reasoning in place`() {
        val view = ReasoningView(reasoning("p1", done = true, text = "one\ntwo\nthree\nfour"))
        val component = view.md.component
        val style = SessionStyle.create(family = "Courier New", size = 24)

        view.applyStyle(style)

        assertSame(component, view.md.component)
        assertEditorSheet(view.md.overrideSheet(), style)
        assertEquals("Courier New", view.headerFont().name)
        assertTrue(view.headerFont().size < style.editorSize)
    }

    private fun assertEditorSheet(sheet: String, style: SessionStyle) {
        assertTrue(sheet.contains(style.editorFamily))
        assertTrue(sheet.contains("${style.editorSize}pt"))
    }

    private fun reasoning(id: String, done: Boolean, text: String) = Reasoning(id).also {
        it.done = done
        it.content.append(text)
    }
}
