package com.vynatix

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis


class TestVault : Vault<TestVault>() {
    val counter by state(
        initialize = { 0 }
    )


    val successfulOps = AtomicInteger(0)
    val failedOps = AtomicInteger(0)
    val totalOpTime = AtomicInteger(0)
}

class VaultStressTest {
    companion object {
        const val NUM_COROUTINES = 100
        const val OPERATIONS_PER_COROUTINE = 1000
        const val OPERATION_DELAY_MS = 1L
        const val RETRY_ATTEMPTS = 3
    }

    private val vault = TestVault()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private suspend fun simulateOperation() {
        delay(OPERATION_DELAY_MS)
    }

    // Modified to handle suspend functions properly
    private suspend fun performIncrementWithRetry(
        increment: suspend () -> Unit
    ): Boolean {
        repeat(RETRY_ATTEMPTS) { attempt ->
            try {
                val operationTime = measureTimeMillis {
                    increment()
                }
                vault.successfulOps.incrementAndGet()
                vault.totalOpTime.addAndGet(operationTime.toInt())
                return true
            } catch (e: Exception) {
                if (attempt == RETRY_ATTEMPTS - 1) {
                    vault.failedOps.incrementAndGet()
                    return false
                }
                delay(10L * (attempt + 1)) // Exponential backoff
            }
        }
        return false
    }

    private suspend fun runIsolationTest(

        incrementOperation: suspend () -> Unit
    ) {
        val startTime = System.currentTimeMillis()

        withContext(Dispatchers.Default) {
            val jobs = List(NUM_COROUTINES) {
                launch {
                    repeat(OPERATIONS_PER_COROUTINE) {
                        // Combine operations into a single suspend lambda
                        performIncrementWithRetry {
                            simulateOperation()
                            incrementOperation()
                        }
                    }
                }
            }
            jobs.joinAll()
        }

        val duration = System.currentTimeMillis() - startTime
        val expectedTotal = NUM_COROUTINES * OPERATIONS_PER_COROUTINE
        val actualTotal = vault.counter.value

        println("\n=== Results ===")
        println("Duration: ${duration}ms")
        println("Expected Total: $expectedTotal")
        println("Actual Total: $actualTotal")
        println("Successful Operations: ${vault.successfulOps.get()}")
        println("Failed Operations: ${vault.failedOps.get()}")
        println("Average Operation Time: ${vault.totalOpTime.get() / kotlin.math.max(1, vault.successfulOps.get())}ms")
        println("Throughput: ${(vault.successfulOps.get() / kotlin.math.max(1.0, duration / 1000.0)).toInt()} ops/sec")
    }

    suspend fun runStressTest() {
        println("Starting Vault Stress Test...")
        println("Configuration:")
        println("- Concurrent Coroutines: $NUM_COROUTINES")
        println("- Operations per Coroutine: $OPERATIONS_PER_COROUTINE")
        println("- Operation Delay: $OPERATION_DELAY_MS ms")
        println("- Retry Attempts: $RETRY_ATTEMPTS")

        // Test optimistic locking
        vault.successfulOps.set(0)
        vault.failedOps.set(0)
        vault.totalOpTime.set(0)
        runIsolationTest() {
            vault action {
                counter mutate (counter.value + 1)
            }
        }

        scope.cancel()
    }
}


// Run the stress test
suspend fun main() {
    val stressTest = VaultStressTest()
    stressTest.runStressTest()
}