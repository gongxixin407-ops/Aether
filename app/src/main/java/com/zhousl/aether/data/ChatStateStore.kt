package com.zhousl.aether.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatStateStore(
    scope: CoroutineScope,
    private val chatRepository: ChatRepository,
) {
    private val updateLock = Any()
    private val persistenceQueue = Channel<PersistedChatState>(capacity = Channel.UNLIMITED)
    private val _state = MutableStateFlow(PersistedChatState())

    val state: StateFlow<PersistedChatState> = _state.asStateFlow()

    init {
        scope.launch {
            chatRepository.chatState.collect { persisted ->
                _state.value = persisted
            }
        }
        scope.launch {
            for (persisted in persistenceQueue) {
                chatRepository.updateChatState(
                    sessions = persisted.sessions,
                    currentSessionId = persisted.currentSessionId,
                )
            }
        }
    }

    fun update(
        transform: (PersistedChatState) -> PersistedChatState,
    ): PersistedChatState {
        val updated = synchronized(updateLock) {
            transform(_state.value).also { _state.value = it }
        }
        persistenceQueue.trySend(updated)
        return updated
    }
}
