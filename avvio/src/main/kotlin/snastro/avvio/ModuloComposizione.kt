package snastro.avvio

import kotlinx.coroutines.CoroutineScope
import snastro.avvio.coda.FonteCoda
import snastro.kernel.AbbonatoDopoCommit
import snastro.kernel.AbbonatoSincrono
import snastro.kernel.EventoPubblicato
import kotlin.reflect.KClass

/**
 * One event type paired with a subscriber VALUE (ADR 0030 §1 [user C3]): the adapters expose their
 * `AbbonatoSincrono`/`AbbonatoDopoCommit`, the module pairs each with the event types it handles, and
 * `apriProgetto` registers the pair so that [abbonato] receives only events of type [evento]. The declared pairs are
 * therefore the whole truth of who hears what (AC-355: asserted on these lists).
 */
internal class Abbonamento<out A : Any>(val evento: KClass<out EventoPubblicato>, val abbonato: A)

/** [abbonato] paired with each of [eventi], in that order. */
internal fun <A : Any> abbonamenti(abbonato: A, vararg eventi: KClass<out EventoPubblicato>): List<Abbonamento<A>> =
    eventi.map { Abbonamento(it, abbonato) }

/** Something `apriProgetto` starts (step 6) and `ArrestoProgetto` stops, in the reverse order. */
internal interface Avviabile {
    /** Starts the background loops on [scope] (the open project's scope); launches nothing before. */
    fun avvia(scope: CoroutineScope)

    /** Blocking: returns once every background loop [avvia] started has ended (the scope was cancelled first). */
    fun ferma()
}

/**
 * One context's part of the single composition (ADR 0030 §1): built from `PorteProgetto` only (it never builds a
 * repository), it exposes its subscriber values, its queue sources, [avvia]/[ferma] and its typed collaborators.
 * Every member defaults to "none": a module overrides what it has.
 */
internal interface ModuloComposizione : Avviabile {
    fun abbonatiSincroni(): List<Abbonamento<AbbonatoSincrono>> = emptyList()

    fun abbonatiDopoCommit(): List<Abbonamento<AbbonatoDopoCommit>> = emptyList()

    fun fontiCoda(): List<FonteCoda> = emptyList()

    override fun avvia(scope: CoroutineScope) = Unit

    override fun ferma() = Unit
}
