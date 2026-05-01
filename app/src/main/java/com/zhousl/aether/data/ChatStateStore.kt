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
    private val persistenceQueue = Channel<PendingPersistedChatState>(capacity = Channel.CONFLATED)
    private val _state = MutableStateFlow(PersistedChatState())
    private var localGeneration = 0L
    private var persistedGeneration = 0L

    val state: StateFlow<PersistedChatState> = _state.asStateFlow()

    init {
        scope.launch {
            chatRepository.chatState.collect { persisted ->
                synchronized(updateLock) {
                    if (localGeneration == persistedGeneration) {
                        _state.value = persisted
                    }
                }
            }
        }
        scope.launch {
            for (pending in persistenceQueue) {
                chatRepository.updateChatState(
                    sessions = pending.state.sessions,
                    currentSessionId = pending.state.currentSessionId,
                )
                synchronized(updateLock) {
                    if (pending.generation > persistedGeneration) {
                        persistedGeneration = pending.generation
                    }
                }
            }
        }
    }

    fun update(
        transform: (PersistedChatState) -> PersistedChatState,
    ): PersistedChatState {
        val pending = synchronized(updateLock) {
            val updated = transform(_state.value)
            localGeneration += 1
            _state.value = updated
            PendingPersistedChatState(
                generation = localGeneration,
                state = updated,
            )
        }
        persistenceQueue.trySend(pending)
        return pending.state
    }

    private data class PendingPersistedChatState(
        val generation: Long,
        val state: PersistedChatState,
    )
}
