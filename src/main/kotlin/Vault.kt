package com.vynatix

abstract class Vault<Self : Vault<Self>> {
    private val transactionLock = VaultLock()
    private val propertiesLock = VaultLock()
    private val middlewareLock = VaultLock()
    private val stateLock = VaultLock()

    @Volatile
    private var _activeTransaction: Transaction? = null
    var activeTransaction: Transaction?
        get() = transactionLock.withLock { _activeTransaction }
        private set(value) = transactionLock.withLock { _activeTransaction = value }

    private val _properties = mutableMapOf<String, MutableState<*>>()
    val properties: Map<String, State<*>>
        get() = propertiesLock.withLock { _properties.toMap() }

    private val middlewareList = mutableListOf<Middleware<Self>>()

    @Suppress("UNCHECKED_CAST")
    private val self: Self get() = this as Self

    fun middlewares(vararg middleware: Middleware<Self>) {
        middlewareLock.withLock {
            middlewareList.addAll(middleware)
        }
    }

    fun clearMiddleware() {
        middlewareLock.withLock {
            middlewareList.clear()
        }
    }

    infix fun action(action: Self.() -> Unit): TransactionResult {
        return transactionLock.withLock {
            val transaction = Transaction(
                action::class.simpleName ?: UUID.randomUUID().toString()
            )

            try {
                activeTransaction = transaction

                return@withLock transaction.executeWithinTransaction {
                    middlewareLock.withLock {
                        // Create a snapshot of middleware to reduce lock time
                        val currentMiddleware = middlewareList.toList()

                        currentMiddleware.fold({
                            action(self)
                        }) { acc, middleware ->
                            { middleware(self, acc) }
                        }.invoke()
                    }
                }.fold(
                    onSuccess = {
                        TransactionResult.Success(transaction)
                    },
                    onFailure = { error ->
                        TransactionResult.Error(error, transaction)
                    }
                )
            } finally {
                activeTransaction = null
            }
        }
    }

    operator fun <R> invoke(block: Self.() -> R): R = stateLock.withLock {
        block(self)
    }

    fun <T : Any> state(
        transformer: Transformer<T>? = null,
        initialize: Initializer<T>
    ): StateDelegate<T> {
        return StateDelegate { _, property ->
            propertiesLock.withLock {
                val existing = _properties[property.name]
                if (existing != null) {
                    @Suppress("UNCHECKED_CAST")
                    existing as MutableState<T>
                } else {
                    MutableState(initialize(), transformer).also { state ->
                        _properties[property.name] = state
                    }
                }
            }
        }
    }

    infix fun <T : Any> State<T>.effect(effect: T.() -> Unit): Disposable = stateLock.withLock {
        this@effect.getMutableState().observe(effect::invoke)
    }

    infix fun <T : Any> State<T>.bridge(bridge: Bridge<T>) {
        stateLock.withLock {
            this.getMutableState().apply {
                this@apply.bridge = bridge
            }
        }
    }

    infix fun <T : Any> State<T>.mutate(that: T) {
        stateLock.withLock {
            val state = this.getMutableState()
            val currentTransaction = activeTransaction

            if (currentTransaction != null) {
                try {
                    currentTransaction.recordChange(state)
                } catch (e: TransactionException) {
                    throw IllegalStateException("Failed to record state change in transaction", e)
                }
            }

            state.value = that
        }
    }

    private fun <T : Any> State<T>.getMutableState(): MutableState<T> {
        return propertiesLock.withLock {
            this as? MutableState<T> ?: error("State must be created by this Vault instance")
        }
    }

    fun getState(name: String): State<*>? = propertiesLock.withLock {
        _properties[name]
    }

    fun hasState(name: String): Boolean = propertiesLock.withLock {
        _properties.containsKey(name)
    }

    fun removeState(name: String) {
        propertiesLock.withLock {
            _properties.remove(name)
        }
    }

    fun clearStates() {
        propertiesLock.withLock {
            _properties.clear()
        }
    }
}
