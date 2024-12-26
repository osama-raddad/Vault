package com.vynatix

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class ObjectPoolTest {
    @Test
    fun `test pool creates new item when empty`() = runBlocking {
        var factoryCallCount = 0
        val pool = ObjectPool(
            factory = {
                factoryCallCount++
                "item$factoryCallCount"
            },
            reset = { }
        )

        val item1 = pool.acquire()
        val item2 = pool.acquire()

        assertEquals("item1", item1)
        assertEquals("item2", item2)
        assertEquals(2, factoryCallCount)
    }

    @Test
    fun `test pool reuses released items`() = runBlocking {
        var factoryCallCount = 0
        val pool = ObjectPool(
            factory = {
                factoryCallCount++
                "item$factoryCallCount"
            },
            reset = { }
        )

        val item1 = pool.acquire()
        pool.release(item1)
        val item2 = pool.acquire()

        assertEquals(item1, item2)
        assertEquals(1, factoryCallCount)
    }

    @Test
    fun `test pool resets items on release`() = runBlocking {
        var resetCallCount = 0
        val pool = ObjectPool(
            factory = { mutableListOf<String>() },
            reset = {
                it.clear()
                resetCallCount++
            }
        )

        val item = pool.acquire()
        item.add("test")
        pool.release(item)

        assertEquals(1, resetCallCount)
        assertTrue(item.isEmpty())
    }

    @Test
    fun `test concurrent access`() = runBlocking {
        val pool = ObjectPool(
            factory = { "new_item" },
            reset = { }
        )

        coroutineScope {
            val results = List(5) {
                async {
                    val item = pool.acquire()
                    pool.release(item)
                    true
                }
            }
            results.forEach { it.await() }
        }
    }
}

class OperationPoolTest {
    @Test
    fun `test operation pool acquire sets useCase id`() = runBlocking {
        val operation = OperationPool.acquire("test_case")
        assertEquals("test_case", operation.useCaseId)
    }

    @Test
    fun `test operation pool clears state on release`() = runBlocking {
        val operation = OperationPool.acquire("test_case")
        val asset = Asset({ "test" })

        operation.modifiedProperties.add(asset)
        operation.previousValues[asset] = "old_value"

        OperationPool.release(operation)

        assertTrue(operation.modifiedProperties.isEmpty())
        assertTrue(operation.previousValues.isEmpty())
    }

    @Test
    fun `test operation pool reuses operations`() = runBlocking {
        val op1 = OperationPool.acquire("case1")
        OperationPool.release(op1)
        val op2 = OperationPool.acquire("case2")

        assertSame(op1, op2)
        assertEquals("case2", op2.useCaseId)
    }
}

class AssetFactoryTest {
    @Test
    fun `test asset factory creates new asset with initial value`() {
        val factory = AssetFactory()
        val asset = factory { "test_value" }

        assertEquals("test_value", asset())
    }
}

class VaultFactoryTest {
    @Test
    fun `test vault factory creates new vault`() {
        val factory = VaultFactory()
        val vault = factory()

        assertNotNull(vault)
        assertTrue(vault.properties.isEmpty())
    }

    @Test
    fun `test vault factory creates vault with custom asset factory`() {
        val assetFactory = AssetFactory()
        val factory = VaultFactory(assetFactory)
        val vault = factory()

        assertNotNull(vault)
    }
}