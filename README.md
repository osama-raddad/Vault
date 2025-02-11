# Vault

A powerful, transactional state management library for Kotlin Multiplatform applications that provides ACID guarantees for application state.

## Table of Contents
1. [Introduction](#introduction)
2. [Installation](#installation)
3. [Core Concepts](#core-concepts)
4. [Advanced Topics](#advanced-topics)
5. [Best Practices](#best-practices)
6. [API Reference](#api-reference)
7. [Performance Guidelines](#performance-guidelines)
8. [Testing](#testing)
9. [FAQ](#faq)

## Introduction

Vault is a type-safe, transaction-based state management library designed specifically for Kotlin Multiplatform applications. It provides ACID (Atomicity, Consistency, Isolation, Durability) guarantees while maintaining high performance through lockless architecture.

### Key Features

- 🔒 **ACID Transactions**
- 🔄 **Effect System**
- 🌉 **Bi-directional state**: 
- 🎯 **Middleware System**
- 🚀 **Lock-free concurrency**

## Installation

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.vynatix:vault:1.0.0")
}
```

## Core Concepts

### State

States are the fundamental building blocks in Vault. They represent immutable, type-safe data containers.

```kotlin
object UserVault : Vault<UserVault>() {
    val profile by state { Profile("", "", 0, "") }
    val followers by state { 0 }
    val posts by state { emptyList<Post>() }
}
```

### Actions

Actions provide atomic state mutations with automatic rollback on failure.

```kotlin
fun updateProfile(newProfile: Profile) = action {
    profile mutate newProfile
}
```

### Effects

Effects handle side-effects reactively based on state changes.

```kotlin
profile effect { 
    println("Profile updated: $it")
}
```

### Middleware

Middleware intercepts transactions for cross-cutting concerns.

```kotlin
class LoggingMiddleware : Middleware<UserVault>() {
    override fun onTransactionStarted(context: MiddlewareContext<UserVault>) {
        println("Transaction started: ${context.transaction.id}")
    }
}
```

## Advanced Topics

### Transaction Management

Transactions in Vault provide:

- Atomic state mutations
- Automatic rollback on failure
- State change tracking
- Transaction metadata

```kotlin
val result = vault action {
    profile mutate newProfile
    followers mutate newFollowerCount
    
    if (someCondition) {
        throw RuntimeException("Rollback all changes")
    }
}
```

### Bridges

Bridges enable bi-directional state synchronization:

```kotlin
loggedIn.bridge(object : Bridge<Boolean> {
    private val observers = mutableListOf<(Boolean) -> Unit>()
    private var value: Boolean? = null

    override fun observe(observer: (Boolean) -> Unit): Disposable {
        value?.let(observer)
        observers.add(observer)
        return Disposable { observers.remove(observer) }
    }

    override fun publish(value: Boolean): Boolean {
        this.value = value
        observers.forEach { it(value) }
        return true
    }
})
```

### Lock-Free Concurrency

Vault uses a sophisticated lock-free architecture for high performance:

- Custom `VaultLock` implementation
- Reentrant locking support
- Automatic deadlock prevention
- Optimistic concurrency control

## Best Practices

1. **State Design**
   - Keep states granular and focused
   - Use transformers for validation
   - Clean up disposables
   - Handle errors appropriately

2. **Transaction Management**
   - Keep transactions atomic and focused
   - Use proper error handling
   - Clean up resources
   - Monitor transaction performance

3. **Middleware Usage**
   - Keep middleware focused on cross-cutting concerns
   - Handle errors gracefully
   - Monitor middleware performance
   - Clean up resources

4. **Testing**
   - Test all state transitions
   - Verify rollback behavior
   - Test concurrent access
   - Validate transformers
   
## Performance Guidelines

1. **Lock Management**
   - Minimize lock duration
   - Use appropriate granularity
   - Monitor lock contention
   - Handle lock timeouts

2. **Transaction Optimization**
   - Keep transactions short
   - Batch operations when appropriate
   - Monitor transaction performance
   - Use appropriate isolation levels

3. **Resource Management**
   - Clean up disposables
   - Monitor memory usage
   - Handle resource leaks
   - Use appropriate pooling

## Testing

Vault provides comprehensive testing support through its `TestVault` implementation:

```kotlin
class TestVault : Vault<TestVault>() {
    val counter by state { 0 }
    val text by state { "" }
    val list by state { listOf<Int>() }
}

class StressTestRunner(
    private val iterations: Int = 100000,
    private val concurrentVaults: Int = 50,
    private val complexOperations: Boolean = true
) {
    // Stress testing implementation
}
```

## FAQ

### Q: How does Vault handle concurrent modifications?
A: Vault uses a lock-free architecture with optimistic concurrency control. Each state has its own lock, and transactions are automatically rolled back on conflicts.

### Q: What happens if a transaction fails?
A: All state changes within a failed transaction are automatically rolled back to their previous values.

### Q: Can I use Vault in a multiplatform project?
A: Yes, Vault is designed for Kotlin Multiplatform and works across all supported platforms.

### Q: How does Vault handle memory management?
A: Vault uses proper resource cleanup through disposables and automatic transaction cleanup.

## API Reference

### Core Classes

#### Vault
The base class for all vault implementations:
```kotlin
abstract class Vault<Self : Vault<Self>> {
    fun middlewares(vararg middleware: Middleware<Self>)
    fun action(action: Self.() -> Unit): TransactionResult
    fun <T : Any> state(transformer: Transformer<T>? = null, initialize: Initializer<T>)
}
```

#### State
Represents an immutable state container:
```kotlin
interface State<T : Any> {
    val value: T
}
```

#### Transaction
Handles atomic state mutations:
```kotlin
class Transaction(val id: String) {
    val status: TransactionStatus
    val modifiedProperties: Set<State<out Any>>
    fun rollback()
    fun commit()
}
```

#### Middleware
Intercepts transaction lifecycle events:
```kotlin
open class Middleware<T : Vault<T>> {
    fun onTransactionStarted(context: MiddlewareContext<T>)
    fun onTransactionCompleted(context: MiddlewareContext<T>)
    fun onTransactionError(context: MiddlewareContext<T>, error: Throwable)
}
```

For more details, please refer to the API documentation or the source code.
