package com.vynatix

import kotlin.reflect.KProperty

fun interface Observable<T : Any> {
    fun observe(observer: (T) -> Unit): Disposable
}

fun interface Publisher<T : Any> {
    fun publish(value: T): Boolean
}

interface Bridge<T : Any> : Observable<T>, Publisher<T>
fun interface Initializer<T : Any> : () -> T
interface State<T : Any> {
    val value: T
}

fun interface Action<V : Vault<V>> : (V) -> Unit
fun interface Effect<T : Any> : (T) -> Unit


fun interface StateDelegate<T : Any> {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): State<T>
}


interface Transformer<T : Any> {
    fun set(value: T): T

    fun get(value: T): T

    fun shouldTransform(value: T): Boolean = true
}

fun interface Disposable {
    fun dispose()
}


