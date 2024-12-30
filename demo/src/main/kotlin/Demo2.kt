import com.vynatix.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import kotlin.random.Random


// Data models
data class User(
    val id: String,
    val name: String,
    val age: Int,
    val email: String,
    val roles: Set<Role> = emptySet()
)

enum class Role { ADMIN, USER, GUEST }

data class AuditLog(
    val action: String,
    val timestamp: Instant = Instant.now(),
    val userId: String
)

data class Metrics(
    val operationCount: Int = 0,
    val errorCount: Int = 0,
    val lastOperationTime: Long = 0
)

data class ErrorState(
    val message: String = "",
    val timestamp: Instant = Instant.now(),
    val isActive: Boolean = false
)

// Validators
class ComplexUserValidator : StateValidator<User> {
    override fun validate(value: User): Boolean {
        return value.age >= 18 &&
                value.email.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\$")) &&
                value.name.length in 2..50 &&
                value.roles.isNotEmpty()
    }

    override fun getValidationError(value: User): String? {
        return when {
            value.age < 18 -> "User must be 18 or older"
            !value.email.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\$")) -> "Invalid email format"
            value.name.length !in 2..50 -> "Name must be between 2 and 50 characters"
            value.roles.isEmpty() -> "User must have at least one role"
            else -> null
        }
    }
}

// Repositories
class InMemoryUserRepository : Repository<User> {
    private val observers = mutableListOf<(User) -> Unit>()
    private val users = mutableMapOf<String, User>()

    override fun transmitted(observer: (User) -> Unit): Boolean {
        observers.add(observer)
        users.values.forEach(observer)
        return true
    }

    override fun received(value: User): Boolean {
        users[value.id] = value
        observers.forEach { it(value) }
        return true
    }
}

class AuditLogRepository : Repository<List<AuditLog>> {
    private val observers = mutableListOf<(List<AuditLog>) -> Unit>()
    private val logs = mutableListOf<AuditLog>()

    override fun transmitted(observer: (List<AuditLog>) -> Unit): Boolean {
        observers.add(observer)
        observer(logs.toList())
        return true
    }

    override fun received(value: List<AuditLog>): Boolean {
        logs.clear()
        logs.addAll(value)
        observers.forEach { it(logs.toList()) }
        return true
    }
}

// Custom Middleware
class MetricsMiddleware<T : Vault<T>> : Middleware<T>() {
    private val metrics = MutableStateFlow(Metrics())
    private val operationTimes = mutableMapOf<String, Long>()

    override fun onTransactionStarted(context: MiddlewareContext<T>) {
        operationTimes[context.transaction.id] = System.currentTimeMillis()
        metrics.value = metrics.value.copy(
            operationCount = metrics.value.operationCount + 1
        )
    }

    override fun onTransactionError(context: MiddlewareContext<T>, error: Throwable) {
        metrics.value = metrics.value.copy(
            errorCount = metrics.value.errorCount + 1
        )
    }

    override fun onTransactionCompleted(context: MiddlewareContext<T>) {
        val startTime = operationTimes.remove(context.transaction.id) ?: return
        val duration = System.currentTimeMillis() - startTime
        metrics.value = metrics.value.copy(
            lastOperationTime = duration
        )
    }

    fun getMetrics() = metrics.value
}

class ResourceCleanupMiddleware<T : Vault<T>> : Middleware<T>() {
    private val resources = mutableSetOf<AutoCloseable>()

    fun registerResource(resource: AutoCloseable) {
        resources.add(resource)
    }

    override fun onTransactionCompleted(context: MiddlewareContext<T>) {
        cleanupResources()
    }

    override fun onTransactionError(context: MiddlewareContext<T>, error: Throwable) {
        cleanupResources()
    }

    private fun cleanupResources() {
        resources.forEach { resource ->
            try {
                resource.close()
            } catch (e: Exception) {
                println("Failed to close resource: ${e.message}")
            }
        }
        resources.clear()
    }
}

// Enhanced UserVault
class EnhancedUserVault : Vault<EnhancedUserVault>() {
    private val metricsMiddleware = MetricsMiddleware<EnhancedUserVault>()
    private val cleanupMiddleware = ResourceCleanupMiddleware<EnhancedUserVault>()
    private val recoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val currentUser by state(ComplexUserValidator()) {
        User("1", "Initial User", 25, "initial@example.com", setOf(Role.USER))
    }

    val auditLogs by state<List<AuditLog>> { emptyList() }

    val isLoggedIn by state { false }

    val loginAttempts by state { 0 }

    val errorState by state { ErrorState() }

    init {
        middlewares(
            LoggingMiddleware(
                Options(
                    logLevel = LogLevel.INFO,
                    includeStackTrace = true,
                    includeTimestamp = true
                )
            ),
            metricsMiddleware,
            cleanupMiddleware
        )

        currentUser repository InMemoryUserRepository()
        auditLogs repository AuditLogRepository()

        // Effects
        currentUser effect {
            println("🔔 User changed: $name")
            addAuditLog("User updated: $name")
        }

        isLoggedIn effect {
            println("🔐 Login status changed: $this")
            if (this) {
                startSessionMonitoring()
            }
        }
    }

    private fun startSessionMonitoring() = recoveryScope.launch {
        try {
            while (isLoggedIn.value) {
                delay(5000)
                validateSession()
            }
        } catch (e: Exception) {
            handleError("Session monitoring failed: ${e.message}")
        }
    }

    private suspend fun validateSession() {
        if (Random.nextInt(100) < 5) {
            handleError("Session validation failed")
            logout()
        }
    }

    private fun addAuditLog(action: String) {
        action {
            val newLog = AuditLog(action, Instant.now(), currentUser.value.id)
            auditLogs mutate auditLogs.value + newLog
        }
    }

    private fun handleError(error: String) {
        action {
            errorState mutate ErrorState(
                message = error,
                timestamp = Instant.now(),
                isActive = true
            )
        }
    }

    // Actions with parallel processing
    suspend fun bulkUserUpdate(updates: List<User>) = coroutineScope {
        updates.map { user ->
            async {
                action {
                    currentUser mutate user
                }
            }
        }.awaitAll()
    }

    // Action with resource management
    fun processUserData(resource: AutoCloseable, processor: (User) -> User) = action {
        cleanupMiddleware.registerResource(resource)
        try {
            val processedUser = processor(currentUser.value)
            currentUser mutate processedUser
        } catch (e: Exception) {
            handleError("Data processing failed: ${e.message}")
            throw e
        }
    }

    // Action with error recovery
    fun login(email: String) = action {
        try {
            loginAttempts mutate (loginAttempts.value + 1)

            currentUser mutate currentUser.value.copy(
                email = email
            )

            isLoggedIn mutate true
            errorState mutate ErrorState()
        } catch (e: Exception) {
            handleError("Login failed: ${e.message}")
            if (loginAttempts.value >= 3) {
                handleError("Account locked due to multiple failed attempts")
            }
            throw e
        }
    }

    fun logout() = action {
        isLoggedIn mutate false
        addAuditLog("User logged out")
    }

    // State recovery action
    fun recoverState() = action {
        if (errorState.value.isActive) {
            println("Attempting state recovery...")
            currentUser mutate currentUser.value.copy(
                roles = currentUser.value.roles + Role.GUEST
            )
            isLoggedIn mutate false
            loginAttempts mutate 0
            errorState mutate ErrorState()
            addAuditLog("State recovered")
        }
    }
}

// Demo usage with enhanced features
suspend fun main() {
    val userVault = EnhancedUserVault()

    // Test successful login
    println("\n📝 Testing successful login...")
    userVault.login("new@example.com")

    // Test parallel updates
    println("\n📝 Testing parallel user updates...")
    val updates = (1..3).map { i ->
        User(
            id = i.toString(),
            name = "User $i",
            age = 20 + i,
            email = "user$i@example.com",
            roles = setOf(Role.USER)
        )
    }
    userVault.bulkUserUpdate(updates)

    // Test resource management
    println("\n📝 Testing resource management...")
    val mockResource = AutoCloseable { println("Resource cleaned up") }
    try {
        userVault.processUserData(mockResource) { user ->
            user.copy(roles = user.roles + Role.ADMIN)
        }
    } catch (e: Exception) {
        println("Error during data processing: ${e.message}")
    }

    // Test error recovery
    println("\n📝 Testing error recovery...")
    try {
        userVault.login("invalid")
    } catch (e: Exception) {
        println("Expected error occurred: ${e.message}")
        userVault.recoverState()
    }

    userVault.logout()

    // Final state check
    println("\n📊 Final state:")
    println("User: ${userVault.currentUser.value}")
    println("Logged in: ${userVault.isLoggedIn.value}")
    println("Login attempts: ${userVault.loginAttempts.value}")
    println("Error state: ${userVault.errorState.value}")
    println("Audit logs: ${userVault.auditLogs.value}")
}