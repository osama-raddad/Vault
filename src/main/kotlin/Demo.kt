package com.vynatix

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking

class UserRepository : Repository<String> {
    private val _dataFlow = MutableStateFlow("none")

    override fun set(value: String): Boolean {
        _dataFlow.value = value
        return true
    }

    override fun flow(): SharedFlow<String> = _dataFlow.asSharedFlow()
}

class LoginAttemptsRepository : Repository<Int> {
    private val _dataFlow = MutableStateFlow(0)

    override fun set(value: Int): Boolean {
        _dataFlow.value = value
        return true
    }

    override fun flow(): SharedFlow<Int> = _dataFlow.asSharedFlow()
}

class EmailRepository : Repository<String> {
    private val _dataFlow = MutableStateFlow("none")
    override fun set(value: String): Boolean {
        _dataFlow.value = value
        return true
    }

    override fun flow(): SharedFlow<String> = _dataFlow.asSharedFlow()
}


class IsLoggedInRepository : Repository<Boolean> {
    private val _dataFlow = MutableStateFlow(false)
    override fun set(value: Boolean): Boolean {
        _dataFlow.value = value
        return true
    }

    override fun flow(): SharedFlow<Boolean> = _dataFlow.asSharedFlow()
}

class UserVault : Vault<UserVault>() {
    val username by state(MutableStateFlow("none"))
    val email by state { "none" }
    val loginAttempts by state { 0 }
    val isLoggedIn by state { false }
}

class LoginAction : Action<UserVault> {
    override fun invoke(vault: UserVault) = vault {
        loginAttempts mutate 1
        isLoggedIn mutate true
        username mutate "Osama Raddad"
    }
}

class LoginEffect(private val vault: UserVault) : Effect<Boolean> {
    override fun invoke(value: Boolean) = vault {
        println("Username: ${username()}")
        println("Email: ${email()}")
        println("Login attempts: ${loginAttempts()}")
        println("Is logged in: ${isLoggedIn()}")
    }
}


fun main() = runBlocking {
    val userVault = UserVault()

    val loggingMiddleware = LoggingMiddleware<UserVault>(
        options = LoggingMiddleware.Options(
            logLevel = LoggingMiddleware.LogLevel.DEBUG,
            includeStackTrace = true,
            includeStateValues = true
        )
    )
    userVault.middlewares(loggingMiddleware)

    userVault {
        username repository UserRepository()
        email repository EmailRepository()
        loginAttempts repository LoginAttemptsRepository()
        isLoggedIn repository IsLoggedInRepository()
    }
    val loginEffect = LoginEffect(userVault)

    val result: TransactionResult = userVault {
        username effect ::println
        email effect ::println
        loginAttempts effect ::println
        isLoggedIn effect loginEffect

        this action LoginAction()
    }

    when (result) {
        is TransactionResult.Success -> println("Transaction completed successfully")
        is TransactionResult.Error -> println("Transaction failed with error: ${result.exception}")
    }
}
