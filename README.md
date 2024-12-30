# 🔒 Vault.kt

> Transaction-safe state management for Kotlin coroutines that just works.

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Latest Release](https://img.shields.io/badge/release-3.17.4-green.svg)](https://github.com/vynatix/vault/releases)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.0-orange.svg)](https://kotlinlang.org)

Vault is a revolutionary state management library that brings the power of database transactions to your application state. Built from the ground up for Kotlin, it combines bulletproof reliability with elegant syntax and seamless coroutine integration.

## ✨ Why Vault?

```kotlin
class UserVault : Vault<UserVault>() {
    val username by asset { "none" }
    val isLoggedIn by asset { false }
}

// Atomic operations with automatic rollback
val result = userVault {
    username mutate "Jane Doe"
    isLoggedIn mutate true
    // If anything fails, everything rolls back automatically!
}
```

### 🚀 Key Features

- **Transactional Safety**: Every state change is wrapped in a transaction that either succeeds completely or rolls back automatically
- **Zero Boilerplate**: Property delegation and clean syntax make state management a breeze
- **Type-Safe by Design**: Leverage Kotlin's type system for compile-time safety
- **High Performance**: Optimized with object pooling and efficient state tracking
- **Built for Scale**: From small apps to enterprise systems, Vault grows with you
- **Coroutine Integration**: Built on Kotlin Flow for seamless reactivity
- **Direct binding**: Direct binding to data sources through the repository pattern

## 📦 Installation

Add Vault to your project in just one line:

```kotlin
implementation("com.vynatix:vault:3.17.4")
```

## 🎯 Quick Start

### 1. Define Your State

```kotlin
class ProfileVault : Vault<ProfileVault>() {
    val name by asset { "" }
    val bio by asset { "" }
    val socialLinks by asset { listOf<String>() }
}
```

### 2. Create Your Repositories

```kotlin
class NameRepository : IRepository<String> {
    private val _dataFlow = MutableSharedFlow<String>(replay = 1)
    override fun flow(): SharedFlow<String> = _dataFlow.asSharedFlow()
    override fun set(value: String) = _dataFlow.tryEmit(value)
}
```

### 3. Connect and Operate

```kotlin
val vault = ProfileVault()
vault {
    // Connect repositories
    name repository NameRepository()
    
    // Add effects
    name effect ::println
    
    // Perform operations
    operation { vault ->
        name mutate "Jane Doe"
        bio mutate "Engineering Lead"
        socialLinks mutate listOf("github.com/jane", "twitter.com/jane")
    }
}
```

## 💡 What Makes Vault Different?

### Traditional State Management
```kotlin
// Hope nothing fails halfway through...
viewModel.name = "Jane"
viewModel.email = "jane@example.com"
viewModel.status = "active"
```

### Vault's Approach
```kotlin
vault {
    operation { vault ->
        name mutate "Jane"
        email mutate "jane@example.com"
        status mutate "active"
        // Automatic rollback if anything fails!
    }
}
```

## 🛠 Features In-Depth

### Repository Integration

Seamlessly connect to your data layer:

```kotlin
class UserRepository : IRepository<String> {
    private val _dataFlow = MutableSharedFlow<String>(replay = 1)
    override fun flow() = _dataFlow.asSharedFlow()
    override fun set(value: String) = _dataFlow.tryEmit(value)
}

// One-line binding
userVault {
    username repository UserRepository()
}
```

### Effect Handling

Add reactive effects to any asset:

```kotlin
userVault {
    username effect { name ->
        println("Name updated: $name")
    }
}
```

### Powerful Middleware

Add logging, analytics, or any cross-cutting concern:

```kotlin
class LoggingMiddleware : Middleware<UserVault>() {
    override fun onTransactionStarted(context: MiddlewareContext<UserVault>) {
        println("Starting: ${context.transaction.useCaseId}")
    }
}

vault.middlewares(LoggingMiddleware())
```

## 📊 Performance

Vault is built for speed:
- **Object Pooling**: Minimal garbage collection pressure through transaction pooling
- **Efficient State Tracking**: Smart diffing and update mechanisms through Flow
- **Optimized Flow Usage**: Minimal overhead for reactivity
- **Transaction Management**: Efficient handling of concurrent operations

## 🎨 Clean Architecture

Perfect for modern architectural patterns:
- **MVVM**: Perfect companion for ViewModels
- **MVI**: Ideal for handling intents and state
- **Clean Architecture**: Natural fit for use cases and repositories
- **Repository Pattern**: First-class support through IRepository interface

## 🤔 Why Choose Vault?

| Feature | Vault | Redux | MobX |
|---------|-------|-------|------|
| Transaction Safety | ✅ | ❌ | ❌ |
| Type Safety | ✅ | ⚠️ | ✅ |
| Automatic Rollback | ✅ | ❌ | ❌ |
| Coroutine Integration | ✅ | ⚠️ | ❌ |
| Boilerplate | Minimal | Heavy | Moderate |
| Learning Curve | Gentle | Steep | Moderate |

## 🚦 Getting Started

1. Add the dependency
2. Create your first vault
3. Write your first operation
4. Watch our [Quick Start Video](https://vault.vynatix.com/quickstart)

## 📊 Transaction Management

Vault uses a sophisticated transaction system:
- **Object Pooling**: Efficient reuse of transaction objects
- **Automatic Tracking**: Tracks modified properties during transactions
- **Rollback Support**: Stores previous values for automatic rollback
- **Transaction Context**: Access through `activeTransaction` Flow
- **Middleware Support**: Intercept and modify transactions
- **Coroutine Integration**: Built for asynchronous operations

## 📚 Documentation

- [Full Documentation](https://vault.vynatix.com/docs)
- [API Reference](https://vault.vynatix.com/api)
- [Best Practices Guide](https://vault.vynatix.com/best-practices)
- [Migration Guide](https://vault.vynatix.com/migration)

## 🤝 Support

- 💬 [Discord Community](https://discord.gg/vault)
- 📧 [Email Support](mailto:support@vynatix.com)
- 🐦 [Twitter Updates](https://twitter.com/vynatix)

## 📜 License

Vault is available under the Apache License 2.0. See the [LICENSE](LICENSE) file for more info.

---

<p align="center">Built with ❤️ by <a href="https://vynatix.com">Vynatix</a></p>