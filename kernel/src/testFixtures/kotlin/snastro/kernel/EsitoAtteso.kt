package snastro.kernel

import kotlin.test.assertIs

/** Asserts this [Esito] is [Esito.Ok] and returns its value. */
public fun <T> Esito<T>.atteso(): T = assertIs<Esito.Ok<T>>(this).valore

/** Asserts this [Esito] is an [Esito.Errore] carrying an [E] and returns it. */
public inline fun <reified E : ErroreDominio> Esito<*>.erroreAtteso(): E = assertIs<E>(assertIs<Esito.Errore>(this).errore)
