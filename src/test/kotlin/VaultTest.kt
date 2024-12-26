package com.vynatix

import kotlinx.coroutines.runBlocking
import kotlin.test.*
import kotlin.test.DefaultAsserter.assertEquals


class VaultTest {
    private var userVault = object : IVault by VaultFactory()() {
        val username by asset { "John Doe" }
        val email by asset { "none" }
        val loginAttempts by asset { 1 }
        val isLoggedIn by asset { false }
    }
    private val loginUseCase get() = suspend { userVault.operation("login") {
        userVault.username("Osama Raddad")
        userVault.email("jane@example.com")
        userVault.loginAttempts(1)
        userVault.isLoggedIn(true)
    } }

    @BeforeTest
    fun setup() {
        userVault.apply {
            middlewares(LoggingMiddleware)
        }
    }

    @Test
    fun testInitialState() = runBlocking {
        assertEquals("Initial username should be John Doe", "John Doe", userVault.username())
        assertEquals("Initial email should be none", "none", userVault.email())
        assertEquals("Initial login attempts should be 1", 1, userVault.loginAttempts())
        assertFalse("User should not be logged in initially") {userVault.isLoggedIn()}
    }

    @Test
    fun testLoginOperation() = runBlocking {
        loginUseCase()

        assertEquals("Username should update after login", "Osama Raddad", userVault.username())
        assertEquals("Email should update after login", "jane@example.com", userVault.email())
        assertEquals("Login attempts should be tracked", 1, userVault.loginAttempts())
        assertTrue("User should be logged in after login operation"){ userVault.isLoggedIn()}
    }

    @Test
    fun testMiddleware() = runBlocking {
        var middlewareExecuted = false

        val testMiddleware = object : Middleware<IVault>() {
            override suspend fun onTransactionStarted(context: MiddlewareContext<IVault>) {
                middlewareExecuted = true
            }
        }

        userVault.middlewares(testMiddleware)
        loginUseCase()

        assertTrue(middlewareExecuted)
    }

    @Test
    fun testErrorHandling(): Unit = runBlocking {
        val errorVault = UserVault(VaultFactory())

        val errorMiddleware = object : Middleware<IVault>() {
            override suspend fun onTransactionStarted(context: MiddlewareContext<IVault>) {
                throw RuntimeException("Test error")
            }
        }

        errorVault.middlewares(errorMiddleware)

        assertFailsWith<RuntimeException> {
            errorVault.operation("test") {
                errorVault.username("test")
            }
        }
    }

    @Test
    fun testPropertyRollback(): Unit = runBlocking {
        val initialUsername = userVault.username()

        try {
            userVault.operation("failedOperation") {
                userVault.username("newName")
                throw RuntimeException("Test error")
            }
        } catch (e: RuntimeException) {
            assertEquals("Username should rollback after error", initialUsername, userVault.username())
        }
    }
}
