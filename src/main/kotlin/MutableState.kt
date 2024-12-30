package com.vynatix

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock


interface StateValidator<T : Any> {
    fun validate(value: T): Boolean
    fun getValidationError(value: T): String?
}


class StateValidationException(message: String) : Exception(message)

class MutableState<T : Any>(
    initialValue: T,
    scope: CoroutineScope,
    private val validator: StateValidator<T>? = null,
) : State<T> {
    private val stateRef = AtomicReference(initialValue)
    private val stateFlow = MutableStateFlow(initialValue)
    private val writeLock = ReentrantReadWriteLock()
    private val lastModifiedTime = AtomicReference(Instant.now())

    override var value: T
        get() = stateRef.get()
        set(newValue) {
            validateState(newValue)
            updateState(newValue)
        }

    var repository: Repository<T>? = null
        set(value) {
            field = value
            value?.transmitted {
                updateState(it)
            }
        }

    init {
        scope.launch {
            stateFlow.collect { value ->
                    repository?.received(value)
                }
        }
    }

    val flow: StateFlow<T> = stateFlow.asStateFlow()

    private fun validateState(newValue: T) {
        validator?.let { validator ->
            if (!validator.validate(newValue)) {
                throw StateValidationException(
                    validator.getValidationError(newValue) ?: "Invalid state"
                )
            }
        }
    }

    private fun updateState(newValue: T) {
        writeLock.writeLock().withLock {
            validateState(newValue)
            val oldValue = stateRef.get()
            stateRef.set(newValue)
            stateFlow.value = newValue
            lastModifiedTime.set(Instant.now())
        }
    }
}

