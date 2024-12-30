package com.vynatix

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// Validators
class EmailValidator : StateValidator<String> {
    override fun validate(value: String): Boolean =
        value.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\$"))

    override fun getValidationError(value: String): String =
        "Invalid email format: $value"
}

class UsernameValidator : StateValidator<String> {
    override fun validate(value: String): Boolean = value.length in 3..50
    override fun getValidationError(value: String): String =
        "Username must be between 3 and 50 characters"
}

// Enhanced Repositories
class UserRepository : Repository<String> {
    private val _dataFlow = MutableStateFlow("none")
    private val transmitters = mutableSetOf<(String) -> Unit>()
    private val userHistory = mutableListOf<String>()

    override fun transmitted(observer: (String) -> Unit): Boolean {
        transmitters.add(observer)
        observer(_dataFlow.value)
        return true
    }

    override fun received(value: String): Boolean {
        userHistory.add(value)
        _dataFlow.value = value
        transmitters.forEach { it(value) }
        return true
    }

}

// Analytics Middleware
class AnalyticsMiddleware<T : Vault<T>> : Middleware<T>() {
    private val metrics = mutableMapOf<String, Int>()

    override fun onTransactionStarted(context: MiddlewareContext<T>) {
        val action = context.transaction.id
        metrics[action] = (metrics[action] ?: 0) + 1
        println("📊 Action started: $action (count: ${metrics[action]})")
    }
}

// Enhanced UserVault
class UserVault : Vault<UserVault>() {
    val username by state(UsernameValidator()) { "guest" }
    val email by state(EmailValidator()) { "guest@example.com" }
    val loginAttempts by state { 0 }
    val isLoggedIn by state { false }
}

// Enhanced Actions
class LoginAction(private val username: String, private val email: String) : Action<UserVault> {
    override fun invoke(vault: UserVault) = vault {
        loginAttempts mutate loginAttempts.value + 1
        this.username mutate this@LoginAction.username
        this.email mutate this@LoginAction.email
        isLoggedIn mutate true
    }
}

class LogoutAction : Action<UserVault> {
    override fun invoke(vault: UserVault) = vault {
        isLoggedIn mutate false
    }
}
private fun UserVault.setupRepositories() {
    username repository UserRepository()
}

private fun UserVault.setupEffects() {
    username effect { println("👤 Username updated: $this") }
    email effect { println("📧 Email updated: $this") }
    loginAttempts effect { println("🔑 Login attempts: $this") }
    isLoggedIn effect {
        println("🔒 Login status: $this")
        if (!this) {
            action { username mutate "guest" }
        }
    }
}
fun main() = runBlocking {
    val userVault = UserVault()

    userVault {
        middlewares(
            LoggingMiddleware(
                Options(
                    logLevel = LogLevel.INFO,
                    includeStackTrace = true,
                    includeStateValues = true
                )
            ),
            AnalyticsMiddleware()
        )

        setupRepositories()
        setupEffects()
    }

    println("\n🚀 Testing login flow...")
    userVault action LoginAction("john_doe", "john@example.com")

    delay(1000)

    println("\n🚪 Testing logout flow...")
    userVault action LogoutAction()

    println("\n❌ Testing validation failure...")
    try {
        userVault action LoginAction("x", "invalid-email")
    } catch (e: Exception) {
        println("Expected validation error: ${e.message}")
    }

    delay(100)
    println("\n📊 Final state:")
    with(userVault) {
        println("Username: ${username.value}")
        println("Email: ${email.value}")
        println("Login attempts: ${loginAttempts.value}")
        println("Is logged in: ${isLoggedIn.value}")
    }
}