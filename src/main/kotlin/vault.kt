package com.vynatix

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.reflect.KProperty


interface IAsset<T : Any> : SharedFlow<T> {
    suspend infix fun repository(repository: IRepository<T>)
    operator fun invoke(): T
}

interface IRepository<T : Any> {
    fun flow(): SharedFlow<T>
    infix fun set(value: T)
}

fun interface IMiddleware<T : IVault> : suspend (T, suspend () -> Unit) -> Unit

interface IVaultScope {
    suspend operator fun <T : Any> IAsset<T>.invoke(value: T)
}

fun interface IAssetDelegate<T : Any> {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): IAsset<T>
}

interface IVault {
    val properties: Map<String, IAsset<*>>
    val activeOperation: StateFlow<Operation?>
    suspend fun operation(useCaseId: String, action: suspend IVaultScope.() -> Unit): OperationResult
    fun middlewares(vararg middleware: Middleware<IVault>)
    infix fun <T : Any> asset(initialize: () -> T): IAssetDelegate<T>
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

object OperationPool {
    private val pool = ObjectPool(
        factory = { Operation("") },
        reset = { op ->
            op.modifiedProperties.clear()
            op.previousValues.clear()
        }
    )

    suspend fun acquire(useCaseId: String): Operation = pool.acquire().apply {
        this.useCaseId = useCaseId
    }

    suspend fun release(operation: Operation) {
        pool.release(operation)
    }
}

data class Operation(
    var useCaseId: String,
    val modifiedProperties: MutableSet<IAsset<out Any>> = mutableSetOf(),
    val previousValues: MutableMap<IAsset<out Any>, Any> = mutableMapOf()
)

sealed class OperationResult {
    data class Success(val operation: Operation) : OperationResult()
    data class Error(val exception: Throwable, val operation: Operation) : OperationResult()
}

class AssetFactory {
    operator fun <T : Any> invoke(initialize: () -> T): IAsset<T> = Asset(initialize)
}


class VaultFactory(
    private val assetFactory: AssetFactory = AssetFactory()
) {
    operator fun invoke(): IVault = Vault(assetFactory)
}

class Asset<T : Any>(
    private val initialize: () -> T,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val _flow: MutableSharedFlow<T> = MutableSharedFlow(replay = 1)
) : IAsset<T>, SharedFlow<T> by _flow {
    private var _repository: IRepository<T>? = null
    private var _value = initialize()
        get() = _flow.replayCache.firstOrNull() ?: field

    override suspend infix fun repository(repository: IRepository<T>) {
        _repository = repository
        scope.launch { repository.flow().collect { _value = it }}
    }

    override operator fun invoke(): T = _value

    internal operator fun invoke(value: T) {
        _value = value
        _repository?.set(value)
        _flow.tryEmit(value)
    }
}


open class Middleware<T : IVault> : IMiddleware<T> {
    data class MiddlewareContext<T : IVault>(
        val vault: T,
        val transaction: Operation,
        val metadata: MutableMap<String, Any> = mutableMapOf()
    )

    private suspend fun execute(context: MiddlewareContext<T>, next: suspend () -> Unit) {
        try {
            onTransactionStarted(context)
            next()
            onTransactionCompleted(context)
        } catch (e: Throwable) {
            onTransactionError(context, e)
            throw e
        }
    }

    override suspend fun invoke(vault: T, next: suspend () -> Unit) {
        val context = MiddlewareContext(
            vault = vault,
            transaction = vault.activeOperation.value ?: error("No active transaction")
        )
        execute(context, next)
    }

    protected open suspend fun onTransactionStarted(context: MiddlewareContext<T>) {}
    protected open suspend fun onTransactionCompleted(context: MiddlewareContext<T>) {}
    protected open suspend fun onTransactionError(
        context: MiddlewareContext<T>,
        error: Throwable
    ) {
    }
}

class Vault(
    private val assetFactory: AssetFactory
) : IVault {
    private val middlewares = mutableListOf<Middleware<IVault>>()
    override val properties = mutableMapOf<String, IAsset<out Any>>()
    override val activeOperation = MutableStateFlow<Operation?>(null)

    override fun middlewares(vararg middleware: Middleware<IVault>) {
        middlewares.addAll(middleware)
    }

    override suspend fun operation(
        useCaseId: String,
        action: suspend IVaultScope.() -> Unit
    ): OperationResult {
        val operation = OperationPool.acquire(useCaseId)
        activeOperation.value = operation

        return try {
            properties.forEach { (_, prop) -> operation.previousValues[prop] = prop() }
            createMiddlewareChain(action)()
            OperationResult.Success(operation)
        } catch (e: Throwable) {
            operation.previousValues.forEach { (asset, value) ->
                (asset as Asset<Any>)(value)
            }
            OperationResult.Error(e, operation).also { throw e }
        } finally {
            activeOperation.value = null
            OperationPool.release(operation)
        }
    }

    private fun createMiddlewareChain(action: suspend IVaultScope.() -> Unit): suspend () -> Unit {
        val vaultScope = object : IVaultScope {
            override suspend fun <T : Any> IAsset<T>.invoke(value: T) {
                val currentOperation = activeOperation.value
                    ?: throw IllegalStateException("No active operation")
                currentOperation.modifiedProperties.add(this)
                (this as Asset)(value)
            }
        }
        return middlewares.foldRight({ vaultScope.action() }) { middleware, next ->
            { middleware(this) { next() } }
        }
    }

    override infix  fun <T : Any> asset(
        initialize: () -> T,
    ): IAssetDelegate<T> = assetFactory(initialize).run {
        IAssetDelegate { _, property ->
            properties[property.name] = this
            this
        }
    }
}


///////////// example
class UserRepository : IRepository<String> {
    private val _dataFlow = MutableSharedFlow<String>(replay = 1)


    override fun set(value: String) {
        _dataFlow.tryEmit(value)
    }

    override fun flow(): SharedFlow<String> = _dataFlow.asSharedFlow()
}

class UserVault(vaultFactory: VaultFactory) : IVault by vaultFactory() {
    val username by asset { "John Doe" }
    val email by asset { "none" }
    val loginAttempts by asset { 1 }
    val isLoggedIn by asset { false }
}

class LoginUseCase(private val vault: UserVault) {
    suspend operator fun invoke() = vault.operation("login") {
        vault.username("Osama Raddad")
        vault.email("jane@example.com")
        vault.loginAttempts(1)
        vault.isLoggedIn(true)
    }
}

data object LoggingMiddleware : Middleware<IVault>() {
    override suspend fun onTransactionStarted(context: MiddlewareContext<IVault>) {
        println("Operation started: ${context.transaction.useCaseId}")

        println("Initial state:")
        context.transaction.previousValues.forEach { (asset, value) ->
            println("  ${getAssetName(context.vault, asset)}: $value")
        }
    }

    override suspend fun onTransactionCompleted(context: MiddlewareContext<IVault>) {
        println("\nOperation completed: ${context.transaction.useCaseId}")
        println("Modified properties:")
        context.vault.properties
            .filter { (_, asset) -> context.transaction.previousValues[asset] != asset() }
            .forEach { (name, asset) ->
                val previousValue = context.transaction.previousValues[asset]
                val currentValue = asset()
                println("  $name: $previousValue -> $currentValue")
            }
    }

    override suspend fun onTransactionError(context: MiddlewareContext<IVault>, error: Throwable) {
        println("\nOperation failed: ${context.transaction.useCaseId}")
        println("Error: $error")
    }

    private fun getAssetName(vault: IVault, asset: IAsset<*>): String {
        return vault.properties.entries
            .find { it.value === asset }
            ?.key ?: "unknown"
    }
}

fun main() = runBlocking {
    val userVault = UserVault(VaultFactory()).apply {
        middlewares(LoggingMiddleware)
    }

    val repo = UserRepository()
    userVault.username.onEach {
        println(it)
    }.launchIn(this)
    userVault.username repository repo


    delay(1000)
    LoginUseCase(userVault)()
    delay(1000)

    println("\nCurrent State:")
    println("Username: ${userVault.username()}")
    println("Email: ${userVault.email()}")
    println("Login Attempts: ${userVault.loginAttempts()}")
    println("Is Logged In: ${userVault.isLoggedIn()}")
}


//////////////////