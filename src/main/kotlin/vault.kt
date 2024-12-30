package com.vynatix

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import kotlin.collections.set
import kotlin.reflect.KProperty



fun interface Initializer<T : Any> : () -> T
fun interface State<T : Any> : () -> T
fun interface Action<V : Vault<V>> : (V) -> Unit
fun interface Effect<T : Any> : (T) -> Unit
interface Repository<T : Any> {
    fun flow(): SharedFlow<T>
    infix fun set(value: T): Boolean
}

fun interface StateDelegate<T : Any> {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): State<T>
}

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

object TransactionPool {
    private val pool = ObjectPool(
        factory = { Transaction("") },
        reset = { op ->
            op.modifiedProperties.clear()
            op.previousValues.clear()
        }
    )

    suspend fun acquire(id: String): Transaction = pool.acquire().apply {
        this.id = id
    }

    suspend fun release(transaction: Transaction) {
        pool.release(transaction)
    }
}

data class Transaction(
    var id: String,
    val modifiedProperties: MutableSet<State<out Any>> = mutableSetOf(),
    val previousValues: MutableMap<State<out Any>, Any> = mutableMapOf()
)

sealed class TransactionResult {
    data class Success(val transaction: Transaction) : TransactionResult()
    data class Error(val exception: Throwable, val transaction: Transaction) : TransactionResult()
}


open class Middleware<T : Vault<T>> {
    data class MiddlewareContext<T : Vault<T>>(
        val vault: T,
        val transaction: Transaction,
        val metadata: MutableMap<String, Any> = mutableMapOf()
    )

    private fun execute(context: MiddlewareContext<T>, next: () -> Unit) {
        try {
            onTransactionStarted(context)
            next()
            onTransactionCompleted(context)
        } catch (e: Throwable) {
            onTransactionError(context, e)
            throw e
        }
    }

    operator fun invoke(vault: T, next: () -> Unit) {
        val context = MiddlewareContext(
            vault = vault,
            transaction = vault.activeTransaction.value ?: error("No active transaction")
        )
        execute(context, next)
    }

    protected open fun onTransactionStarted(context: MiddlewareContext<T>) {}
    protected open fun onTransactionCompleted(context: MiddlewareContext<T>) {}
    protected open fun onTransactionError(
        context: MiddlewareContext<T>,
        error: Throwable
    ) {
    }
}

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

    infix fun action(action: Action<Self>): TransactionResult {
        return runBlocking {
            val transaction = TransactionPool.acquire(action::class.simpleName ?: UUID.randomUUID().toString())
            try {
                _activeTransaction.value = transaction

                try {
                    middlewareList.fold({
                        action(self)
                    }) { acc, middleware ->
                        { middleware(self, acc) }
                    }.invoke()

                    TransactionResult.Success(transaction)
                } catch (e: Throwable) {
                    TransactionResult.Error(e, transaction)
                } finally {
                    _activeTransaction.value = null
                    TransactionPool.release(transaction)
                }
            } catch (e: Throwable) {
                TransactionResult.Error(e, transaction)
            }
        }
    }

    operator fun <R> invoke(block: Self.() -> R): R = block(self)

    fun <T : Any> state(initialize: Initializer<T>): StateDelegate<T> {
        return StateDelegate { _, property ->
            val existing = _properties[property.name]
            if (existing != null) {
                @Suppress("UNCHECKED_CAST")
                existing as MutableState<T>
            } else {
                MutableState(initialize(), scope).also { state ->
                    _properties[property.name] = state
                }
            }
        }
    }

    infix fun <T : Any> State<T>.effect(effect: Effect<T>): Job = scope.launch {
        this@effect.getMutableState().flow.collect(effect::invoke)
    }

    infix fun <T : Any> State<T>.repository(repository: Repository<T>) {
        this.getMutableState().apply {
            this@apply.repository = repository

        }

    }

    infix fun <T : Any> State<T>.mutate(that: T) {
        val state = this.getMutableState()
        val transaction = activeTransaction.value

        transaction?.let {
            if (state !in it.modifiedProperties) {
                it.previousValues[state] = state.get()
                it.modifiedProperties.add(state)
            }
        }

        state.set(that)
    }

    private fun <T : Any> State<T>.getMutableState(): MutableState<T> {
        return this as? MutableState<T> ?: error("State must be created by this Vault instance")
    }


}

private class MutableState<T : Any>(
    initialValue: T,
    scope: CoroutineScope
) : State<T> {
    lateinit var repository: Repository<T>
    val flow: StateFlow<T> by lazy { repository.flow().stateIn(scope, SharingStarted.Eagerly, initialValue) }
    fun get(): T = flow.value
    fun set(value: T) = repository.set(value)
    override fun invoke(): T = get()
}