import com.vynatix.*
import com.vynatix.TransactionResult.*

data class Profile(
    val id: String,
    val name: String,
    val age: Int,
    val email: String
)

data class Post(
    val id: String,
    val title: String,
    val content: String,
    val author: String
)

class ProfileCleaner : Transformer<Profile> {
    private fun process(value: Profile): Profile {
        return value.copy(
            name = value.name.trim(),
            email = value.email.trim()
        )
    }

    override fun set(value: Profile): Profile =
        process(value)

    override fun get(value: Profile): Profile =
        process(value)
}

object Social : Vault<Social>() {
    val profile by state(ProfileCleaner()) { Profile("1", "John Doe", 30, "John@doe.ai") }
    val followers by state { 0 }
    val following by state { 0 }
    val posts by state { emptyList<Post>() }
    val loggedIn by state { false }
}

fun main(): Unit = Social {
    middlewares(
        LoggingMiddleware(),
        AnalyticsMiddleware()
    )

    val loginDisposable = loggedIn effect {
        if (this) {
            println("🔑 User logged in")
        } else {
            println("🔓 User logged out")
        }
    }

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


    val login = action {
        profile mutate Profile("1", "John Doe", 30, "John@doe.ai")
        followers mutate 100
        following mutate 200
        posts mutate listOf(
            Post("1", "Hello World", "This is my first post", "John Doe")
        )
        loggedIn mutate true
    }

    when (login) {
        is Success -> println("Login successful")
        is Error -> println("Login failed: ${login.exception.message}")
    }

    loginDisposable.dispose()
}
