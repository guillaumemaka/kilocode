package ai.kilocode.client.session.ui

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.rpc.dto.ModelSelectionDto

internal fun modelPickerRows(
    items: List<ModelPicker.Item>,
    favorites: List<ModelSelectionDto>,
    query: String,
): List<ModelPickerRow> {
    val q = query.trim()
    val all = items.filterNot(ModelText::small)
    val filtered = all.filter {
        ModelSearch.matches(q, it.display) || ModelSearch.matches(q, it.id) || ModelSearch.matches(q, it.providerName)
    }
    val recommended = filtered
        .filter { it.recommendedIndex != null }
        .sortedWith(compareBy<ModelPicker.Item> { it.recommendedIndex }.thenBy { it.display.lowercase() }.thenBy { it.id })
    val grouped = filtered
        .filter { it.recommendedIndex == null }
        .groupBy { it.provider }
        .toList()
        .sortedWith(compareBy<Pair<String, List<ModelPicker.Item>>> { ModelText.providerSort(it.first) })
    val out = mutableListOf<ModelPickerRow>()
    if (q.isBlank()) {
        val byKey = all.associateBy { it.key }
        val fav = favorites.map { "${it.providerID}/${it.modelID}" }.mapNotNull(byKey::get)
        if (fav.isNotEmpty()) {
            out += ModelPickerRow.Header(KiloBundle.message("model.picker.favorites"))
            out += fav.map { ModelPickerRow.Entry(it, favorite = true) }
        }
    }
    if (recommended.isNotEmpty()) {
        out += ModelPickerRow.Header(KiloBundle.message("model.picker.recommended"))
        out += recommended.map { ModelPickerRow.Entry(it, favorite = false) }
    }
    for ((_, list) in grouped) {
        val sorted = list.sortedWith(compareBy<ModelPicker.Item> { it.display.lowercase() }.thenBy { it.id })
        val label = sorted.firstOrNull()?.providerName ?: continue
        out += ModelPickerRow.Header(label)
        out += sorted.map { ModelPickerRow.Entry(it, favorite = false) }
    }
    return out
}
