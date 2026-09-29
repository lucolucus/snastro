package snastro.avvio.progetto

import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.matcher.ElementMatchers.isConstructor
import net.bytebuddy.matcher.ElementMatchers.isSynthetic
import net.bytebuddy.matcher.ElementMatchers.nameStartsWith
import net.bytebuddy.matcher.ElementMatchers.named
import net.bytebuddy.matcher.ElementMatchers.not
import java.util.IdentityHashMap

/**
 * AC-C60/AC-C61 (ADR 0030 §1): the counting probe of the composition test Ambiente. A ByteBuddy agent adds an exit
 * advice to the constructors of every `snastro.*` class (production classes untouched on disk): while [durante]
 * runs, each object constructed ON THAT THREAD is recorded with its constructor arguments. The per-project graph is
 * built synchronously by `SessioneProgettoImpl.crea/apri` (`apriProgetto`, on the caller's thread), so one
 * [durante] around an open sees every constructor the open runs.
 *
 * - [Costruzioni.istanze] answers "how many times did `X(` run" (distinct instances, so a secondary / default-args
 *   constructor delegating to the primary one — two exits, one object — counts once);
 * - [Costruzioni.argomenti] answers "which instance did each consumer receive" (AC-C61's ===).
 */
internal object SondaCostruzioni {
    private val registro = ThreadLocal<MutableList<Costruzione>?>()
    private val rientro = ThreadLocal.withInitial { false }

    /** One observed constructor exit: the object built and the arguments that constructor received. */
    class Costruzione(val istanza: Any, val argomenti: List<Any?>)

    /** Everything [durante] observed, deduplicated by identity. */
    class Costruzioni(private val registrate: List<Costruzione>) {
        /** The distinct instances of the class named [classe] (fully-qualified) built during the observed block. */
        fun istanze(classe: String): List<Any> {
            val viste = IdentityHashMap<Any, Unit>()
            registrate.filter { it.istanza.javaClass.name == classe }.forEach { viste[it.istanza] = Unit }
            return viste.keys.toList()
        }

        /** Every argument of class [classe] handed to a constructor of class [consumatore] (both fully-qualified). */
        fun argomenti(consumatore: String, classe: String): List<Any> = registrate
            .filter { it.istanza.javaClass.name == consumatore }
            .flatMap { it.argomenti }
            .filterNotNull()
            .filter { it.javaClass.name == classe }

        /** Every observed construction of a class whose fully-qualified name satisfies [filtro], in order. */
        fun di(filtro: (String) -> Boolean): List<Costruzione> = registrate.filter { filtro(it.istanza.javaClass.name) }

        /** Every argument of class [classe] (fully-qualified) handed to ANY observed constructor. */
        fun argomentiOvunque(classe: String): List<Any> = registrate
            .flatMap { it.argomenti }
            .filterNotNull()
            .filter { it.javaClass.name == classe }
    }

    fun <T> durante(blocco: () -> T): Pair<T, Costruzioni> {
        installa
        val registrate = mutableListOf<Costruzione>()
        registro.set(registrate)
        try {
            return blocco() to Costruzioni(registrate.toList())
        } finally {
            registro.remove()
        }
    }

    /** Called by the inlined advice at every `snastro.*` constructor exit. */
    @JvmStatic
    fun registra(istanza: Any, argomenti: Array<Any?>) {
        val registrate = registro.get() ?: return
        if (rientro.get()) return
        rientro.set(true)
        try {
            registrate += Costruzione(istanza, argomenti.toList())
        } finally {
            rientro.set(false)
        }
    }

    private val installa: Unit by lazy {
        AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            // Retransforming an already-loaded class makes the verifier load the classes it refers to, on this thread
            // while the transformer is busy: those are skipped by the circularity lock, so discovery reiterates.
            .with(AgentBuilder.RedefinitionStrategy.DiscoveryStrategy.Reiterating.INSTANCE)
            // A class the agent could not instrument would silently read as "0 constructions": say so.
            .with(AgentBuilder.Listener.StreamWriting.toSystemError().withErrorsOnly())
            // The probe itself is never instrumented (registra would observe its own bookkeeping).
            .ignore(
                nameStartsWith<TypeDescription>("snastro.avvio.progetto.Sonda")
                    .or(named(UscitaCostruttore::class.java.name)),
            )
            .type(nameStartsWith<TypeDescription>("snastro.").and(not(isSynthetic())))
            .transform { builder, _, _, _, _ ->
                builder.visit(Advice.to(UscitaCostruttore::class.java).on(isConstructor()))
            }
            .installOn(ByteBuddyAgent.install())
        Unit
    }
}

/** The advice inlined into every instrumented constructor: it only forwards to [SondaCostruzioni.registra]. */
internal object UscitaCostruttore {
    @JvmStatic
    @Advice.OnMethodExit
    fun esci(@Advice.This istanza: Any, @Advice.AllArguments argomenti: Array<Any?>) {
        SondaCostruzioni.registra(istanza, argomenti)
    }
}
