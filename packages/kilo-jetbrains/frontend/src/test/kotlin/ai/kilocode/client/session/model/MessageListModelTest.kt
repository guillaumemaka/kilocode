package ai.kilocode.client.session.model

import ai.kilocode.rpc.dto.MessageDto
import ai.kilocode.rpc.dto.MessageTimeDto
import ai.kilocode.rpc.dto.PartDto
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.UsefulTestCase

class MessageListModelTest : UsefulTestCase() {

    private lateinit var model: MessageListModel
    private lateinit var parent: Disposable
    private lateinit var events: MutableList<MessageModelEvent>

    override fun setUp() {
        super.setUp()
        parent = Disposer.newDisposable("test")
        model = MessageListModel()
        events = mutableListOf()
        model.addListener(parent) { events.add(it) }
    }

    override fun tearDown() {
        try {
            Disposer.dispose(parent)
        } finally {
            super.tearDown()
        }
    }

    // --- addMessage ---

    fun `test addMessage stores entry and fires MessageAdded`() {
        val info = msg("m1", "user")
        model.addMessage(info)

        assertNotNull(model.entry("m1"))
        assertEquals(1, events.size)
        val event = events[0] as MessageModelEvent.MessageAdded
        assertEquals("m1", event.info.id)
    }

    fun `test addMessage duplicate is ignored`() {
        model.addMessage(msg("m1", "user"))
        events.clear()

        model.addMessage(msg("m1", "user"))

        assertEquals(1, model.entries().size)
        assertTrue(events.isEmpty())
    }

    // --- removeMessage ---

    fun `test removeMessage removes entry and fires MessageRemoved`() {
        model.addMessage(msg("m1", "assistant"))
        events.clear()

        model.removeMessage("m1")

        assertNull(model.entry("m1"))
        assertEquals(1, events.size)
        assertTrue(events[0] is MessageModelEvent.MessageRemoved)
        assertEquals("m1", (events[0] as MessageModelEvent.MessageRemoved).id)
    }

    fun `test removeMessage unknown id is noop`() {
        model.removeMessage("unknown")
        assertTrue(events.isEmpty())
    }

    // --- setPartText ---

    fun `test setPartText creates part and fires PartText`() {
        model.addMessage(msg("m1", "assistant"))
        events.clear()

        model.setPartText("m1", "p1", "hello")

        val entry = model.entry("m1")!!
        assertEquals("hello", entry.parts["p1"].toString())
        assertEquals(1, events.size)
        val event = events[0] as MessageModelEvent.PartText
        assertEquals("m1", event.messageId)
        assertEquals("p1", event.partId)
        assertEquals("hello", event.text)
    }

    fun `test setPartText replaces existing text`() {
        model.addMessage(msg("m1", "assistant"))
        model.setPartText("m1", "p1", "old")
        events.clear()

        model.setPartText("m1", "p1", "new")

        assertEquals("new", model.entry("m1")!!.parts["p1"].toString())
        assertEquals(1, events.size)
    }

    fun `test setPartText unknown message is noop`() {
        model.setPartText("unknown", "p1", "text")
        assertTrue(events.isEmpty())
    }

    // --- appendDelta ---

    fun `test appendDelta appends to existing part and fires PartDelta`() {
        model.addMessage(msg("m1", "assistant"))
        model.setPartText("m1", "p1", "hello ")
        events.clear()

        model.appendDelta("m1", "p1", "world")

        assertEquals("hello world", model.entry("m1")!!.parts["p1"].toString())
        assertEquals(1, events.size)
        val event = events[0] as MessageModelEvent.PartDelta
        assertEquals("m1", event.messageId)
        assertEquals("p1", event.partId)
        assertEquals("world", event.delta)
    }

    fun `test appendDelta creates part if missing`() {
        model.addMessage(msg("m1", "assistant"))
        events.clear()

        model.appendDelta("m1", "p1", "first")

        assertEquals("first", model.entry("m1")!!.parts["p1"].toString())
        assertEquals(1, events.size)
        assertTrue(events[0] is MessageModelEvent.PartDelta)
    }

    fun `test appendDelta unknown message is noop`() {
        model.appendDelta("unknown", "p1", "delta")
        assertTrue(events.isEmpty())
    }

    // --- addError ---

    fun `test addError stores error and fires Error`() {
        model.addError("something broke")

        assertEquals(listOf("something broke"), model.errors)
        assertEquals(1, events.size)
        val event = events[0] as MessageModelEvent.Error
        assertEquals("something broke", event.message)
    }

    fun `test addError accumulates errors`() {
        model.addError("first")
        model.addError("second")

        assertEquals(listOf("first", "second"), model.errors)
        assertEquals(2, events.size)
    }

    // --- setStatus ---

    fun `test setStatus stores status and fires StatusChanged`() {
        model.setStatus("thinking")

        assertEquals("thinking", model.status)
        assertEquals(1, events.size)
        val event = events[0] as MessageModelEvent.StatusChanged
        assertEquals("thinking", event.text)
    }

    fun `test setStatus null hides status`() {
        model.setStatus("working")
        events.clear()

        model.setStatus(null)

        assertNull(model.status)
        assertEquals(1, events.size)
        assertNull((events[0] as MessageModelEvent.StatusChanged).text)
    }

    // --- clear ---

    fun `test clear removes entries errors status and fires Cleared`() {
        model.addMessage(msg("m1", "user"))
        model.addError("oops")
        model.setStatus("busy")
        events.clear()

        model.clear()

        assertTrue(model.isEmpty())
        assertTrue(model.errors.isEmpty())
        assertNull(model.status)
        assertEquals(1, events.size)
        assertTrue(events[0] is MessageModelEvent.Cleared)
    }

    // --- loadHistory ---

    fun `test loadHistory populates entries and fires HistoryLoaded`() {
        model.addMessage(msg("old", "user"))
        events.clear()

        val part = PartDto(id = "p1", sessionID = "s1", messageID = "m1", type = "text", text = "hello")
        val history = listOf(
            MessageData(
                msg("m1", "assistant"),
                linkedMapOf("p1" to PartData(part, StringBuilder("hello"))),
            ),
        )

        model.loadHistory(history)

        assertNull(model.entry("old"))
        assertNotNull(model.entry("m1"))
        assertEquals("hello", model.entry("m1")!!.parts["p1"].toString())
        assertEquals(1, events.size)
        assertTrue(events[0] is MessageModelEvent.HistoryLoaded)
    }

    fun `test loadHistory skips non-text parts`() {
        val text = PartDto(id = "p1", sessionID = "s1", messageID = "m1", type = "text", text = "visible")
        val tool = PartDto(id = "p2", sessionID = "s1", messageID = "m1", type = "tool", text = "hidden")
        val history = listOf(
            MessageData(
                msg("m1", "assistant"),
                linkedMapOf(
                    "p1" to PartData(text, StringBuilder("visible")),
                    "p2" to PartData(tool, StringBuilder("hidden")),
                ),
            ),
        )

        model.loadHistory(history)

        val entry = model.entry("m1")!!
        assertTrue(entry.parts.containsKey("p1"))
        assertFalse(entry.parts.containsKey("p2"))
    }

    // --- Listener lifecycle ---

    fun `test listener auto-removed on dispose`() {
        val child = Disposer.newDisposable("child")
        Disposer.register(parent, child)

        val extra = mutableListOf<MessageModelEvent>()
        model.addListener(child) { extra.add(it) }

        model.addMessage(msg("m1", "user"))
        assertEquals(1, extra.size)

        Disposer.dispose(child)
        extra.clear()

        model.addMessage(msg("m2", "user"))
        assertTrue(extra.isEmpty())
    }

    // --- Helpers ---

    private fun msg(id: String, role: String) = MessageDto(
        id = id,
        sessionID = "ses",
        role = role,
        time = MessageTimeDto(created = 0.0),
    )

    private fun <K, V> linkedMapOf(vararg pairs: Pair<K, V>): LinkedHashMap<K, V> {
        val map = LinkedHashMap<K, V>()
        for ((k, v) in pairs) map[k] = v
        return map
    }
}
