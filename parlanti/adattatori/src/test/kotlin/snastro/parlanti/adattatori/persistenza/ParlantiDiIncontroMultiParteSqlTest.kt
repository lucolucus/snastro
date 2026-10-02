package snastro.parlanti.adattatori.persistenza

import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.parlanti.adattatori.eventi.AbbonatoRevisioneParlanti
import snastro.parlanti.applicazione.politiche.ApplicaRevisionePolitica
import snastro.parlanti.applicazione.politiche.ApplicaSostituzioneTrascrittoPolitica
import snastro.parlanti.applicazione.porte.LettoreVociFinta
import snastro.parlanti.dominio.Attribuzione
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.TipoParlante
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.databaseInMemoria
import snastro.persistenza.seminaTrascrittoDiProva
import snastro.persistenza.seminaVoceDiProva
import snastro.progetto.applicazione.eventi.RegistrazioneEliminata
import snastro.trascrizione.applicazione.eventi.TrascrittoEliminato
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parlanti's SQL persistence over an Incontro of TWO Parti on the real SQLite schema: AC-I60 (a print per
 * (Voce, Parte), the second deferred FK) and the ADR 0038 §2 composition (the purge a deleted Parte triggers, in
 * row counts). Parent rows are seeded with the generated queries like the Trascrizione adapter's own test does.
 */
class ParlantiDiIncontroMultiParteSqlTest {
    private val db: SnastroDatabase = databaseInMemoria()
    private val unita = UnitaDiLavoroSql(db)
    private val parlanti = ParlanteRepositorySql(db, unita)
    private val attribuzioni = AttribuzioneRepositorySql(db)

    private fun seminaIncontro(partiConVoci: Map<String, List<Long>>, partiSenzaVoci: List<String> = emptyList()) {
        db.progettoQueries.inserisci(PROGETTO.valore, "Progetto di prova")
        db.incontroQueries.inserisci(id = INCONTRO.valore, progettoId = PROGETTO.valore)
        (partiConVoci.keys + partiSenzaVoci).forEach { parte ->
            db.registrazioneQueries.inserisci(
                id = parte,
                progettoId = PROGETTO.valore,
                incontroId = INCONTRO.valore,
                titolo = parte,
                riferimentoAudio = "audio/$parte.wav",
                durataMs = 600_000L,
                dataRegistrazione = "2026-09-23",
                aggiuntaAlle = 0L,
                oraDiInizio = null,
            )
            db.seminaTrascrittoDiProva(registrazioneId = parte)
        }
        partiConVoci.forEach { (parte, voci) -> voci.forEach { db.seminaVoceDiProva(parte, it) } }
    }

    private fun parlante(id: String, tipo: TipoParlante): Parlante =
        Parlante.crea(ParlanteId(id), PROGETTO, Nome.di(id).atteso(), tipo).aggregato

    private fun stampa(p: Parlante, voce: Long, parte: String) {
        val impronta = Impronta(floatArrayOf(1f))
        val ref = VoceRef(INCONTRO, VoceId(voce.toInt()))
        p.aggiungiImpronta(ref, RegistrazioneId(parte), impronta, "0-1000", "m").atteso()
    }

    @Test
    fun `AC-I60 due impronte della stessa Voce in due Parti fanno il giro e restano due righe`() {
        seminaIncontro(mapOf(P1 to listOf(1L), P2 to listOf(1L)))
        val marco = parlante("marco", TipoParlante.RICORRENTE)
        stampa(marco, 1, P1)
        stampa(marco, 1, P2)

        unita.inTransazione { parlanti.salva(marco) }.atteso()

        val letto = assertNotNull(parlanti.trova(marco.id))
        assertEquals(setOf(P1, P2), letto.impronte.map { it.parte.valore }.toSet())
        assertEquals(2, letto.impronte.count { it.voceRef == VoceRef(INCONTRO, VoceId(1)) })
        assertEquals(2, db.improntaVocaleQueries.trovaDiParlante(marco.id.valore).executeAsList().size)
    }

    @Test
    fun `AC-I60 un'impronta la cui fetta (Voce, Parte) non esiste fa fallire il COMMIT sulla seconda FK differita`() {
        // voce_incontro (incontro, 1) exists through P1, voce (P2, 1) does not
        seminaIncontro(mapOf(P1 to listOf(1L)), partiSenzaVoci = listOf(P2))
        val marco = parlante("marco", TipoParlante.RICORRENTE)
        stampa(marco, 1, P2)

        val errore = assertFails { unita.inTransazione { parlanti.salva(marco) } }

        assertTrue(errore.message.orEmpty().contains("FOREIGN KEY"), "il COMMIT cade sulla FK differita: $errore")
    }

    @Test
    fun `ADR 0038 eliminare una Parte non ultima purga le sue impronte e le Voci cessate, tiene il resto`() {
        seminaIncontro(mapOf(P1 to listOf(1L), P2 to listOf(1L, 2L)))
        val mario = parlante("mario", TipoParlante.RICORRENTE)
        val ospite = parlante("ospite", TipoParlante.OCCASIONALE)
        stampa(mario, 1, P1)
        stampa(mario, 1, P2)
        stampa(ospite, 2, P2)
        unita.inTransazione {
            parlanti.salva(mario).atteso()
            parlanti.salva(ospite)
        }.atteso()
        unita.inTransazione {
            attribuisci(1, mario)
            attribuisci(2, ospite)
            Esito.Ok(Unit)
        }.atteso()
        assertEquals(3, impronteTotali())
        assertEquals(2, attribuzioni.diIncontro(INCONTRO).size)
        val dispatcher = dispatcher()

        // The Trascrizione deleting unit of the NON-last Parte P2 ends only Voce 2 (Voce 1 lives on in P1).
        dispatcher.unitaDiLavoro.inTransazione {
            dispatcher.pubblica(TrascrittoEliminato(RegistrazioneId(P2), INCONTRO, setOf(VoceId(2))))
            Esito.Ok(Unit)
        }.atteso()

        assertEquals(1, impronteTotali(), "resta solo l'impronta di Mario nella Parte 1")
        assertEquals(listOf(P1), parlanti.impronteDiRegistrazione(RegistrazioneId(P1)).map { it.parte.valore })
        assertEquals(listOf(VoceId(1)), attribuzioni.diIncontro(INCONTRO).map { it.voceRef.voceId }, "Voce 1 resta")
        assertNull(parlanti.trova(ospite.id), "INV-25: l'occasionale senza Attribuzioni cessa")
        assertNotNull(parlanti.trova(mario.id))
    }

    @Test
    fun `ADR 0038 eliminare l'ultima Parte purga ogni impronta e Attribuzione dell'Incontro cessato`() {
        seminaIncontro(mapOf(P1 to listOf(1L, 2L)))
        val mario = parlante("mario", TipoParlante.RICORRENTE)
        val ospite = parlante("ospite", TipoParlante.OCCASIONALE)
        stampa(mario, 1, P1)
        stampa(ospite, 2, P1)
        unita.inTransazione {
            parlanti.salva(mario).atteso()
            parlanti.salva(ospite)
        }.atteso()
        unita.inTransazione {
            attribuisci(1, mario)
            attribuisci(2, ospite)
            Esito.Ok(Unit)
        }.atteso()
        val dispatcher = dispatcher()

        // The deleting unit of the LAST Parte ends every Voce of the Incontro.
        dispatcher.unitaDiLavoro.inTransazione {
            dispatcher.pubblica(TrascrittoEliminato(RegistrazioneId(P1), INCONTRO, setOf(VoceId(1), VoceId(2))))
            Esito.Ok(Unit)
        }.atteso()

        assertEquals(0, impronteTotali(), "nessuna impronta resta")
        assertEquals(emptyList(), attribuzioni.diIncontro(INCONTRO), "nessuna Attribuzione resta")
        assertNull(parlanti.trova(ospite.id), "INV-25: l'occasionale senza Attribuzioni cessa")
        assertNotNull(parlanti.trova(mario.id), "il ricorrente resta, senza impronte")
    }

    @Test
    fun `AC-I61 RegistrazioneEliminata pubblicato non cancella nessuna Attribuzione dell'Incontro`() {
        seminaIncontro(mapOf(P1 to listOf(1L), P2 to listOf(2L)))
        val mario = parlante("mario", TipoParlante.RICORRENTE)
        stampa(mario, 1, P1)
        unita.inTransazione { parlanti.salva(mario) }.atteso()
        val dispatcher = dispatcher()
        dispatcher.unitaDiLavoro.inTransazione {
            attribuisci(1, mario)
            dispatcher.pubblica(
                RegistrazioneEliminata(
                    registrazioneId = RegistrazioneId(P2),
                    progettoId = PROGETTO,
                    titolo = "P2",
                    dataRegistrazione = LocalDate.of(2026, 9, 23),
                    riferimentoAudio = RiferimentoAudio("audio/p2.wav"),
                    incontroId = INCONTRO,
                    incontroCessato = false,
                ),
            )
            Esito.Ok(Unit)
        }.atteso()

        assertEquals(1, attribuzioni.diIncontro(INCONTRO).size)
        assertEquals(1, impronteTotali())
    }

    private fun attribuisci(voce: Int, p: Parlante) {
        val ref = VoceRef(INCONTRO, VoceId(voce))
        attribuzioni.salva(Attribuzione.conferma(ref, PROGETTO, p.id).aggregato)
    }

    private fun dispatcher() = DispatcherEventiInMemoria(unita).also {
        it.registraSincrono(
            AbbonatoRevisioneParlanti(
                ApplicaRevisionePolitica(parlanti, attribuzioni, LettoreVociFinta()),
                ApplicaSostituzioneTrascrittoPolitica(parlanti, attribuzioni),
            ),
        )
    }

    private fun impronteTotali(): Int = db.improntaVocaleQueries.trovaDiParlante("mario").executeAsList().size +
        db.improntaVocaleQueries.trovaDiParlante("ospite").executeAsList().size +
        db.improntaVocaleQueries.trovaDiParlante("marco").executeAsList().size

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val INCONTRO = IncontroId("incontro-1")
        const val P1 = "p1"
        const val P2 = "p2"
    }
}
