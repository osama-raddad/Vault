package com.vynatix

///////////// example
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
        fun formatComplete(context: MiddlewareContext<*>, durationMs: Long, stateDiff: Map<State<*>, StateDiff>): String
        fun formatError(
            context: MiddlewareContext<*>,
            error: Throwable,
            durationMs: Long,
            stateDiff: Map<State<*>, StateDiff>
        ): String

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

    data class StateDiff(
        val propertyName: String,
        val oldValue: Any?,
        val newValue: Any?,
        val changeType: ChangeType
    )

    enum class ChangeType {
        MODIFIED, ADDED
    }

    class DefaultLogFormatter : LogFormatter {
        override fun formatStart(context: MiddlewareContext<*>): String {
            return buildString {
                append("🔵 Transaction started: ${context.transaction.useCaseId}")
                if (context.metadata.isNotEmpty()) {
                    append(" [Metadata: ${context.metadata}]")
                }
            }
        }

        override fun formatComplete(
            context: MiddlewareContext<*>,
            durationMs: Long,
            stateDiff: Map<State<*>, StateDiff>
        ): String {
            return buildString {
                appendLine("✅ Transaction completed: ${context.transaction.useCaseId}")
                append("   Duration: ${durationMs}ms")

                if (stateDiff.isNotEmpty()) {
                    appendLine("\n   State changes:")
                    stateDiff.values.forEach { diff ->
                        appendLine("   - ${diff.propertyName}:")
                        appendLine("     Before: ${formatValue(diff.oldValue)}")
                        appendLine("     After:  ${formatValue(diff.newValue)}")
                    }
                }
            }
        }

        override fun formatError(
            context: MiddlewareContext<*>,
            error: Throwable,
            durationMs: Long,
            stateDiff: Map<State<*>, StateDiff>
        ): String {
            return buildString {
                appendLine("❌ Transaction failed: ${context.transaction.useCaseId}")
                appendLine("   Duration: ${durationMs}ms")
                appendLine("   Error: ${error.message}")

                if (stateDiff.isNotEmpty()) {
                    appendLine("   Attempted state changes:")
                    stateDiff.values.forEach { diff ->
                        appendLine("   - ${diff.propertyName}:")
                        appendLine("     Before: ${formatValue(diff.oldValue)}")
                        appendLine("     After:  ${formatValue(diff.newValue)}")
                    }
                }
            }
        }

        override fun formatStateChange(
            context: MiddlewareContext<*>,
            state: State<*>,
            oldValue: Any,
            newValue: Any
        ): String {
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

        // Log and capture initial state of all properties
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
        val stateDiff = calculateStateDiff(context)

        // Log mutations that were attempted
        context.transaction.modifiedProperties.forEach { state ->
            val propertyName = context.vault.properties.entries.find { it.value == state }?.key ?: "unknown"
            log("Mutation attempted - $propertyName:")
            log("  Previous value: ${formatValue(context.transaction.previousValues[state])}")
            log("  Current value: ${formatValue(state.invoke())}")
        }

        log(options.formatter.formatComplete(context, duration, stateDiff))
        stateSnapshots.clear()
    }

    override fun onTransactionError(context: MiddlewareContext<T>, error: Throwable) {
        val duration = System.currentTimeMillis() - startTime
        val stateDiff = calculateStateDiff(context)
        log(options.formatter.formatError(context, error, duration, stateDiff))

        if (options.includeStackTrace) {
            log(error.stackTraceToString())
        }
        stateSnapshots.clear()
    }

    private fun calculateStateDiff(context: MiddlewareContext<T>): Map<State<*>, StateDiff> {
        val diff = mutableMapOf<State<*>, StateDiff>()

        context.vault.properties.forEach { (name, state) ->
            try {
                val oldValue = stateSnapshots[state]
                val newValue = state.invoke()

                if (oldValue != newValue || state in context.transaction.modifiedProperties) {
                    diff[state] = StateDiff(
                        propertyName = name,
                        oldValue = oldValue,
                        newValue = newValue,
                        changeType = when {
                            oldValue == null -> ChangeType.ADDED
                            else -> ChangeType.MODIFIED
                        }
                    )
                }
            } catch (e: Exception) {
                log("Failed to calculate diff for $name: ${e.message}")
            }
        }

        return diff
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