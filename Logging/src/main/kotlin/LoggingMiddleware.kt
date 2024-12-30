package com.vynatix

import com.vynatix.Middleware.MiddlewareContext
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class Options(
    val logLevel: LogLevel = LogLevel.INFO,
    val includeStackTrace: Boolean = false,
    val includeTimestamp: Boolean = true,
    val includeStateValues: Boolean = true,
    val formatter: LogFormatter = DefaultLogFormatter()
)

enum class LogLevel {
    DEBUG, INFO, WARNING, ERROR
}

class DefaultLogFormatter : LogFormatter {
    override fun formatStart(context: MiddlewareContext<*>): String {
        return buildString {
            append("🔵 Transaction started: ${context.transaction.id}")
            if (context.metadata.isNotEmpty()) {
                append(" [Metadata: ${context.metadata}]")
            }
        }
    }

    override fun formatComplete(context: MiddlewareContext<*>, durationMs: Long): String {
        return buildString {
            appendLine("✅ Transaction completed: ${context.transaction.id}")
            append("   Duration: ${durationMs}ms")
            appendLine("\n   Current state:")
            context.vault.properties.forEach { (name, state) ->
                appendLine("   - $name: ${formatValue(state.value)}")
            }
        }
    }

    override fun formatError(context: MiddlewareContext<*>, error: Throwable, durationMs: Long): String {
        return buildString {
            appendLine("❌ Transaction failed: ${context.transaction.id}")
            appendLine("   Duration: ${durationMs}ms")
            appendLine("   Error: ${error.message}")
            appendLine("   Current state:")
            context.vault.properties.forEach { (name, state) ->
                appendLine("   - $name: ${formatValue(state.value)}")
            }
        }
    }

    override fun formatStateChange(context: MiddlewareContext<*>, state: State<*>, oldValue: Any, newValue: Any): String {
        return buildString {
            append("📝 State changed: ")
            append(context.vault.properties.entries.find { it.value == state }?.key ?: "unknown")
            append(" [${formatValue(oldValue)} -> ${formatValue(newValue)}]")
        }
    }
}

interface LogFormatter {
    fun formatStart(context: MiddlewareContext<*>): String
    fun formatComplete(context: MiddlewareContext<*>, durationMs: Long): String
    fun formatError(context: MiddlewareContext<*>, error: Throwable, durationMs: Long): String
    fun formatStateChange(context: MiddlewareContext<*>, state: State<*>, oldValue: Any, newValue: Any): String

    fun formatValue(value: Any?): String = when (value) {
        null -> "null"
        is String -> "\"$value\""
        is Number -> value.toString()
        is Boolean -> value.toString()
        is Collection<*> -> value.joinToString(prefix = "[", postfix = "]")
        is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}")
        else -> value.toString()
    }
}
class LoggingMiddleware<T : Vault<T>>(
    private val options: Options = Options()
) : Middleware<T>() {
    // Track active transactions to prevent duplicate logging
    private val activeTransactions = Collections.synchronizedSet(mutableSetOf<String>())

    // Use concurrent map for thread-safe state snapshots
    private val transactionSnapshots = ConcurrentHashMap<String, Map<String, Any?>>()

    override fun onTransactionStarted(context: MiddlewareContext<T>) {
        // Only log if this is the first time we're seeing this transaction
        if (!activeTransactions.add(context.transaction.id)) {
            return
        }

        val timestamp = System.currentTimeMillis()

        // Take a snapshot of the initial state
        val initialState = context.vault.properties.mapValues { (_, state) ->
            try {
                state.value
            } catch (e: Exception) {
                "Unable to capture: ${e.message}"
            }
        }

        transactionSnapshots[context.transaction.id] = initialState

        log(options.formatter.formatStart(context))

        // Log initial state with proper formatting
        initialState.forEach { (name, value) ->
            log("Initial state - $name: ${formatValue(value)}")
        }

        // Store timestamp for duration calculation
        context.metadata["startTime"] = timestamp
    }

    override fun onTransactionCompleted(context: MiddlewareContext<T>) {
        if (!activeTransactions.remove(context.transaction.id)) {
            return
        }

        val startTime = context.metadata["startTime"] as? Long ?: return
        val duration = System.currentTimeMillis() - startTime

        val currentState = context.vault.properties.mapValues { it.value.value }

        context.transaction.modifiedProperties.forEach { state ->
            val propertyName = context.vault.properties.entries.find { it.value == state }?.key
            if (propertyName != null) {
                val oldValue = context.transaction.previousValues[state]
                val newValue = currentState[propertyName]

                if (oldValue != newValue) {
                    log("State change - $propertyName:")
                    log("  From: ${formatValue(oldValue)}")
                    log("    To: ${formatValue(newValue)}")
                }
            }
        }

        log(options.formatter.formatComplete(context, duration))
    }

    override fun onTransactionError(context: MiddlewareContext<T>, error: Throwable) {
        activeTransactions.remove(context.transaction.id)
        transactionSnapshots.remove(context.transaction.id)

        val startTime = context.metadata["startTime"] as? Long ?: return
        val duration = System.currentTimeMillis() - startTime

        log(options.formatter.formatError(context, error, duration))

        if (options.includeStackTrace) {
            log(error.stackTraceToString())
        }
    }

    private fun formatValue(value: Any?): String = options.formatter.formatValue(value)

    private fun log(message: String) {
        val timestamp = if (options.includeTimestamp) {
            "[${java.time.LocalDateTime.now()}] "
        } else {
            ""
        }

        when (options.logLevel) {
            LogLevel.DEBUG -> println("${timestamp}DEBUG: $message")
            LogLevel.INFO -> println("${timestamp}INFO: $message")
            LogLevel.WARNING -> System.err.println("${timestamp}WARN: $message")
            LogLevel.ERROR -> System.err.println("${timestamp}ERROR: $message")
        }
    }
}