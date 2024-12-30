package com.vynatix

class LoggingMiddleware<T : Vault<T>>(
    private val options: Options = Options()
) : Middleware<T>() {
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
                    appendLine("   - $name: ${formatValue(state.invoke())}")
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
                    appendLine("   - $name: ${formatValue(state.invoke())}")
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

    private var startTime: Long = 0
    private val stateSnapshots = mutableMapOf<State<*>, Any?>()

    override fun onTransactionStarted(context: MiddlewareContext<T>) {
        startTime = System.currentTimeMillis()
        log(options.formatter.formatStart(context))

        // Log and capture initial state
        context.vault.properties.forEach { (name, state) ->
            try {
                val value = state.invoke()
                log("Initial state - $name: ${formatValue(value)}")
                stateSnapshots[state] = value
            } catch (e: Exception) {
                log("Failed to capture initial state for $name: ${e.message}")
            }
        }
    }

    override fun onTransactionCompleted(context: MiddlewareContext<T>) {
        val duration = System.currentTimeMillis() - startTime

        // Log mutations that were attempted
        context.transaction.modifiedProperties.forEach { state ->
            val propertyName = context.vault.properties.entries.find { it.value == state }?.key ?: "unknown"
            log("Mutation attempted - $propertyName:")
            log("  Previous value: ${formatValue(context.transaction.previousValues[state])}")
            log("  Current value: ${formatValue(state.invoke())}")
        }

        log(options.formatter.formatComplete(context, duration))
        stateSnapshots.clear()
    }

    override fun onTransactionError(context: MiddlewareContext<T>, error: Throwable) {
        val duration = System.currentTimeMillis() - startTime
        log(options.formatter.formatError(context, error, duration))

        if (options.includeStackTrace) {
            log(error.stackTraceToString())
        }
        stateSnapshots.clear()
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