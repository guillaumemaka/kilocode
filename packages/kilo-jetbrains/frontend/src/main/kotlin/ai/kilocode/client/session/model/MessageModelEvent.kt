package ai.kilocode.client.session.model

import ai.kilocode.rpc.dto.MessageDto

/**
 * Change events fired by [MessageListModel].
 *
 * Events carry the data needed for rendering so [MessageListUi][ai.kilocode.client.session.ui.MessageListUi]
 * can update without reading back from the model (except for [HistoryLoaded]).
 */
sealed class MessageModelEvent {
    data class MessageAdded(val info: MessageDto) : MessageModelEvent()
    data class MessageRemoved(val id: String) : MessageModelEvent()
    data class PartText(val messageId: String, val partId: String, val text: String) : MessageModelEvent()
    data class PartDelta(val messageId: String, val partId: String, val delta: String) : MessageModelEvent()
    data class Error(val message: String) : MessageModelEvent()
    data class StatusChanged(val text: String?) : MessageModelEvent()
    data object HistoryLoaded : MessageModelEvent()
    data object Cleared : MessageModelEvent()

    /**
     * Listener for [MessageModelEvent]s fired by [MessageListModel].
     * All callbacks are guaranteed to run on the EDT.
     */
    fun interface Listener {
        fun onEvent(event: MessageModelEvent)
    }
}
