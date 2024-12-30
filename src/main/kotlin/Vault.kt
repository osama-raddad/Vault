package com.vynatix

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class Vault<Self> where Self : Vault<Self> {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _activeTransaction = MutableStateFlow<Transaction?>(null)
    val activeTransaction: StateFlow<Transaction?> = _activeTransaction.asStateFlow()

    private val _properties = mutableMapOf<String, MutableState<*>>()
    val properties: Map<String, State<*>> = _properties

    private val middlewareList = mutableListOf<Middleware<Self>>()

    @Suppress("UNCHECKED_CAST")
    private val self: Self get() = this as Self

    fun middlewares(vararg middleware: Middleware<Self>) {
        middlewareList.addAll(middleware)
    }

    infix fun action(action: Self.() -> Unit): TransactionResult {
        return runBlocking {
            val transaction = Transaction(action::class.simpleName ?: UUID.randomUUID().toString())

            try {
                _activeTransaction.value = transaction

                transaction.executeWithinTransaction {
                    middlewareList.fold({
                        action(self)
                    }) { acc, middleware ->
                        { middleware(self, acc) }
                    }.invoke()
                }.fold(
                    onSuccess = {
                        TransactionResult.Success(transaction)
                    },
                    onFailure = { error ->
                        TransactionResult.Error(error, transaction)
                    }
                )
            } finally {
                _activeTransaction.value = null
            }
        }
    }

    operator fun <R> invoke(block: Self.() -> R): R = block(self)

    fun <T : Any> state(
        validator: StateValidator<T>? = null,
        initialize: Initializer<T>): StateDelegate<T> {
        return StateDelegate { _, property ->
            val existing = _properties[property.name]
            if (existing != null) {
                @Suppress("UNCHECKED_CAST")
                existing as MutableState<T>
            } else {
                MutableState(initialize(), scope,validator).also { state ->
                    _properties[property.name] = state
                }
            }
        }
    }

    infix fun <T : Any> State<T>.effect(effect: T.() -> Unit): Job = scope.launch {
        this@effect.getMutableState().flow.collect(effect::invoke)
    }

    infix fun <T : Any> State<T>.repository(repository: Repository<T>) {
        this.getMutableState().apply {
            this@apply.repository = repository

        }

    }

    infix fun <T : Any> State<T>.mutate(that: T) {
        val state = this.getMutableState()
        val currentTransaction = activeTransaction.value

        // If we have an active transaction, record the change through the transaction's API
        if (currentTransaction != null) {
            runBlocking {  // Note: Using runBlocking since mutate is not suspend
                try {
                    currentTransaction.recordChange(state)
                } catch (e: TransactionException) {
                    throw IllegalStateException("Failed to record state change in transaction", e)
                }
            }
        }

        // Apply the new value
        state.value = that
    }

    private fun <T : Any> State<T>.getMutableState(): MutableState<T> {
        return this as? MutableState<T> ?: error("State must be created by this Vault instance")
    }
}

suspend fun <T> Transaction.executeWithinTransaction(
    block: suspend Transaction.() -> T
): Result<T> = try {
    Result.success(block()).also {
        commit()
    }
} catch (e: Exception) {
    rollback()
    Result.failure(e)
}
