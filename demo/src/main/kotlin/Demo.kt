package com.vynatix

import kotlinx.coroutines.flow.*

// Validators
class EmailValidator : Transformer<String> {
    override fun set(value: String): String {
        require(value.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\$"))) {
            "Invalid email format: $value"
        }
        return value
    }

    override fun get(value: String): String {
        return value
    }
}

class UsernameValidator : Transformer<String> {
    override fun set(value: String): String = value.apply {
        require(length in 3..50) {
            "invalid username: $value, Username must be between 3 and 50 characters"
        }
    }

    override fun get(value: String): String = value
}

class UserBridge : Bridge<String> {
    private val _dataFlow = MutableStateFlow("none")
    private val transmitters = mutableSetOf<(String) -> Unit>()
    private val userHistory = mutableListOf<String>()

    override fun observe(observer: (String) -> Unit): Disposable {
        transmitters.add(observer)
        observer(_dataFlow.value)
        return Disposable { transmitters.remove(observer) }
    }

    override fun publish(value: String): Boolean {
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
class LoginAction(private val vault: UserVault) {
    operator fun invoke(username: String, email: String) = vault action {
        loginAttempts mutate loginAttempts.value + 1
        this.username mutate username
        this.email mutate email
        isLoggedIn mutate true
    }
}

class LogoutAction {
    operator fun invoke(): UserVault.() -> Unit = {
        isLoggedIn mutate false
    }
}

class UserNameEffect : Effect<String> {
    override fun invoke(value: String) {
        println("👤 Username updated: $value")
    }
}

val  userVault = UserVault()
fun main() = userVault {
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

    username bridge UserBridge()
    username effect UserNameEffect()
    email effect { println("📧 Email updated: $this") }
    loginAttempts effect { println("🔑 Login attempts: $this") }
    isLoggedIn effect {
        println("🔒 Login status: $this")
        if (!this) {
            action { username mutate "guest" }
        }
    }


    println("\n🚀 Testing login flow...")
    LoginAction(this)("john_doe", "john@example.com")



    println("\n🚪 Testing logout flow...")
    this action LogoutAction()()

    println("\n❌ Testing validation failure...")
    try {
        LoginAction(this).invoke("x", "invalid-email")
    } catch (e: Exception) {
        println("Expected validation error: ${e.message}")
    }

    println("\n📊 Final state:")

    println("Username: ${username.value}")
    println("Email: ${email.value}")
    println("Login attempts: ${loginAttempts.value}")
    println("Is logged in: ${isLoggedIn.value}")
}