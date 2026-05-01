package ai.kilocode.client.testing

import ai.kilocode.rpc.KiloAppRpcApi
import ai.kilocode.rpc.dto.HealthDto
import ai.kilocode.rpc.dto.KiloAppStateDto
import ai.kilocode.rpc.dto.KiloAppStatusDto
import ai.kilocode.rpc.dto.ModelFavoriteUpdateDto
import ai.kilocode.rpc.dto.ModelSelectionDto
import ai.kilocode.rpc.dto.ModelStateDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake [KiloAppRpcApi] for testing.
 *
 * Push state changes via [state]. Health check returns [health].
 *
 * Every `suspend` method asserts it is NOT called on the EDT.
 */
class FakeAppRpcApi : KiloAppRpcApi {

    val state = MutableStateFlow(KiloAppStateDto(KiloAppStatusDto.DISCONNECTED))
    var health = HealthDto(healthy = true, version = "1.0.0")
    var models = ModelStateDto()

    var connected = false
        private set
    var retries = 0
        private set

    override suspend fun connect() {
        assertNotEdt("connect")
        connected = true
    }

    override suspend fun state(): Flow<KiloAppStateDto> {
        assertNotEdt("state")
        return state
    }

    override suspend fun health(): HealthDto {
        assertNotEdt("health")
        return health
    }

    override suspend fun retry() {
        assertNotEdt("retry")
        retries += 1
    }

    override suspend fun restart() {
        assertNotEdt("restart")
    }

    override suspend fun reinstall() {
        assertNotEdt("reinstall")
    }

    override suspend fun modelState(): ModelStateDto {
        assertNotEdt("modelState")
        return models
    }

    override suspend fun updateModelFavorite(update: ModelFavoriteUpdateDto): ModelStateDto {
        assertNotEdt("updateModelFavorite")
        val key = update.providerID to update.modelID
        val next = when (update.action) {
            "add" -> if (models.favorite.any { it.providerID to it.modelID == key }) {
                models.favorite
            } else {
                listOf(ModelSelectionDto(update.providerID, update.modelID)) + models.favorite
            }
            "remove" -> models.favorite.filterNot { it.providerID to it.modelID == key }
            else -> models.favorite
        }
        models = ModelStateDto(next)
        return models
    }
}
