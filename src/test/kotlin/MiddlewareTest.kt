package com.vynatix

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class MiddlewareTest {
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
    private val executionOrder = mutableListOf<String>()

    @BeforeTest
    fun setup() {

        executionOrder.clear()
    }

    @Test
    fun testMiddlewareOrder() = runBlocking {
        val firstMiddleware = createTestMiddleware("first")
        val secondMiddleware = createTestMiddleware("second")

        userVault.middlewares(firstMiddleware, secondMiddleware)
        loginUseCase()

        assertEquals(
            listOf("first_start", "second_start", "second_complete", "first_complete"),
            executionOrder,
            "Middleware execution order incorrect")
    }

    @Test
    fun testMiddlewareError() = runBlocking {
        val errorMiddleware = object : Middleware<IVault>() {
            override suspend fun onTransactionStarted(context: MiddlewareContext<IVault>) {
                executionOrder.add("error_start")
                throw RuntimeException("Test error")
            }

            override suspend fun onTransactionError(context: MiddlewareContext<IVault>, error: Throwable) {
                executionOrder.add("error_handled")
            }
        }

        userVault.middlewares(errorMiddleware)

        assertFailsWith<RuntimeException>("Middleware should propagate errors") {
            userVault.operation("test") { userVault.username("test") }
        }

        assertEquals(
            listOf("error_start", "error_handled"),
            executionOrder,
            "Error handling sequence incorrect",)
    }

    @Test
    fun testMiddlewareContext() = runBlocking {
        var capturedContext: Middleware.MiddlewareContext<IVault>? = null

        val contextTestMiddleware = object : Middleware<IVault>() {
            override suspend fun onTransactionStarted(context: MiddlewareContext<IVault>) {
                capturedContext = context
            }
        }

        userVault.middlewares(contextTestMiddleware)
        userVault.operation("contextTest") { userVault.username("test") }

        assertNotNull( capturedContext,"Middleware context should not be null")
        assertEquals(
            "contextTest",
            capturedContext?.transaction?.useCaseId,
            "Context should have correct operation ID")
    }

    @Test
    fun testMiddlewareMetadata(): Unit = runBlocking {
        val metadataMiddleware = object : Middleware<IVault>() {
            override suspend fun onTransactionStarted(context: MiddlewareContext<IVault>) {
                context.metadata["test"] = "value"
            }

            override suspend fun onTransactionCompleted(context: MiddlewareContext<IVault>) {
                assertEquals(
                    "value",
                    context.metadata["test"],
                    "Metadata should persist")
            }
        }

        userVault.middlewares(metadataMiddleware)
        userVault.operation("test") { userVault.username("test") }
    }

    private fun createTestMiddleware(name: String) = object : Middleware<IVault>() {
        override suspend fun onTransactionStarted(context: MiddlewareContext<IVault>) {
            executionOrder.add("${name}_start")
        }

        override suspend fun onTransactionCompleted(context: MiddlewareContext<IVault>) {
            executionOrder.add("${name}_complete")
        }
    }
}