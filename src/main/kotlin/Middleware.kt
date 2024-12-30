package com.vynatix

open class Middleware<T : Vault<T>> {
    data class MiddlewareContext<T : Vault<T>>(
        val vault: T,
        val transaction: Transaction,
        val metadata: MutableMap<String, Any> = mutableMapOf()
    )

    private fun execute(context: MiddlewareContext<T>, next: () -> Unit) {
        try {
            onTransactionStarted(context)
            next()
            onTransactionCompleted(context)
        } catch (e: Throwable) {
            onTransactionError(context, e)
            throw e
        }
    }

    operator fun invoke(vault: T, next: () -> Unit) {
        val context = MiddlewareContext(
            vault = vault,
            transaction = vault.activeTransaction.value ?: error("No active transaction")
        )
        execute(context, next)
    }

    protected open fun onTransactionStarted(context: MiddlewareContext<T>) {}
    protected open fun onTransactionCompleted(context: MiddlewareContext<T>) {}
    protected open fun onTransactionError(
        context: MiddlewareContext<T>,
        error: Throwable
    ) {
    }
}