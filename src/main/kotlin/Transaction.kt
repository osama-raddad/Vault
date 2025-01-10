package com.vynatix

@JvmInline
value class Timestamp private constructor(private val millisSinceEpoch: Long) {
    companion object {
        fun now(): Timestamp = Timestamp(System.currentTimeMillis())
    }

    override fun toString(): String = millisSinceEpoch.toString()
}

class Transaction(val id: String) {
    private val statusLock = VaultLock()
    private val endTimeLock = VaultLock()
    private val propertiesLock = VaultLock()
    private val valuesLock = VaultLock()

    @Volatile
    private var _status = TransactionStatus.Active
    val status: TransactionStatus
        get() = statusLock.withLock { _status }

    @Volatile
    private var _endTime: String? = null
    val endTime: String?
        get() = endTimeLock.withLock { _endTime }

    private val _modifiedProperties = mutableSetOf<State<out Any>>()
    private val _previousValues = mutableMapOf<State<out Any>, Any>()

    val modifiedProperties: Set<State<out Any>>
        get() = propertiesLock.withLock { _modifiedProperties.toSet() }

    val previousValues: Map<State<out Any>, Any>
        get() = valuesLock.withLock { _previousValues.toMap() }

    fun <T : Any> recordChange(state: State<T>) {
        propertiesLock.withLock {
            valuesLock.withLock {
                if (state !in _modifiedProperties) {
                    val currentValue = state.value
                    _previousValues[state] = currentValue
                    _modifiedProperties.add(state)
                }
            }
        }
    }

    fun rollback() {
        try {
            propertiesLock.withLock {
                valuesLock.withLock {
                    // Create snapshots to minimize lock duration
                    val propertiesToRestore = _modifiedProperties.toSet()
                    val previousValuesCopy = _previousValues.toMap()

                    propertiesToRestore.forEach { state ->
                        @Suppress("UNCHECKED_CAST")
                        (state as? MutableState<Any>)?.let { mutableState ->
                            previousValuesCopy[state]?.let { previousValue ->
                                try {
                                    mutableState.value = previousValue
                                } catch (e: Exception) {
                                    throw TransactionException(
                                        "Failed to restore state during rollback: ${state::class.simpleName}",
                                        e
                                    )
                                }
                            }
                        }
                    }

                    updateStatus(TransactionStatus.RolledBack)
                }
            }
        } catch (e: Exception) {
            updateStatus(TransactionStatus.Failed)
            throw TransactionException("Rollback failed", e)
        } finally {
            endTimeLock.withLock {
                _endTime = Timestamp.now().toString()
            }
        }
    }

    fun commit() {
        try {
            statusLock.withLock {
                if (_status != TransactionStatus.Active) {
                    throw TransactionException("Cannot commit transaction in ${_status} state")
                }

                propertiesLock.withLock {
                    // Verify all modified properties are still valid
                    _modifiedProperties.forEach { state ->
                        if (state !is MutableState<*>) {
                            throw TransactionException("Invalid state modification detected during commit")
                        }
                    }

                    updateStatus(TransactionStatus.Committed)
                }
            }
        } catch (e: Exception) {
            updateStatus(TransactionStatus.Failed)
            throw TransactionException("Commit failed", e)
        } finally {
            endTimeLock.withLock {
                _endTime = Timestamp.now().toString()
            }
        }
    }

    private fun updateStatus(newStatus: TransactionStatus) {
        statusLock.withLock {
            val oldStatus = _status
            if (!isValidStatusTransition(oldStatus, newStatus)) {
                throw TransactionException("Invalid status transition from $oldStatus to $newStatus")
            }
            _status = newStatus
        }
    }

    private fun isValidStatusTransition(from: TransactionStatus, to: TransactionStatus): Boolean {
        return when (from) {
            TransactionStatus.Active -> to in setOf(
                TransactionStatus.Committed,
                TransactionStatus.RolledBack,
                TransactionStatus.Failed
            )
            TransactionStatus.Committed -> false
            TransactionStatus.RolledBack -> false
            TransactionStatus.Failed -> false
        }
    }
}

enum class TransactionStatus {
    Active,
    Committed,
    RolledBack,
    Failed
}

class TransactionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

sealed class TransactionResult {
    data class Success(val transaction: Transaction) : TransactionResult()
    data class Error(val exception: Throwable, val transaction: Transaction) : TransactionResult()
}

fun <T> Transaction.executeWithinTransaction(
    block: Transaction.() -> T
): Result<T> = try {
    Result.success(block()).also {
        commit()
    }
} catch (e: Exception) {
    rollback()
    Result.failure(e)
}