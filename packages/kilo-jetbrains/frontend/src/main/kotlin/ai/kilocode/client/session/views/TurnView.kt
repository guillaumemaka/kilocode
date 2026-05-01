package ai.kilocode.client.session.views

import ai.kilocode.client.session.model.Message
import ai.kilocode.client.session.ui.SessionLayoutPanel
import ai.kilocode.client.session.ui.SessionStyle
import ai.kilocode.client.session.ui.SessionStyleTarget
import ai.kilocode.client.ui.UiStyle
import com.intellij.util.ui.JBDimension
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager

/**
 * Top-level transcript item representing one conversational turn.
 *
 * A turn contains one user [MessageView] (the "anchor") and the consecutive
 * assistant [MessageView]s that follow it. The turn id matches the user anchor
 * message id, or the first assistant message id when no user message precedes.
 *
 * Children are stacked by [ai.kilocode.client.session.ui.SessionLayout].
 */
class TurnView(
    val id: String,
    private var style: SessionStyle = SessionStyle.current(),
) : SessionLayoutPanel(), SessionStyleTarget {

    constructor(id: String) : this(id, SessionStyle.current())

    private val messages = LinkedHashMap<String, MessageView>()

    init {
        isOpaque = false
        layout = TurnLayout()
    }

    /** Add a new [MessageView] for [msg] at the end of this turn. */
    fun addMessage(msg: Message): MessageView {
        val view = MessageView(msg, style)
        messages[msg.info.id] = view
        add(view)
        revalidate()
        return view
    }

    /** Remove the [MessageView] for [msgId] if present. */
    fun removeMessage(msgId: String) {
        val view = messages.remove(msgId) ?: return
        remove(view)
        revalidate()
    }

    /** Look up a nested [MessageView] by message id. */
    fun messageView(id: String): MessageView? = messages[id]

    /** Ordered message ids currently displayed — stable for test assertions. */
    fun messageIds(): List<String> = messages.keys.toList()

    /** Compact dump for test assertions. */
    fun dump(): String = messages.entries.joinToString(", ") { (id, mv) -> "${mv.role}#$id" }

    override fun applyStyle(style: SessionStyle) {
        this.style = style
        for (view in messages.values) view.applyStyle(style)
        revalidate()
        repaint()
    }
}

private class TurnLayout : LayoutManager {

    override fun addLayoutComponent(name: String, comp: Component) = Unit
    override fun removeLayoutComponent(comp: Component) = Unit

    override fun preferredLayoutSize(parent: Container): Dimension {
        val ins = parent.insets
        val w = maxOf(0, parent.width - ins.left - ins.right)
        var h = ins.top + ins.bottom
        var prev: MessageView? = null
        for (comp in parent.components) {
            if (!comp.isVisible) continue
            if (prev != null) h += gap(prev, comp)
            comp.setSize(w, comp.height.coerceAtLeast(1))
            h += comp.preferredSize.height
            prev = comp as? MessageView
        }
        return JBDimension(w + ins.left + ins.right, h)
    }

    override fun minimumLayoutSize(parent: Container): Dimension = preferredLayoutSize(parent)

    override fun layoutContainer(parent: Container) {
        val ins = parent.insets
        val w = maxOf(0, parent.width - ins.left - ins.right)
        var y = ins.top
        var prev: MessageView? = null
        for (comp in parent.components) {
            if (!comp.isVisible) continue
            if (prev != null) y += gap(prev, comp)
            comp.setSize(w, comp.height.coerceAtLeast(1))
            val h = comp.preferredSize.height
            comp.setBounds(ins.left, y, w, h)
            y += h
            prev = comp as? MessageView
        }
    }

    private fun gap(prev: MessageView?, comp: Component): Int {
        val next = comp as? MessageView ?: return UiStyle.Gap.turn()
        if (prev?.role == "assistant" && next.role == "assistant") return UiStyle.Card.groupGap()
        return UiStyle.Gap.turn()
    }
}
