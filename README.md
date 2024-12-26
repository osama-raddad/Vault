# Vault

Vault is a next-generation state management library for Kotlin that combines transactional safety with reactive programming. It provides a robust, type-safe way to manage application state with automatic error recovery and seamless persistence integration.

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

## Features

- 🔒 **Transactional State Management**: Every state change is wrapped in a transaction that either completes successfully or rolls back automatically
- ⚡ **Reactive Programming**: Built-in integration with Kotlin Flow for efficient reactive updates
- 🎯 **Type-safe API**: Leverages Kotlin's type system for compile-time safety
- 🔄 **Automatic Rollback**: Built-in error recovery keeps your state consistent
- 📦 **Repository Pattern**: Seamless integration with your data layer
- 🛠 **Middleware Support**: Extensible system for cross-cutting concerns
- 🚀 **High Performance**: Optimized with features like operation pooling
- 📝 **Clean Syntax**: Minimal boilerplate through property delegation

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.vynatix:vault:3.17.4")
}
```

## Quick Start

1. Define your vault with assets:

```kotlin
class UserVault(vaultFactory: VaultFactory) : IVault by vaultFactory() {
    val username by asset { "John Doe" }
    val email by asset { "none" }
    val isLoggedIn by asset { false }
}
```

2. Perform atomic operations:

```kotlin
suspend fun login() = userVault.operation("login") {
    userVault.username("Jane Doe")
    userVault.email("jane@example.com")
    userVault.isLoggedIn(true)
}
```

3. Observe state changes:

```kotlin
userVault.username.onEach { username ->
    println("Username updated: $username")
}.launchIn(scope)
```

## Key Concepts
### Built for Performance
Vault doesn't just make state management safer—it makes it faster. Through innovative features like operation pooling and efficient state tracking, Vault minimizes memory allocations and optimizes state updates.
### Assets

Assets are state containers and each asset is:
- Type-safe
- Reactive
- Transaction-aware
- Repository-backed
- Automatically managed
```kotlin
class ProfileVault(vaultFactory: VaultFactory) : IVault by vaultFactory() {
    val name by asset { "" }
    val bio by asset { "" }
    val socialLinks by asset { listOf<String>() }
}
```

### Transactions
Imagine if you could manage your application state with the same reliability as database transactions. That's exactly what Vault brings to the table. Every state change in Vault is wrapped in a transaction that either completes successfully or rolls back automatically, ensuring your application state remains consistent even when errors occur.

All state modifications are wrapped in transactions:

```kotlin
vault.operation("updateProfile") {
    vault.name("Jane Doe")
    vault.bio("Software Engineer")
    // Automatic rollback if any operation fails
}
```

### Repository Integration

Vault seamlessly integrates with your data layer through its repository pattern:

```kotlin
class UserRepository : IRepository<String> {
    private val _dataFlow = MutableSharedFlow<String>(replay = 1)

    override fun set(value: String) {
        _dataFlow.tryEmit(value)
    }

    override fun flow(): SharedFlow<String> = _dataFlow.asSharedFlow()
}

// Bind repository to asset
userVault.username.repository(UserRepository())
```

### Middleware

Add cross-cutting concerns with middleware:

```kotlin
data object LoggingMiddleware : Middleware<IVault>() {
    override suspend fun onTransactionStarted(context: MiddlewareContext<IVault>) {
        println("Operation started: ${context.transaction.useCaseId}")
    }
}

userVault.middlewares(LoggingMiddleware)
```

## The Future of State Management

Vault represents a significant step forward in state management, combining:
- Transactional safety
- Reactive programming
- Clean architecture
- High performance
- Developer ergonomics

Whether you're building a small application or a large-scale system, Vault provides the tools you need to manage state effectively, safely, and elegantly.

## Ready to Try Vault?

Experience the next generation of state management. With Vault, you get the power of transactional operations, the simplicity of property delegation, and the reliability of reactive programming—all in one cohesive package.

Join the growing community of developers who are discovering how Vault can transform their approach to state management in Kotlin applications.

Remember: Your application state is too important to leave to chance. Choose Vault and build with confidence.

## How Vault Compares to Other Solutions

Let's look at how Vault stacks up against other popular state management solutions:

### Vault vs Redux

While Redux pioneered unidirectional data flow and centralized state management, Vault takes these concepts further:

```kotlin
// Redux approach
const userReducer = (state, action) => {
  switch (action.type) {
    case 'UPDATE_USER':
      return { ...state, username: action.payload }
    default:
      return state
  }
}

// Vault approach
class UserVault(vaultFactory: VaultFactory) : IVault by vaultFactory() {
    val username by asset { "" }
}

vault.operation("updateUser") {
    vault.username("newName")
}
```

**Key Advantages over Redux:**
- No boilerplate actions or reducers
- Built-in transaction safety
- Type-safe by default
- Automatic state rollback
- Direct property access
- Built-in persistence layer

### Vault vs MobX

MobX brought reactive programming to state management. Vault builds on this foundation:

```kotlin
// MobX approach
class UserStore {
    @observable username = ""
    
    @action
    setUsername(name) {
        this.username = name
    }
}

// Vault approach
class UserVault(vaultFactory: VaultFactory) : IVault by vaultFactory() {
    val username by asset { "" }
}
```

**Key Advantages over MobX:**
- Native Kotlin coroutines integration
- Transaction-based operations
- Built-in repository pattern
- More predictable state changes
- Better testing support
- No decorators needed

### Vault vs BLoC

BLoC pattern brought stream-based state management to Flutter. Vault offers similar benefits with additional safety:

```kotlin
// BLoC approach
class UserBloc extends Bloc<UserEvent, UserState> {
    Stream<UserState> mapEventToState(UserEvent event) async* {
        yield UserState(username: event.username)
    }
}

// Vault approach
class UserVault(vaultFactory: VaultFactory) : IVault by vaultFactory() {
    val username by asset { "" }
}
```

**Key Advantages over BLoC:**
- Simpler mental model
- Automatic error recovery
- Built-in transaction support
- More intuitive API
- Property-based access
- Better type inference

### Vault vs Mobius

Spotify's Mobius offers pure functional state management. Vault provides similar benefits with more flexibility:

```kotlin
// Mobius approach
data class Model(val username: String)
fun update(model: Model, event: Event): Next<Model, Effect>

// Vault approach
class UserVault(vaultFactory: VaultFactory) : IVault by vaultFactory() {
    val username by asset { "" }
}
```

**Key Advantages over Mobius:**
- More familiar property-based API
- Built-in persistence
- Simpler integration
- Transaction safety
- Direct state access when needed
- Less boilerplate

### Feature Comparison Matrix

| Feature | Vault | Redux | MobX | BLoC | Mobius |
|---------|-------|-------|------|------|---------|
| Transaction Support | ✅ Built-in | ❌ Manual | ⚠️ Partial | ❌ Manual | ❌ Manual |
| Type Safety | ✅ Native | ⚠️ Plugin | ✅ Native | ✅ Native | ✅ Native |
| Reactivity | ✅ Flow | ⚠️ Manual | ✅ Auto | ✅ Stream | ⚠️ Manual |
| Error Recovery | ✅ Automatic | ❌ Manual | ❌ Manual | ❌ Manual | ❌ Manual |
| Persistence | ✅ Built-in | ❌ Manual | ❌ Manual | ❌ Manual | ❌ Manual |
| Testing | ✅ Simple | ⚠️ Complex | ⚠️ Complex | ✅ Simple | ✅ Simple |
| Boilerplate | ✅ Minimal | ❌ Heavy | ✅ Minimal | ⚠️ Moderate | ⚠️ Moderate |
| Learning Curve | ✅ Low | ❌ High | ✅ Low | ⚠️ Moderate | ❌ High |


## Best Practices

1. **Group Related States**: Organize related states in a single vault
2. **Meaningful Operation Names**: Use descriptive names for operations
3. **Keep Transactions Small**: Focus on atomic operations
4. **Error Handling**: Let the vault handle rollbacks automatically
5. **Repository Pattern**: Use repositories for data persistence
6. **Middleware for Cross-cutting Concerns**: Implement logging, analytics, etc.

## Performance Considerations

- Operations are pooled for memory efficiency
- State changes are tracked efficiently
- Middleware execution is optimized
- Flow operations are cold by default

## Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

Special thanks to all contributors who have helped shape Vault into what it is today.

## Support

- 📚 [Documentation](https://vault.vynatix.com/docs)
- 💬 [Discord Community](https://discord.gg/vault)
- 🐦 [Twitter](https://twitter.com/vynatix)
- 📧 [Email Support](mailto:support@vynatix.com)

## FAQ

**Q: Why choose Vault over other state management solutions?**  
A: Vault provides unique features like transactional safety, automatic rollback, and seamless repository integration while maintaining a clean, reactive API.

**Q: Is Vault suitable for large-scale applications?**  
A: Yes! Vault is designed for scalability and is particularly well-suited for enterprise applications with complex state management needs.

**Q: How does Vault handle concurrent modifications?**  
A: Vault uses Kotlin coroutines and mutex locks to ensure thread-safe state modifications.

**Q: Can I use Vault with existing repositories?**  
A: Yes, Vault's `IRepository` interface makes it easy to integrate with existing data sources.

## For Managers

Vault helps engineering managers deliver business value through:

### Risk Management
- Automatic rollback capabilities prevent cascading failures
- Built-in audit trails for compliance
- Complete history of state changes

### Team Productivity
- Faster development cycles with less boilerplate
- Easier onboarding with standard patterns
- Clear separation of concerns

### Resource Optimization
- Reduced development and maintenance time
- Built-in debugging and monitoring
- Lower technical debt

### Business Metrics
- Real-time insights into system state
- Performance monitoring
- Clear success metrics

See our detailed [Manager's Guide](docs/managers-guide.md) for more information.

## For Architects

Vault provides architects with powerful tools for building robust systems:

### Clean Architecture Support
```kotlin
// Clear separation of concerns
class OrderUseCase(private val orderVault: OrderVault) {
    suspend fun completeOrder(orderId: String) = orderVault.operation("completeOrder") {
        orderVault.status(OrderStatus.COMPLETED)
        orderVault.completedAt(Instant.now())
    }
}
```

### Domain-Driven Design
```kotlin
// Domain-specific vaults
class OrderVault(vaultFactory: VaultFactory) : IVault by vaultFactory() {
    val status by asset { OrderStatus.PENDING }
    val items by asset { emptyList<OrderItem>() }
    val totalAmount by asset { Money.zero() }
}
```

### Event-Driven Architecture
- Built-in support for reactive programming
- Clean event propagation
- State synchronization

### Enterprise Patterns
- CQRS support
- Event sourcing capabilities
- Saga pattern for distributed transactions
- Microservices integration

See our detailed [Architect's Guide](docs/architects-guide.md) for more information.

# Roadmap

- [ ] More built-in middleware options
- [ ] Enhanced debugging tools
- [ ] Performance monitoring utilities
- [ ] Additional repository adapters
- [ ] Integration with popular frameworks

Keep an eye on our [GitHub issues](https://github.com/vynatix/vault/issues) for updates!