package com.vynatix

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AssetTest {
    private lateinit var asset: Asset<String>
    private lateinit var repository: TestRepository
    @BeforeTest
    fun setup() {
        asset = Asset({ "initial" })
        repository = TestRepository()
    }

    @Test
    fun testInitialValue() = runTest {
        assertEquals("initial", asset())
    }

    @Test
    fun testSet() = runTest {
        asset("updated")
        assertEquals("updated", asset())
    }

    @Test
    fun testRepository() = runTest {
        asset.repository(repository)
        asset("test")
        delay(100)
        assertEquals("test", repository.lastSetValue)
        repository.emit("fromRepo")
        delay(800)
        assertEquals("fromRepo", asset())
    }

    @Test
    fun testErrorPropagation(): Unit = runTest {
        val errorRepo = object : TestRepository() {
            override fun set(value: String) {
                throw RuntimeException("Test error")
            }
        }

        asset.repository(errorRepo)
        assertFailsWith<RuntimeException> {
            asset("error")
        }
    }
}
open class TestRepository : IRepository<String> {
    private val _flow = MutableSharedFlow<String>(replay = 1)
    var lastSetValue: String? = null
        private set


    override fun set(value: String) {
        lastSetValue = value
        _flow.tryEmit(value)
    }

    suspend fun emit(value: String) {
        _flow.emit(value)
    }

    override fun flow(): SharedFlow<String> =_flow.asSharedFlow()
}