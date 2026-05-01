package ai.kilocode.rpc.dto

import kotlinx.serialization.Serializable

@Serializable
data class ModelSelectionDto(
    val providerID: String,
    val modelID: String,
)

@Serializable
data class ModelStateDto(
    val favorite: List<ModelSelectionDto> = emptyList(),
)

@Serializable
data class ModelFavoriteUpdateDto(
    val action: String,
    val providerID: String,
    val modelID: String,
)
