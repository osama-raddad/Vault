package com.vynatix



class MutableState<T : Any>(
    initialValue: T,
    private val transformer: Transformer<T>? = null
) : State<T> {
    private val stateLock = VaultLock()
    private val observersLock = VaultLock()
    private val bridgeLock = VaultLock()

    private val observers = mutableSetOf<(T) -> Unit>()
    @Volatile private var _value: T = initialValue
    @Volatile private var _repository: Bridge<T>? = null

    override var value: T
        get() = stateLock.withLock { afterGet(_value) }
        set(newValue) = stateLock.withLock { updateState(newValue, true) }

    private fun afterGet(rawValue: T): T = stateLock.withLock {
        transformer?.takeIf { it.shouldTransform(rawValue) }
            ?.get(rawValue) ?: rawValue
    }

    private fun beforeSet(newValue: T): T = stateLock.withLock {
        transformer?.takeIf { it.shouldTransform(newValue) }
            ?.set(newValue) ?: newValue
    }

    private fun updateState(newValue: T, notifyRepository: Boolean) {
        stateLock.withLock {
            val processedValue = beforeSet(newValue)
            _value = processedValue

            // Notify observers under observer lock
            observersLock.withLock {
                notifyObservers(processedValue)
            }

            // Notify repository if needed under bridge lock
            if (notifyRepository) {
                bridgeLock.withLock {
                    _repository?.publish(processedValue)
                }
            }
        }
    }

    private fun notifyObservers(value: T) = observersLock.withLock {
        // Create a snapshot of observers to reduce lock holding time
        val currentObservers = observers.toSet()
        currentObservers.forEach { observer ->
            try {
                observer(value)
            } catch (e: Exception) {
                // Handle observer notification failure
                // Could add error handling strategy here
            }
        }
    }

    fun observe(observer: (T) -> Unit): Disposable = observersLock.withLock {
        observers.add(observer)
        // Initial notification with current value
        val currentValue = stateLock.withLock { _value }
        observer(currentValue)

        return Disposable {
            observersLock.withLock {
                observers.remove(observer)
            }
        }
    }

    var bridge: Bridge<T>?
        get() = bridgeLock.withLock { _repository }
        set(value) = bridgeLock.withLock {
            _repository = value
            value?.observe { receivedValue ->
                updateState(receivedValue, false)
            }
        }
}