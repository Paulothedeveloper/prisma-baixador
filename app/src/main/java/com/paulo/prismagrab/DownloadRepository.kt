package com.paulo.prismagrab

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

enum class DlStatus { FILA, BUSCANDO, BAIXANDO, PRONTO, ERRO, CANCELADO }

data class DlItem(
    val id: String,
    val url: String,
    val title: String = "",
    val progress: Int = 0,
    val status: DlStatus = DlStatus.FILA,
    val message: String = "",
    val audioOnly: Boolean = false,
)

/** Fonte única do estado da fila. Service escreve, UI observa. */
object DownloadRepository {
    val items = MutableStateFlow<List<DlItem>>(emptyList())

    // ids que o usuário pediu pra cancelar (o loop do Service consulta)
    val cancelRequested = MutableStateFlow<Set<String>>(emptySet())

    fun add(item: DlItem) = items.update { it + item }

    fun patch(id: String, f: (DlItem) -> DlItem) =
        items.update { list -> list.map { if (it.id == id) f(it) else it } }

    fun requestCancel(id: String) {
        cancelRequested.update { it + id }
        Downloader.cancel(id)
    }

    fun consumeCancel(id: String): Boolean {
        val hit = cancelRequested.value.contains(id)
        if (hit) cancelRequested.update { it - id }
        return hit
    }

    fun clearFinished() = items.update { list ->
        list.filter { it.status == DlStatus.FILA || it.status == DlStatus.BUSCANDO || it.status == DlStatus.BAIXANDO }
    }
}
