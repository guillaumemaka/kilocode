package ai.kilocode.client.session

import ai.kilocode.client.app.KiloAppService
import ai.kilocode.client.app.KiloSessionService
import ai.kilocode.client.app.Workspace
import ai.kilocode.client.session.model.MessageListModel
import ai.kilocode.client.session.model.SessionEvent
import ai.kilocode.client.session.model.SessionManager
import ai.kilocode.client.session.ui.LabelPicker
import ai.kilocode.client.session.ui.MessageListUi
import ai.kilocode.client.session.ui.PromptPanel
import ai.kilocode.client.session.ui.StatusPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JPanel

/**
 * Main chat panel — reacts to [SessionManager] events.
 *
 * Uses [CardLayout] in the center to switch between the empty panel
 * (shown before the first prompt) and the scrollable message list.
 *
 * All business logic (app/workspace watching, session lifecycle, event
 * handling, status computation) lives in [SessionManager]. Welcome
 * rendering lives in [ai.kilocode.client.session.ui.StatusPanel].
 * Message list rendering lives in [MessageListUi], driven by
 * [MessageListModel] events. This class handles layout, prompt
 * wiring, model mutations, card switching, picker population,
 * busy state, and scrolling.
 */
class SessionUi(
    project: Project,
    workspace: Workspace,
    sessions: KiloSessionService,
    app: KiloAppService,
    cs: CoroutineScope,
) : JPanel(BorderLayout()), Disposable {

    companion object {
        private const val STATUS = "status"
        private const val MESSAGES = "messages"
    }

    private val model = SessionManager(this, null, sessions, workspace, app, cs)
    private val status = StatusPanel(this, model)
    private val messageList = MessageListModel()
    private val messages = MessageListUi(this, messageList)

    private val cards = CardLayout()
    private val center = JPanel(cards)

    private val scroll = JBScrollPane(messages).apply {
        border = JBUI.Borders.empty()
        verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    }

    private val prompt = PromptPanel(
      project = project,
      onSend = { text -> send(text) },
      onAbort = { model.abort() },
    )

    init {
        // Layout
        center.add(status, STATUS)
        center.add(scroll, MESSAGES)
        cards.show(center, STATUS)

        add(center, BorderLayout.CENTER)
        add(prompt, BorderLayout.SOUTH)

        // Wire picker callbacks via typed model methods
        prompt.mode.onSelect = { item ->
            model.selectAgent(item.id)
        }
        prompt.model.onSelect = { item ->
            val group = item.group
            if (group != null) {
                model.selectModel(group, item.id)
            }
        }

        // React to model events — no coroutines, pure EDT.
        // Message-related events are forwarded to MessageListModel;
        // MessageListUi listens to the model and renders autonomously.
        model.addListener(this) { event ->
            when (event) {
                is SessionEvent.MessageAdded -> {
                    val msg = model.chat.message(event.id) ?: return@addListener
                    messageList.addMessage(msg.info)
                    scrollToBottom()
                }

                is SessionEvent.MessageRemoved -> {
                    messageList.removeMessage(event.id)
                    scrollToBottom()
                }

                is SessionEvent.PartUpdated -> {
                    val part = model.chat.part(event.messageId, event.partId) ?: return@addListener
                    messageList.setPartText(event.messageId, event.partId, part.text.toString())
                    scrollToBottom()
                }

                is SessionEvent.PartDelta -> {
                    messageList.appendDelta(event.messageId, event.partId, event.delta)
                    scrollToBottom()
                }

                is SessionEvent.StatusChanged -> {
                    messageList.setStatus(event.text)
                    scrollToBottom()
                }

                is SessionEvent.Error -> {
                    messageList.addError(event.message)
                    scrollToBottom()
                }

                is SessionEvent.HistoryLoaded -> {
                    messageList.loadHistory(model.chat.messages())
                    scrollToBottom()
                }

                is SessionEvent.Cleared -> {
                    messageList.clear()
                }

                is SessionEvent.WorkspaceReady -> {
                    val c = model.chat
                    prompt.mode.setItems(
                        c.agents.map { LabelPicker.Item(it.name, it.display) },
                        c.agent,
                    )
                    val items = c.models.map { LabelPicker.Item(it.id, it.display, it.provider) }
                    // chat.model is "provider/modelId", picker items use modelId only.
                    // Find the matching item and pass its id for selection.
                    val selected = c.model?.let { full ->
                        items.firstOrNull { "${it.group}/${it.id}" == full }?.id
                    }
                    prompt.model.setItems(items, selected)
                    prompt.setReady(c.ready)
                }

                is SessionEvent.ViewChanged -> {
                    cards.show(center, if (event.show) MESSAGES else STATUS)
                }

                is SessionEvent.BusyChanged -> {
                    prompt.setBusy(event.busy)
                }

                is SessionEvent.AppChanged,
                is SessionEvent.WorkspaceChanged -> {
                    // Handled by StatusPanel
                }
            }
        }
    }

    private fun send(text: String) {
        if (text.isBlank()) return
        model.prompt(text)
        prompt.clear()
    }

    private fun scrollToBottom() {
        val bar = scroll.verticalScrollBar
        bar.value = bar.maximum
    }

    override fun dispose() {
        // All children (status, model) disposed by Disposer
    }
}
