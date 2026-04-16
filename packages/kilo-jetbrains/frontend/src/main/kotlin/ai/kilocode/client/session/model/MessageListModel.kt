package ai.kilocode.client.session.model

import ai.kilocode.rpc.dto.MessageDto
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

/**
 * Pure data model for the message list.
 *
 * Holds messages, parts, status, and errors. All mutations fire
 * [MessageModelEvent]s to registered [MessageModelEvent.Listener]s.
 *
 * **EDT-only access** — no synchronization. The caller ([SessionUi])
 * guarantees all reads and writes happen on the EDT.
 */
class MessageListModel {

    private val entries = LinkedHashMap<String, MessageModel>()
    private val _errors = mutableListOf<String>()

    /** Current status text. Null means hidden. */
    var status: String? = null
        private set

    /** Accumulated error messages. */
    val errors: List<String> get() = _errors

    private val listeners = mutableListOf<MessageModelEvent.Listener>()

    // --- Listener management ---

    fun addListener(parent: Disposable, listener: MessageModelEvent.Listener) {
        listeners.add(listener)
        Disposer.register(parent) { listeners.remove(listener) }
    }

    // --- Read ---

    fun entries(): Collection<MessageModel> = entries.values

    fun entry(id: String): MessageModel? = entries[id]

    fun isEmpty(): Boolean = entries.isEmpty()

    // --- Mutations ---

    fun addMessage(info: MessageDto) {
        if (entries.containsKey(info.id)) return
        entries[info.id] = MessageModel(info)
        fire(MessageModelEvent.MessageAdded(info))
    }

    fun removeMessage(id: String) {
        if (entries.remove(id) == null) return
        fire(MessageModelEvent.MessageRemoved(id))
    }

    fun setPartText(messageId: String, partId: String, text: String) {
        val entry = entries[messageId] ?: return
        entry.parts.getOrPut(partId) { StringBuilder() }.apply {
            clear()
            append(text)
        }
        fire(MessageModelEvent.PartText(messageId, partId, text))
    }

    fun appendDelta(messageId: String, partId: String, delta: String) {
        val entry = entries[messageId] ?: return
        entry.parts.getOrPut(partId) { StringBuilder() }.append(delta)
        fire(MessageModelEvent.PartDelta(messageId, partId, delta))
    }

    fun addError(message: String) {
        _errors.add(message)
        fire(MessageModelEvent.Error(message))
    }

    fun setStatus(text: String?) {
        status = text
        fire(MessageModelEvent.StatusChanged(text))
    }

    fun clear() {
        entries.clear()
        _errors.clear()
        status = null
        fire(MessageModelEvent.Cleared)
    }

    /**
     * Bulk-load message history. Clears existing entries first,
     * populates from [SessionState] data, and fires a single
     * [MessageModelEvent.HistoryLoaded].
     */
    fun loadHistory(messages: Collection<MessageData>) {
        entries.clear()
        _errors.clear()
        status = null
        for (msg in messages) {
            val entry = MessageModel(msg.info)
            for ((partId, part) in msg.parts) {
                if (part.dto.type == "text" && part.text.isNotEmpty()) {
                    entry.parts[partId] = StringBuilder(part.text)
                }
            }
            entries[msg.info.id] = entry
        }
        fire(MessageModelEvent.HistoryLoaded)
    }

    private fun fire(event: MessageModelEvent) {
        for (l in listeners) l.onEvent(event)
    }
}
