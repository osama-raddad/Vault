import com.vynatix.*
import kotlin.random.Random
import kotlin.system.measureTimeMillis

class TestVault : Vault<TestVault>() {
    val counter by state { 0 }
    val text by state { "" }
    val list by state { listOf<Int>() }
    val flag by state { false }
    val map by state { mapOf<String, Int>() }
    val nestedList by state { listOf<List<Int>>() }
}

class ConflictMiddleware : Middleware<TestVault>() {
    override fun onTransactionStarted(context: MiddlewareContext<TestVault>) {
        if (Random.nextInt(500) == 0) {
            Thread.sleep(Random.nextLong(10)) // Introduce timing variations
            throw RuntimeException("Simulated conflict")
        }
    }
}

class TestMiddleware : Middleware<TestVault>() {
    var transactionCount = 0
    var errorCount = 0
    var rollbackCount = 0

    override fun onTransactionStarted(context: MiddlewareContext<TestVault>) {
        transactionCount++
        if (Random.nextInt(1000) == 0) { // Occasional random failure
            throw RuntimeException("Random middleware failure")
        }
    }

    override fun onTransactionError(context: MiddlewareContext<TestVault>, error: Throwable) {
        errorCount++
        if (error is RuntimeException) {
            rollbackCount++
        }
    }
}

class StressTestRunner(
    private val iterations: Int = 100000,
    private val concurrentVaults: Int = 50
) {
    private val vaults = List(concurrentVaults) { TestVault() }
    private val middleware = TestMiddleware()
    private var successCount = 0
    private var failureCount = 0
    private var rollbackCount = 0

    fun runTests() {
        println("Starting stress test with:")
        println("- $iterations iterations per vault")
        println("- $concurrentVaults concurrent vaults")

        val totalTime = measureTimeMillis {
            vaults.forEach { vault ->
                vault.middlewares(
                    middleware,
                    ConflictMiddleware()
                )
            }

            val jobs = vaults.map { vault ->
                Thread {
                    repeat(iterations) {
                        performComplexOperation(vault)
                        performSimpleOperation(vault)
                    }
                }.apply { start() }
            }

            jobs.forEach { it.join() }
        }

        printResults(totalTime)
    }

    private fun performComplexOperation(vault: TestVault) {
        when (Random.nextInt(6)) {
            0 -> chainedOperations(vault)
            1 -> nestedListOperation(vault)
            2 -> mapOperation(vault)
            3 -> multiStateUpdate(vault)
            4 -> conditionalUpdate(vault)
            5 -> largeListOperation(vault)
        }
    }

    private fun performSimpleOperation(vault: TestVault) {
        when (Random.nextInt(4)) {
            0 -> incrementCounter(vault)
            1 -> updateText(vault)
            2 -> modifyList(vault)
            3 -> toggleFlag(vault)
        }
    }

    private fun chainedOperations(vault: TestVault) {
        val result = vault action {
            counter mutate (counter.value + 1)
            text mutate "Updated ${counter.value}"
            list mutate list.value + counter.value
        }
        recordResult(result)
    }

    private fun nestedListOperation(vault: TestVault) {
        val result = vault action {
            val current = nestedList.value
            val newSubList = List(10) { Random.nextInt(100) }
            nestedList mutate current + listOf(newSubList)
            if (current.size > 10) {
                nestedList mutate current.drop(1)
            }
        }
        recordResult(result)
    }

    private fun mapOperation(vault: TestVault) {
        val result = vault action {
            val current = map.value
            val key = "key_${Random.nextInt(1000)}"
            map mutate current + (key to Random.nextInt())
            if (current.size > 100) {
                map mutate current.filter { Random.nextBoolean() }
            }
        }
        recordResult(result)
    }

    private fun multiStateUpdate(vault: TestVault) {
        val result = vault action {
            counter mutate (counter.value + 1)
            text mutate "Multi ${counter.value}"
            list mutate list.value + counter.value
            flag mutate !flag.value
            map mutate map.value + ("counter" to counter.value)
        }
        recordResult(result)
    }

    private fun conditionalUpdate(vault: TestVault) {
        val result = vault action {
            if (flag.value) {
                counter mutate (counter.value * 2)
            } else {
                counter mutate (counter.value + 1)
            }
            flag mutate !flag.value
        }
        recordResult(result)
    }

    private fun largeListOperation(vault: TestVault) {
        val result = vault action {
            val newItems = List(100) { Random.nextInt() }
            list mutate (list.value + newItems).takeLast(1000)
        }
        recordResult(result)
    }

    // Simple operations remain the same
    private fun incrementCounter(vault: TestVault) {
        val result = vault action {
            counter mutate (counter.value + 1)
        }
        recordResult(result)
    }

    private fun updateText(vault: TestVault) {
        val result = vault action {
            text mutate "Updated ${Random.nextInt()}"
        }
        recordResult(result)
    }

    private fun modifyList(vault: TestVault) {
        val result = vault action {
            list mutate list.value + Random.nextInt()
        }
        recordResult(result)
    }

    private fun toggleFlag(vault: TestVault) {
        val result = vault action {
            flag mutate !flag.value
        }
        recordResult(result)
    }

    private fun recordResult(result: TransactionResult) {
        when (result) {
            is TransactionResult.Success -> successCount++
            is TransactionResult.Error -> {
                failureCount++
                rollbackCount++ // Count all errors as rollbacks
            }
        }
    }

    private fun printResults(totalTime: Long) {
        println("\nTest Results:")
        println("Total time: ${totalTime}ms")
        println("Successful transactions: $successCount")
        println("Failed transactions: $failureCount")
        println("Rollbacks: $rollbackCount")
        println("Middleware transactions: ${middleware.transactionCount}")
        println("Middleware errors: ${middleware.errorCount}")
        println("Middleware rollbacks: ${middleware.rollbackCount}")
        println("Average time per transaction: %.4f ms".format(totalTime.toDouble() / (successCount + failureCount)))
        println("Transaction success rate: %.2f%%".format(successCount.toDouble() / (successCount + failureCount) * 100))
        println("Memory usage: ${Runtime.getRuntime().totalMemory() / 1024 / 1024}MB")
    }
}

fun main() {
    val stressTest = StressTestRunner(
        iterations = 1_000_000,
        concurrentVaults = 500
    )
    stressTest.runTests()
}