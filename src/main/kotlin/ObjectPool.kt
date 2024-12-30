package com.vynatix

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ObjectPool<T>(
    private val factory: () -> T,
    private val reset: (T) -> Unit
) {
    private val items = mutableListOf<T>()
    private val mutex = Mutex()

    suspend fun acquire(): T = mutex.withLock {
        items.removeFirstOrNull() ?: factory()
    }

    suspend fun release(item: T) = mutex.withLock {
        reset(item)
        items.add(item)
    }
}