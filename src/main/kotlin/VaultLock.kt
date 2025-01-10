package com.vynatix

class VaultLock {
    @Volatile
    private var locked = false
    private var lockCount = 0
    private var ownerThreadId = 0L

    fun lock() {
        val currentThreadId = Thread.currentThread().id
        if (isReentrant(currentThreadId)) {
            lockCount++
            return
        }

        while (!tryLock(currentThreadId)) {
            Thread.yield() // Better than busy waiting
        }
    }

    fun unlock() {
        val currentThreadId = Thread.currentThread().id
        synchronized(this) {
            if (!isLocked() || ownerThreadId != currentThreadId) {
                throw IllegalStateException("Cannot unlock: lock not held by current thread")
            }
            lockCount--
            if (lockCount == 0) {
                locked = false
                ownerThreadId = 0
            }
        }
    }

    inline fun <T> withLock(block: () -> T): T {
        lock()
        try {
            return block()
        } finally {
            unlock()
        }
    }

    private fun tryLock(threadId: Long): Boolean {
        synchronized(this) {
            if (!locked) {
                locked = true
                lockCount = 1
                ownerThreadId = threadId
                return true
            }
            return false
        }
    }

    private fun isReentrant(threadId: Long): Boolean =
        synchronized(this) {
            isLocked() && ownerThreadId == threadId
        }

    private fun isLocked(): Boolean = locked
}