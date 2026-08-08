package com.deepeye.agent.ui.history

class HistoryRepositoryImpl(
    private val localStore: HistoryLocalStore
) : HistoryRepository {

    override suspend fun getHistory(): List<HistoryItemUi> {
        return localStore.getAll()
    }

    override suspend fun deleteHistory(id: String) {
        localStore.delete(id)
    }

    override suspend fun exportHistoryItem(id: String) {
        localStore.export(id)
    }
}

interface HistoryLocalStore {
    suspend fun getAll(): List<HistoryItemUi>
    suspend fun delete(id: String)
    suspend fun export(id: String)
}

interface HistoryRepository {
    suspend fun getHistory(): List<HistoryItemUi>
    suspend fun deleteHistory(id: String)
    suspend fun exportHistoryItem(id: String)
}
