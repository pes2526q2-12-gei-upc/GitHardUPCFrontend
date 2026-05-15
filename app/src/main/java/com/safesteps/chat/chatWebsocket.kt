package com.safesteps.chat

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

    object ChatEventBus {
    private val _newMessageEvent = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1  // no bloqueja si no hi ha col·lectors actius
    )
    val newMessageEvent: SharedFlow<Unit> = _newMessageEvent.asSharedFlow()

    fun onNewMessage() { _newMessageEvent.tryEmit(Unit) }
}