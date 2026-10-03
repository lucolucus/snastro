package snastro.avvio.progetto

import snastro.avvio.Abbonamento
import snastro.avvio.ModuloComposizione
import snastro.avvio.parlanti.ModuloParlanti
import snastro.avvio.parlanti.PropostaTraPartiProgetto
import snastro.kernel.AbbonatoDopoCommit
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.trascrizione.applicazione.eventi.ElaborazioneCompletata
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AC-I49 / L212: a priority after-commit subscriber (a cache invalidation) of ANY module runs before the ordinary
 * subscribers of EVERY module, so an earlier module reacting to the same event (an S3 reload) never reads a stale
 * cache.
 */
class RegistraAbbonatiOrdineTest {
    private class Modulo(
        val ordinari: List<Abbonamento<AbbonatoDopoCommit>> = emptyList(),
        val prioritari: List<Abbonamento<AbbonatoDopoCommit>> = emptyList(),
    ) : ModuloComposizione {
        override fun abbonatiDopoCommit() = ordinari
        override fun abbonatiDopoCommitPrioritari() = prioritari
    }

    @Test
    fun `l'invalidazione di un modulo tardivo precede gli abbonati di un modulo precedente sullo stesso evento`() {
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
        val visti = mutableListOf<String>()
        fun abbonato(nome: String) = Abbonamento(ElaborazioneCompletata::class, AbbonatoDopoCommit { visti += nome })
        val presto = Modulo(ordinari = listOf(abbonato("ricarica-schermata")))
        val tardi = Modulo(prioritari = listOf(abbonato("invalida-cache")))

        registraAbbonati(dispatcher, sincroni = emptyList(), dopoCommit = listOf(presto, tardi))
        dispatcher.unitaDiLavoro.inTransazione {
            dispatcher.pubblica(ElaborazioneCompletata(RegistrazioneId("r"), IncontroId("i")) as EventoPubblicato)
            Esito.Ok(Unit)
        }

        assertEquals(listOf("invalida-cache", "ricarica-schermata"), visti)
    }

    @Test
    fun `L255 l'invalidazione precede la ricarica di un evento precedente dello stesso commit`() {
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
        val visti = mutableListOf<String>()
        val presto = Modulo(
            ordinari = listOf(
                Abbonamento(ElaborazioneCompletata::class, AbbonatoDopoCommit { visti += "ricarica-schermata" }),
            ),
        )
        val invalida = Abbonamento(Invalidante::class, AbbonatoDopoCommit { visti += "invalida" })
        val tardi = Modulo(prioritari = listOf(invalida))

        registraAbbonati(dispatcher, sincroni = emptyList(), dopoCommit = listOf(presto, tardi))
        dispatcher.unitaDiLavoro.inTransazione {
            dispatcher.pubblica(ElaborazioneCompletata(RegistrazioneId("r"), IncontroId("i")) as EventoPubblicato)
            dispatcher.pubblica(Invalidante)
            Esito.Ok(Unit)
        }

        assertEquals(listOf("invalida", "ricarica-schermata"), visti)
    }

    private object Invalidante : EventoPubblicato

    @Test
    fun `in produzione l'invalidazione tra Parti e prioritaria, mai fra gli abbonati ordinari di Parlanti`() {
        val radice = Files.createTempDirectory("ordine-abbonati")
        AmbienteProgetto(radice).use {
            val parlanti = it.composto.ordineDopoCommit.filterIsInstance<ModuloParlanti>().single()
            val prioritari = parlanti.abbonatiDopoCommitPrioritari().map { a -> a.evento }
            assertEquals(PropostaTraPartiProgetto.EVENTI_INVALIDANTI.toSet(), prioritari.toSet())
            val invalidante = parlanti.abbonatiDopoCommitPrioritari().first().abbonato
            assertTrue(parlanti.abbonatiDopoCommit().none { a -> a.abbonato === invalidante })
            assertTrue(
                it.composto.ordineDopoCommit.filter { m -> m !is ModuloParlanti }
                    .all { m -> m.abbonatiDopoCommitPrioritari().isEmpty() },
            )
        }
    }
}
