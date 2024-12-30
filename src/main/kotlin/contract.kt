package com.vynatix

import kotlin.reflect.KProperty

fun interface Transmitter<T : Any> {
    fun transmitted(observer: (T) -> Unit): Boolean
}

fun interface Receiver<T : Any> {
    fun received(value: T): Boolean
}
interface Repository<T : Any> : Transmitter<T>, Receiver<T>
fun interface Initializer<T : Any> : () -> T
interface State<T : Any> {
    val value: T
}

fun interface Action<V : Vault<V>> : (V) -> Unit
fun interface Effect<T : Any> : (T) -> Unit


fun interface StateDelegate<T : Any> {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): State<T>
}


