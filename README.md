# Vault: Making State Management Natural in Kotlin Multiplatform

[![Kotlin](https://img.shields.io/badge/kotlin-1.9.0-blue.svg)](http://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Imagine building applications where state management feels as natural as having a conversation – where every state change is predictable, every data transformation is automatic, and every side effect is controlled. This is what Vault brings to Kotlin Multiplatform development.

## The State Management Challenge

Think about the last time you dealt with these common scenarios in your applications:

- A user is halfway through a multi-step form when an error occurs. How do you ensure their data isn't partially saved?
- Your application needs to sync settings across multiple screens. How do you keep everything consistent?
- You're handling real-time updates while processing user input. How do you prevent race conditions?

These challenges arise because state management in modern applications is inherently complex. Vault transforms these complexities into simple, intuitive patterns that feel natural to Kotlin developers.

## A Fresh Approach to State Management

Let's explore how Vault makes state management both powerful and intuitive through its core concepts.

### The Power of Type-Safe State

Imagine your application's state as a well-organized filing cabinet where every document has its proper place and format. Vault creates this structure through type-safe state containers:

```kotlin
object UserManager : Vault<UserManager>() {
    // States are like labeled drawers in your filing cabinet
    val profile by state { 
        UserProfile(
            name = "",
            email = "",
            preferences = UserPreferences()
        )
    }
    
    // Each drawer ensures its contents are always valid
    val sessionStatus by state { SessionStatus.LOGGED_OUT }
    val lastActive by state { Clock.System.now() }
}
```

This type safety isn't just about preventing errors – it's about making your code self-documenting and maintainable. The compiler becomes your first line of defense against state-related bugs.

### Transactions: Your Safety Net

Think of transactions like a choreographed dance where every step must be perfect, or the whole sequence starts over. Vault ensures your state changes follow this all-or-nothing principle:

```kotlin
fun login(credentials: Credentials) = UserManager action {
    // All these changes succeed together or fail together
    profile mutate fetchUserProfile(credentials)
    sessionStatus mutate SessionStatus.ACTIVE
    lastActive mutate Clock.System.now()
    
    // If anything fails, everything automatically reverts
    // No partial updates, no inconsistent state
}
```

This transactional approach means you can focus on describing what should happen, while Vault handles the complexities of making it happen safely.

### Transformers: Your Data Quality Guardians

Consider how a spell-checker automatically corrects text as you type. Vault's transformers work similarly, automatically cleaning and validating your data:

```kotlin
class ProfileTransformer : Transformer<UserProfile> {
    override fun set(value: UserProfile): UserProfile {
        return value.copy(
            name = value.name.trim(),
            email = value.email.trim().lowercase(),
            // Ensure phone numbers are consistently formatted
            phone = value.phone.replace(Regex("[^0-9+]"), "")
        )
    }
}

// Every profile update now automatically ensures data quality
val profile by state(ProfileTransformer()) { UserProfile() }
```

### Effects: Controlled Reactions to Change

Effects in Vault are like setting up smart notifications that automatically handle themselves. They make managing side effects as simple as describing what should happen:

```kotlin
class UserPresenter {
    private var cleanup: Disposable? = null
    
    fun start() {
        // Automatically respond to user status changes
        cleanup = UserManager.sessionStatus effect { status ->
            when (status) {
                SessionStatus.ACTIVE -> {
                    refreshUserInterface()
                    startHeartbeat()
                }
                SessionStatus.LOGGED_OUT -> {
                    clearInterface()
                    stopHeartbeat()
                }
            }
        }
    }
    
    fun stop() {
        // Clean up is straightforward
        cleanup?.dispose()
    }
}
```

### Thread Safety That Just Works

Modern applications are inherently concurrent, but managing thread safety shouldn't be your daily concern. Vault's sophisticated locking system handles this automatically:

```kotlin
// This code is automatically thread-safe
UserManager action {
    // Concurrent access is handled transparently
    profile mutate updatedProfile
    lastActive mutate Clock.System.now()
}
```

The system provides fine-grained locking, deadlock prevention, and efficient read operations without you having to think about the details.

## Real Applications, Real Solutions

Vault shines in real-world scenarios where state management complexity typically creates challenges:

### Form Management
```kotlin
object FormManager : Vault<FormManager>() {
    val formData by state { FormData() }
    val validation by state { ValidationState() }
    val submitStatus by state { SubmitStatus.READY }
    
    // All form operations are automatically transactional
    fun submit() = action {
        submitStatus mutate SubmitStatus.SUBMITTING
        try {
            val result = submitToServer(formData.value)
            formData mutate FormData() // Clear on success
            submitStatus mutate SubmitStatus.COMPLETED
        } catch (e: Exception) {
            submitStatus mutate SubmitStatus.FAILED
            // Original form data is preserved
        }
    }
}
```

### Real-time Updates
```kotlin
object ChatManager : Vault<ChatManager>() {
    val messages by state { listOf<Message>() }
    val connectionStatus by state { ConnectionStatus.DISCONNECTED }
    
    // Real-time updates are safely handled
    fun connect() = action {
        connectionStatus mutate ConnectionStatus.CONNECTING
        startWebSocket { message ->
            action {
                messages mutate (messages.value + message)
            }
        }
    }
}
```

## Experience the Difference

Vault brings clarity and confidence to state management in Kotlin Multiplatform applications. It transforms complex state management challenges into straightforward, maintainable solutions that just work.

Start building better applications today with Vault – where state management feels natural, and you can focus on creating great features for your users.

## Contributing

Join us in making state management better for everyone. See our [Contributing Guide](CONTRIBUTING.md) for details.

## License

Vault is released under the Apache 2.0 license. See [LICENSE](LICENSE) for details.

---

*Created by developers who believe state management should be a joy, not a chore.*