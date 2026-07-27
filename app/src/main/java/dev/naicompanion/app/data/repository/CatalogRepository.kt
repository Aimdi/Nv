package dev.naicompanion.app.data.repository

import dev.naicompanion.app.data.catalog.ArtistEntity
import dev.naicompanion.app.data.catalog.CatalogDao
import dev.naicompanion.app.data.catalog.CatalogQuery
import dev.naicompanion.app.data.catalog.CatalogQueryBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

sealed interface CatalogStatus {
    data object Loading : CatalogStatus
    data class Ready(val tagCount: Int, val sources: List<String>) : CatalogStatus

    /** The bundled asset could not be opened. The rest of the app keeps working without it. */
    data class Unavailable(val message: String) : CatalogStatus
}

class CatalogRepository(private val dao: CatalogDao) {

    private val _status = MutableStateFlow<CatalogStatus>(CatalogStatus.Loading)
    val status: StateFlow<CatalogStatus> = _status.asStateFlow()

    suspend fun refresh() {
        _status.value = runCatching { CatalogStatus.Ready(dao.count(), dao.sources()) }
            .getOrElse { error ->
                CatalogStatus.Unavailable(error.message ?: "Tag catalog could not be opened")
            }
    }

    fun observe(query: CatalogQuery): Flow<List<ArtistEntity>> = flow {
        emitAll(dao.searchFlow(CatalogQueryBuilder.build(query)))
    }.catch { error ->
        _status.value = CatalogStatus.Unavailable(error.message ?: "Tag catalog query failed")
        emit(emptyList())
    }

    suspend fun findByName(name: String): ArtistEntity? =
        runCatching { dao.findByName(name) }.getOrNull()

    suspend fun findAllByName(names: List<String>): List<ArtistEntity> =
        runCatching { dao.findAllByName(names) }.getOrDefault(emptyList())

    suspend fun sources(): List<String> = runCatching { dao.sources() }.getOrDefault(emptyList())
}
