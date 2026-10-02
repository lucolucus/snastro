package snastro.avvio.progetto

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.coda.Campanello
import snastro.avvio.parlanti.ModuloParlanti
import snastro.avvio.sintesi.ModuloSintesi
import snastro.avvio.trascrizione.ModuloTrascrizione
import snastro.kernel.AbbonatoSincrono
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.progetto.applicazione.comandi.Destinazione
import snastro.progetto.applicazione.comandi.EliminaRegistrazione
import snastro.progetto.applicazione.comandi.EliminaRegistrazioneServizio
import snastro.progetto.applicazione.eventi.RegistrazioneEliminata
import snastro.progetto.applicazione.porte.SondaAudioFinta
import snastro.trascrizione.applicazione.eventi.TrascrittoEliminato
import snastro.ui.registrazione.ComandoVoce
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ADR 0038 end to end on the REAL single composition over a SQLite project file (AC-I85..AC-I87): the deleting
 * transaction of a Parte of a two-Parti Incontro, its row counts read through the SQL adapters.
 */
class EliminaParteIncontroTest {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `AC-I85 Parte non ultima toglie impronte e Attribuzioni delle Voci svuotate, tiene Incontro e Riassunto`() {
        AmbienteProgetto(radice, estrattore = EstrattoreConMutex()).use {
            val s = preparaDueParti(it)
            val vociSvuotate = vociDi(it, s.a).toSet()
            val vociSuperstiti = vociDi(it, s.b).toSet()
            val tutte = it.porte.trascritti.trova(s.incontro)!!.voci.size
            assertTrue(vociSvuotate.isNotEmpty(), "$vociSvuotate")
            assertTrue(vociSvuotate.intersect(vociSuperstiti).isEmpty(), "$vociSuperstiti")
            val prima = conteggi(it, s.incontro)
            assertEquals(2, prima.getValue("attribuzione"), "$prima")
            assertEquals(2, prima.getValue("impronta_vocale"), "$prima")
            assertEquals(1, prima.getValue("riassunto"))

            it.collaboratori.eliminaRegistrazione(EliminaRegistrazione(s.a)).atteso()

            val dopo = conteggi(it, s.incontro)
            assertEquals(1, dopo.getValue("incontro"))
            assertEquals(1, dopo.getValue("voci_incontro"))
            assertEquals(tutte - vociSvuotate.size, dopo.getValue("voce_incontro"))
            assertEquals(1, dopo.getValue("attribuzione"), "resta solo quella della Parte che resta")
            assertEquals(1, dopo.getValue("impronta_vocale"), "resta solo l'impronta della Parte che resta")
            assertEquals(1, dopo.getValue("riassunto"), "il Riassunto resta")
            val rimaste = it.porte.attribuzioni.diIncontro(s.incontro).map { a -> a.voceRef.voceId }
            assertTrue(rimaste.none { v -> v in vociSvuotate }, "$rimaste")
            val vista = checkNotNull(it.sintesi.vista(s.b))
            assertTrue(vista.mostrato?.superato == true, "il Riassunto e' ora superato: ${vista.mostrato}")
        }
    }

    @Test
    fun `AC-I86 eliminare l ultima Parte non lascia riga dell Incontro in nessuna tabella`() {
        AmbienteProgetto(radice, estrattore = EstrattoreConMutex()).use {
            val s = preparaDueParti(it)
            it.collaboratori.eliminaRegistrazione(EliminaRegistrazione(s.b)).atteso()
            it.collaboratori.eliminaRegistrazione(EliminaRegistrazione(s.a)).atteso()

            val dopo = conteggi(it, s.incontro)
            TABELLE.forEach { t -> assertEquals(0, dopo.getValue(t), "$t: $dopo") }
        }
    }

    /**
     * Each missing synchronous subscriber makes the deleting transaction fail, and for ITS OWN reason (ADR 0038):
     * Sintesi's `riassunto` and Trascrizione's `voci_incontro` FKs are immediate (the DELETE statement fails), the
     * Parlanti ones are deferred (the COMMIT fails). Nothing changes (rollback) and the complete deletion then works.
     */
    @Test
    fun `AC-I86 senza il sottoscrittore di Sintesi, Trascrizione o la purga Parlanti l eliminazione fallisce`() {
        SOTTOSCRITTORI.forEach { (mancante, alCommit) ->
            AmbienteProgetto(
                radice.resolve(mancante).also { d -> d.toFile().mkdirs() },
                estrattore = EstrattoreConMutex(),
            ).use {
                val s = preparaDueParti(it)
                it.collaboratori.eliminaRegistrazione(EliminaRegistrazione(s.b)).atteso()
                val prima = conteggi(it, s.incontro)

                val esito = runCatching { eliminaSenza(it, mancante, s.a) }

                val errore = assertNotNull(esito.exceptionOrNull(), "$mancante: l'eliminazione e' riuscita: $esito")
                assertEquals("SQLiteException", causaRadice(errore)::class.simpleName, "$mancante: $errore")
                assertEquals(alCommit, fallitaAlCommit(errore), "$mancante: ${if (alCommit) "COMMIT" else "statement"}")
                assertEquals(prima, conteggi(it, s.incontro), "$mancante: nulla e' cambiato (rollback)")
                it.collaboratori.eliminaRegistrazione(EliminaRegistrazione(s.a)).atteso() // la completa riesce
                assertEquals(0, conteggi(it, s.incontro).getValue("incontro"))
            }
        }
    }

    @Test
    fun `AC-I87 ordine sincrono Sintesi, Parlanti, Trascrizione e Parlanti non ascolta RegistrazioneEliminata`() {
        AmbienteProgetto(radice).use {
            assertEquals(
                listOf(ModuloSintesi::class, ModuloParlanti::class, ModuloTrascrizione::class),
                it.composto.ordineSincroni.map { m -> m::class },
            )
            val parlanti = it.composto.ordineSincroni.single { m -> m is ModuloParlanti }.abbonatiSincroni()
            assertFalse(parlanti.any { a -> a.evento == RegistrazioneEliminata::class })
            assertTrue(parlanti.any { a -> a.evento == TrascrittoEliminato::class })
        }
    }

    /**
     * The real deleting service over a SECOND, self-consistent composition of the same database (its own unit of work,
     * dispatcher and modules), holding every synchronous subscriber BUT the one of [senza] (a subscriber class name).
     * Never the production dispatcher: that holds them all, and a half-wired one over another unit of work would fail
     * for the wrong reason.
     */
    private fun eliminaSenza(ambiente: AmbienteProgetto, senza: String, id: RegistrazioneId): Esito<Unit> {
        val cartella = Path.of(ambiente.progetto.percorso)
        val porte = PorteProgetto(ambiente.porte.database, ambiente.clock, cartella.toFile())
        val apertura = AperturaProgetto(
            ambiente.progetto.progettoId,
            cartella,
            ambiente.scope,
            ambiente.collaboratori.lettoreAudio,
            GeneratoreIdFinto(),
            ambiente.clock,
            SondaAudioFinta(emptyMap()),
        )
        val trascrizione = ModuloTrascrizione(porte, apertura, ambiente.app, Campanello())
        val moduli = listOf(
            ModuloSintesi(porte, apertura, ambiente.app, Campanello()),
            ModuloParlanti(porte, apertura, ambiente.app, trascrizione.collaboratori),
            trascrizione,
        )
        moduli.flatMap { m -> m.abbonatiSincroni() }
            .filter { a -> a.abbonato::class.simpleName != senza }
            .forEach { a ->
                porte.dispatcher.registraSincrono(
                    object : AbbonatoSincrono {
                        override fun ricevi(evento: EventoPubblicato): Esito<Unit> =
                            if (a.evento.isInstance(evento)) a.abbonato.ricevi(evento) else Esito.Ok(Unit)
                    },
                )
            }
        return EliminaRegistrazioneServizio(
            porte.unitaDiLavoro,
            porte.registrazioni,
            porte.incontri,
            porte.eliminazioniInSospeso,
            porte.dispatcher,
        ).esegui(EliminaRegistrazione(id))
    }

    private fun causaRadice(e: Throwable): Throwable = generateSequence(e) { it.cause }.last()

    /** True when the failure came out of the transaction's COMMIT (a deferred FK), not of a statement before it. */
    private fun fallitaAlCommit(e: Throwable): Boolean = generateSequence(e) { it.cause }
        .flatMap { it.stackTrace.asSequence() }
        .any { f -> f.methodName == "endTransaction" } // the driver runs COMMIT there

    private class Scenario(val incontro: IncontroId, val a: RegistrazioneId, val b: RegistrazioneId)

    /**
     * An Incontro of two transcribed Parti, [Scenario.a] first: its Riassunto is made while [Scenario.a] is alone, then
     * [Scenario.b] joins, so the Riassunto is `superato`. Each Parte has one Voce identified through the real command.
     */
    private fun preparaDueParti(ambiente: AmbienteProgetto): Scenario {
        val a = ambiente.importa()
        val incontro = ambiente.incontroDi(a)
        ambiente.trascrivi(a)
        ambiente.riassumi(a)
        ambiente.attendiPronto(a)
        val prima = ambiente.collaboratori.registrazioni().map { r -> r.registrazioneId }.toSet()
        ambiente.importaIn(Destinazione.Incontro(incontro)).atteso()
        val b = ambiente.collaboratori.registrazioni().map { r -> r.registrazioneId }.single { r -> r !in prima }
        ambiente.rendiLeggibile(b)
        ambiente.trascrivi(b)
        identifica(ambiente, "Anna", VoceRef(incontro, vociDi(ambiente, a).first()))
        identifica(ambiente, "Berta", VoceRef(incontro, vociDi(ambiente, b).first()))
        return Scenario(incontro, a, b)
    }

    private fun vociDi(ambiente: AmbienteProgetto, parte: RegistrazioneId): List<VoceId> =
        ambiente.porte.trascritti.trascritto(parte)!!.segmenti.map { sg -> sg.voceId }.distinct()
            .sortedBy { v -> v.numero }

    private fun identifica(ambiente: AmbienteProgetto, nome: String, voce: VoceRef) {
        assertEquals(Esito.Ok(Unit), runBlocking { ambiente.parlanti.comandi.esegui(ComandoVoce.Nuovo(voce, nome)) })
    }

    /**
     * Row counts of the Incontro's tables, through the SQL adapters. `riassunto_elemento` counts the Elementi of the
     * Riassunti found (their `riassunto_fonte` rows hang off them by cascade: none without the root).
     */
    private fun conteggi(ambiente: AmbienteProgetto, incontro: IncontroId): Map<String, Int> {
        val porte = ambiente.porte
        val radice = porte.trascritti.trova(incontro)
        val riassunti = porte.riassunti.trova(incontro)
        return mapOf(
            "incontro" to listOfNotNull(porte.incontri.trova(incontro)).size,
            "voci_incontro" to listOfNotNull(radice).size,
            "voce_incontro" to (radice?.voci?.size ?: 0),
            "attribuzione" to porte.attribuzioni.diIncontro(incontro).size,
            "impronta_vocale" to porte.parlanti.impronteDelProgetto(ambiente.progetto.progettoId).size,
            "riassunto" to riassunti.size,
            "riassunto_elemento" to riassunti.sumOf { r ->
                r.decisioni.size + r.questioniAperte.size + r.azioni.size + r.puntiChiave.size
            },
            "registrazione" to ambiente.collaboratori.registrazioni().size,
        )
    }

    private companion object {
        /** The subscriber class and whether its absence fails at COMMIT (deferred FK) or at a statement (immediate). */
        val SOTTOSCRITTORI = listOf(
            "AbbonatoProgettoSintesi" to false,
            "AbbonatoEliminazioneRegistrazione" to false,
            "AbbonatoRevisioneParlanti" to true,
        )
        val TABELLE = listOf(
            "incontro",
            "voci_incontro",
            "voce_incontro",
            "attribuzione",
            "impronta_vocale",
            "riassunto",
            "riassunto_elemento",
            "registrazione",
        )
    }
}
