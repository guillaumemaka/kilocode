package ai.kilocode.client.session.ui

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.ui.UiStyle
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupShowOptions
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ListUtil
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

class ModePicker : JBLabel() {

    data class Item(
        val id: String,
        val display: String,
        val description: String? = null,
        val deprecated: Boolean = false,
    ) {
        override fun toString(): String = listOfNotNull(display, description).joinToString(" ")
    }

    var onSelect: (Item) -> Unit = {}

    private var items: List<Item> = emptyList()
    private var selected: Item? = null

    init {
        border = UiStyle.Borders.picker()
        isEnabled = false
        text = " "

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (!isEnabled || items.isEmpty()) return
                showPopup()
            }
        })
    }

    fun setItems(values: List<Item>, default: String? = null) {
        items = values.sortedWith(compareBy<Item> { it.display.lowercase() }.thenBy { it.id })
        selected = if (default != null) items.firstOrNull { it.id == default } else items.firstOrNull()
        refresh()
    }

    fun select(id: String) {
        selected = items.firstOrNull { it.id == id }
        refresh()
    }

    internal fun itemsForTest(): List<Item> = items

    private fun refresh() {
        if (items.isEmpty()) {
            isEnabled = false
            text = " "
            cursor = Cursor.getDefaultCursor()
            return
        }
        val display = selected?.display ?: items.firstOrNull()?.display ?: ""
        text = "$display ▴"
        isEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    private fun showPopup() {
        val model = CollectionListModel(items)
        val list = JBList(model).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            visibleRowCount = minOf(ModePickerRenderer.MAX_ROWS, items.size.coerceAtLeast(1))
            cellRenderer = ModePickerRenderer { selected?.id }
        }
        val idx = items.indexOfFirst { it.id == selected?.id }.takeIf { it >= 0 } ?: 0
        list.selectedIndex = idx
        ListUtil.installAutoSelectOnMouseMove(list)
        ScrollingUtil.installActions(list)
        TreeUIHelper.getInstance().installListSpeedSearch(list) { it.toString() }

        lateinit var popup: JBPopup

        fun activate(item: Item) {
            selected = item
            refresh()
            onSelect(item)
            popup.closeOk(null)
        }

        list.registerKeyboardAction(
            { list.selectedValue?.let(::activate) },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JComponent.WHEN_FOCUSED,
        )
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                val row = list.locationToIndex(e.point)
                val bounds = row.takeIf { it >= 0 }?.let { list.getCellBounds(it, it) }
                if (SwingUtilities.isLeftMouseButton(e) && bounds?.contains(e.point) == true) {
                    activate(list.model.getElementAt(row))
                }
            }
        })

        val content = JBUI.Panels.simplePanel(ScrollPaneFactory.createScrollPane(list))
        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(content, list)
            .setRequestFocus(true)
            .setCancelOnClickOutside(true)
            .setCancelKeyEnabled(true)
            .setResizable(false)
            .setMovable(false)
            .createPopup()

        popup.show(PopupShowOptions.aboveComponent(this))
        list.ensureIndexIsVisible(idx)
    }
}

internal class ModePickerRenderer(
    private val active: () -> String?,
) : JPanel(BorderLayout()), ListCellRenderer<ModePicker.Item> {

    companion object {
        const val MAX_ROWS = 8
        val checked: Icon = AllIcons.Actions.Checked
        val empty: Icon = EmptyIcon.create(checked)
    }

    private val icon = JBLabel().apply {
        UiStyle.Components.transparent(this)
    }
    private val title = SimpleColoredComponent().apply {
        UiStyle.Components.transparent(this)
    }
    private val desc = SimpleColoredComponent().apply {
        UiStyle.Components.transparent(this)
    }
    private val badge = JBLabel(KiloBundle.message("mode.picker.deprecated")).apply {
        UiStyle.Components.transparent(this)
    }
    private val head = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
        UiStyle.Components.transparent(this)
        add(title)
        add(badge)
    }
    private val body = JPanel(BorderLayout()).apply {
        UiStyle.Components.transparent(this)
    }

    init {
        isOpaque = true
        (layout as BorderLayout).hgap = UiStyle.Gap.inline()
        border = JBUI.Borders.empty(UiStyle.Space.MD, UiStyle.Space.LG)
        icon.horizontalAlignment = SwingConstants.CENTER
        icon.verticalAlignment = SwingConstants.CENTER
        body.add(head, BorderLayout.NORTH)
        body.add(desc, BorderLayout.CENTER)
        add(icon, BorderLayout.WEST)
        add(body, BorderLayout.CENTER)
    }

    override fun getListCellRendererComponent(
        list: JList<out ModePicker.Item>,
        value: ModePicker.Item,
        index: Int,
        selected: Boolean,
        focused: Boolean,
    ): java.awt.Component {
        val focus = list.hasFocus() || focused
        val fg = UIUtil.getListForeground(selected, focus)
        val bg = UIUtil.getListBackground(selected, focus)
        val weak = if (selected) fg else UiStyle.Colors.weak()
        val warn = if (selected) fg else UiStyle.Colors.warning()

        background = bg
        title.clear()
        title.append(value.display, SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, fg))
        desc.clear()
        desc.isVisible = value.description?.isNotBlank() == true
        value.description?.takeIf { it.isNotBlank() }?.let {
            desc.append(it, SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, weak))
        }
        badge.isVisible = value.deprecated
        badge.foreground = warn
        badge.border = JBUI.Borders.compound(
            JBUI.Borders.emptyLeft(JBUI.CurrentTheme.ActionsList.elementIconGap()),
            JBUI.Borders.compound(
                RoundedLineBorder(warn, JBUI.scale(UiStyle.Space.SM)),
                JBUI.Borders.empty(0, UiStyle.Space.MD),
            ),
        )
        icon.icon = icon(value)
        return this
    }

    internal fun icon(value: ModePicker.Item): Icon = when {
        value.id != active() -> empty
        else -> checked
    }

    internal fun badgeVisible(): Boolean = badge.isVisible

    internal fun badgeText(): String = badge.text
}
