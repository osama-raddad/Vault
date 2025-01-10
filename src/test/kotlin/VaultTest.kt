package com.vynatix
import kotlin.test.*
import kotlin.test.Test

// Test data classes
data class TestData(
    val id: String,
    val value: Int
)

data class ComplexData(
    val name: String,
    val items: List<String>,
    val metadata: Map<String, Any>
)

// Test transformer
class TestTransformer : Transformer<TestData> {
    override fun set(value: TestData): TestData =
        value.copy(value = value.value * 2)

    override fun get(value: TestData): TestData =
        value.copy(value = value.value / 2)
}

// Test vault implementation
class TestVault : Vault<TestVault>() {
    val simpleState by state { "initial" }
    val numberState by state { 0 }
    val dataState by state(TestTransformer()) { TestData("test", 1) }
    val listState by state { listOf<String>() }
    val complexState by state { ComplexData("test", emptyList(), emptyMap()) }
}

// Test middleware
class TestMiddleware : Middleware<TestVault>() {
    var transactionStartCount = 0
    var transactionCompleteCount = 0
    var transactionErrorCount = 0

    override fun onTransactionStarted(context: MiddlewareContext<TestVault>) {
        transactionStartCount++
    }

    override fun onTransactionCompleted(context: MiddlewareContext<TestVault>) {
        transactionCompleteCount++
    }

    override fun onTransactionError(context: MiddlewareContext<TestVault>, error: Throwable) {
        transactionErrorCount++
    }
}

class VaultTest {
    @Test
    fun `test basic state operations`() {
        val vault = TestVault()

        assertEquals("initial", vault.simpleState.value)
        assertEquals(0, vault.numberState.value)

        vault action {
            simpleState mutate "updated"
            numberState mutate 42
        }

        assertEquals("updated", vault.simpleState.value)
        assertEquals(42, vault.numberState.value)
    }

    @Test
    fun `test transformer behavior`() {
        val vault = TestVault()

        // Initial value should be transformed on get
        assertEquals(TestData("test", 0), vault.dataState.value) // value divided by 2

        vault action {
            dataState mutate TestData("modified", 10)
        }

        // Value should be transformed on set (multiplied by 2) and get (divided by 2)
        assertEquals(TestData("modified", 10), vault.dataState.value)
    }

    @Test
    fun `test middleware execution`() {
        val vault = TestVault()
        val middleware = TestMiddleware()
        vault.middlewares(middleware)

        // Successful transaction
        vault action {
            simpleState mutate "success"
        }

        assertEquals(1, middleware.transactionStartCount)
        assertEquals(1, middleware.transactionCompleteCount)
        assertEquals(0, middleware.transactionErrorCount)

        // Failed transaction
        try {
            vault action {
                throw RuntimeException("Test error")
            }
        } catch (e: RuntimeException) {
            // Expected
        }

        assertEquals(2, middleware.transactionStartCount)
        assertEquals(1, middleware.transactionCompleteCount)
        assertEquals(1, middleware.transactionErrorCount)
    }

    @Test
    fun `test transaction rollback`() {
        val vault = TestVault()
        val initialValue = vault.simpleState.value

        val result = vault action {
            simpleState mutate "will be rolled back"
            throw RuntimeException("Trigger rollback")
        }

        assertTrue(result is TransactionResult.Error)
        assertEquals(initialValue, vault.simpleState.value)
    }

    @Test
    fun `test state effects`() {
        val vault = TestVault()
        var effectTriggerCount = 0

        val disposable = vault {
            simpleState effect {
                effectTriggerCount++
            }
        }

        assertEquals(1, effectTriggerCount) // Initial value triggers effect once

        vault action {
            simpleState mutate "trigger effect"
        }

        assertEquals(2, effectTriggerCount)

        // Clean up
        disposable.dispose()
    }

    @Test
    fun `test bridge pattern`() {
        val vault = TestVault()
        var bridgeValue: String? = null

        val bridge = object : Bridge<String> {
            private val observers = mutableListOf<(String) -> Unit>()

            override fun observe(observer: (String) -> Unit): Disposable {
                observers.add(observer)
                return Disposable { observers.remove(observer) }
            }

            override fun publish(value: String): Boolean {
                bridgeValue = value
                observers.forEach { it(value) }
                return true
            }
        }

        vault {
            simpleState bridge bridge
        }

        vault action {
            simpleState mutate "bridge test"
        }

        assertEquals("bridge test", bridgeValue)
    }

    @Test
    fun `test concurrent modifications`() {
        val vault = TestVault()
        val iterations = 1000
        val threads = 10

        val jobs = List(threads) {
            Thread {
                repeat(iterations) { i ->
                    vault action {
                        numberState mutate (numberState.value + 1)
                    }
                }
            }
        }

        jobs.forEach { it.start() }
        jobs.forEach { it.join() }

        assertEquals(threads * iterations, vault.numberState.value)
    }

    @Test
    fun `test complex state mutations`() {
        val vault = TestVault()

        vault action {
            complexState mutate ComplexData(
                name = "updated",
                items = listOf("item1", "item2"),
                metadata = mapOf("key" to "value")
            )
        }

        assertEquals("updated", vault.complexState.value.name)
        assertEquals(2, vault.complexState.value.items.size)
        assertEquals("value", vault.complexState.value.metadata["key"])
    }

    @Test
    fun `test transaction isolation`() {
        val vault = TestVault()
        val outsideValue = vault.numberState.value

        vault action {
            numberState mutate 100
            assertEquals(100, numberState.value)
            assertEquals(outsideValue, 0) // Outside value shouldn't change until commit
        }

        assertEquals(100, vault.numberState.value) // After commit
    }
}