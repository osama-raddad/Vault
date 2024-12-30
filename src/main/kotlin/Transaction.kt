package com.vynatix

import kotlinx.coroutines.flow.MutableStateFlow

@JvmInline
value class Timestamp private constructor(private val millisSinceEpoch: Long) {
    companion object {
        fun now(): Timestamp = Timestamp(System.currentTimeMillis())
    }

    override fun toString(): String = millisSinceEpoch.toString()
}

class Transaction(var id: String) {
    private val _modifiedProperties = mutableSetOf<State<out Any>>()
    private val _previousValues = mutableMapOf<State<out Any>, Any>()

    private val propertiesLock = Any()
    private val valuesLock = Any()

    val modifiedProperties: Set<State<out Any>>
        get() = synchronized(propertiesLock) { _modifiedProperties.toSet() }

    val previousValues: Map<State<out Any>, Any>
        get() = synchronized(valuesLock) { _previousValues.toMap() }

    private val _status = MutableStateFlow(TransactionStatus.Active)


    private val _endTime = MutableStateFlow<String?>(null)

    fun <T : Any> recordChange(state: State<T>) {
        synchronized(propertiesLock) {
            synchronized(valuesLock) {
                if (state !in _modifiedProperties) {
                    _previousValues[state] = state.value
                    _modifiedProperties.add(state)
                }
            }
        }
    }

    fun rollback() {
        try {
            synchronized(propertiesLock) {
                synchronized(valuesLock) {
                    val propertiesToRestore = _modifiedProperties.toSet()
                    val previousValuesCopy = _previousValues.toMap()

                    propertiesToRestore.forEach { state ->
                        @Suppress("UNCHECKED_CAST")
                        (state as? MutableState<Any>)?.let { mutableState ->
                            previousValuesCopy[state]?.let { previousValue ->
                                mutableState.value = previousValue
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
            _endTime.value = Timestamp.now().toString()
        }
    }

    fun commit() {
        try {
            synchronized(propertiesLock) {
                updateStatus(TransactionStatus.Committed)
            }
        } catch (e: Exception) {
            updateStatus(TransactionStatus.Failed)
            throw TransactionException("Commit failed", e)
        } finally {
            _endTime.value = Timestamp.now().toString()
        }
    }

    private fun updateStatus(newStatus: TransactionStatus) {
        _status.value = newStatus
    }
}

// Transaction status enum to track lifecycle
enum class TransactionStatus {
    Active,
    Committed,
    RolledBack,
    Failed
}

// Custom exception for transaction-related errors
class TransactionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

// Extension function to execute code within a transaction and handle results

sealed class TransactionResult {
    data class Success(val transaction: Transaction) : TransactionResult()
    data class Error(val exception: Throwable, val transaction: Transaction) : TransactionResult()
}