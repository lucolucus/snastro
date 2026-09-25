package snastro.avvio.r2

import org.junit.jupiter.api.io.TempDir
import snastro.avvio.orologioApp
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.Esito
import snastro.kernel.GeneratoreIdUuid
import snastro.kernel.RegistrazioneId
import snastro.parlanti.adattatori.eventi.AbbonatoRevisioneParlanti
import snastro.parlanti.adattatori.persistenza.AttribuzioneRepositorySql
import snastro.parlanti.adattatori.persistenza.ParlanteRepositorySql
import snastro.parlanti.applicazione.politiche.ApplicaRevisionePolitica
import snastro.parlanti.applicazione.politiche.ApplicaSostituzioneTrascrittoPolitica
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.apriDatabaseProgetto
import snastro.progetto.adattatori.persistenza.EliminazioniInSospesoSql
import snastro.progetto.adattatori.persistenza.RegistrazioneRepositorySql
import snastro.progetto.applicazione.comandi.EliminaRegistrazione
import snastro.progetto.applicazione.comandi.EliminaRegistrazioneServizio
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.trascrizione.adattatori.eventi.AbbonatoEliminazioneRegistrazione
import snastro.trascrizione.adattatori.persistenza.ElaborazioneRepositorySql
import snastro.trascrizione.adattatori.persistenza.TrascrittoRepositorySql
import snastro.trascrizione.adattatori.porte.LettoreRegistrazioneDaProgetto
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.comandi.AvviaElaborazioneServizio
import snastro.trascrizione.applicazione.politiche.ApplicaEliminazioneRegistrazionePolitica
import snastro.trascrizione.dominio.ErroreTrascrizione
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AC-636 (INV-28, ADR 0020 §2 "race safety"): `EliminaRegistrazione(R)` against `AvviaElaborazione(R)` on a real
 * SQLite FILE (the production `apriDatabaseProgetto`: WAL, `BEGIN IMMEDIATE`, FKs), two threads over ONE
 * `UnitaDiLavoroSql` (a connection per thread) started on a barrier, 100 times, with the two synchronous
 * subscribers the R2 composition registers. Exactly one of the two outcomes, never both, never an exception.
 */
class EliminaRegistrazioneCorsaTest {
    @TempDir
    lateinit var cartella: Path

    @Test
    fun `AC-636 eliminazione contro avvio, 100 volte, sempre uno solo dei due esiti`() {
        val aperto = apriDatabaseProgetto(cartella.toFile())
        val db = aperto.database
        val registrazioni = RegistrazioneRepositorySql(db)
        val elaborazioni = ElaborazioneRepositorySql(db)
        val (elimina, avvia) = comandi(db, registrazioni, elaborazioni)
        db.progettoQueries.inserisci(PROGETTO, "Progetto di prova")
        val esecutore = Executors.newFixedThreadPool(2)
        try {
            val esiti = (1..RIPETIZIONI).map { n ->
                val id = RegistrazioneId("r-$n")
                db.registrazioneQueries.inserisci(
                    id = id.valore,
                    progettoId = PROGETTO,
                    titolo = "R $n",
                    riferimentoAudio = "audio/r-$n.wav",
                    durataMs = 1_000L,
                    dataRegistrazione = "2026-09-25",
                    aggiuntaAlle = 0L,
                )
                val barriera = CyclicBarrier(2)
                val eliminazione = esecutore.submit(
                    Callable {
                        barriera.await()
                        elimina.esegui(EliminaRegistrazione(id))
                    },
                )
                val avvio = esecutore.submit(
                    Callable {
                        barriera.await()
                        avvia.esegui(AvviaElaborazione(id))
                    },
                )
                val esito = classifica(
                    eliminazione.get(ATTESA_S, TimeUnit.SECONDS),
                    avvio.get(ATTESA_S, TimeUnit.SECONDS),
                    registrazioni.trova(id) != null,
                    elaborazioni.diRegistrazione(id).map { it.stato.name },
                )
                "$n: $esito"
            }
            assertEquals(emptyList(), esiti.filter { "ANOMALO" in it })
        } finally {
            esecutore.shutdownNow()
            aperto.chiudi()
        }
    }

    /** The two commands over ONE dispatcher with the two synchronous subscribers the R2 composition registers. */
    private fun comandi(
        db: SnastroDatabase,
        registrazioni: RegistrazioneRepositorySql,
        elaborazioni: ElaborazioneRepositorySql,
    ): Pair<EliminaRegistrazioneServizio, AvviaElaborazioneServizio> {
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroSql(db))
        val uow = dispatcher.unitaDiLavoro
        val parlanti = ParlanteRepositorySql(db)
        val attribuzioni = AttribuzioneRepositorySql(db)
        AbbonatoEliminazioneRegistrazione(
            dispatcher,
            ApplicaEliminazioneRegistrazionePolitica(elaborazioni, TrascrittoRepositorySql(db)),
        )
        AbbonatoRevisioneParlanti(
            dispatcher,
            ApplicaRevisionePolitica(parlanti, attribuzioni),
            ApplicaSostituzioneTrascrittoPolitica(parlanti, attribuzioni),
        )
        val elimina = EliminaRegistrazioneServizio(
            uow,
            registrazioni,
            EliminazioniInSospesoSql(db, orologioApp()),
            dispatcher,
        )
        val avvia = AvviaElaborazioneServizio(
            uow,
            GeneratoreIdUuid(),
            orologioApp(),
            LettoreRegistrazioneDaProgetto(CatalogoRegistrazioni(registrazioni)),
            elaborazioni,
        )
        return elimina to avvia
    }

    private fun classifica(
        eliminazione: Esito<Unit>,
        avvio: Esito<Unit>,
        registrazionePresente: Boolean,
        statiElaborazioni: List<String>,
    ): String {
        val eliminata = eliminazione is Esito.Ok &&
            (avvio as? Esito.Errore)?.errore is ErroreTrascrizione.RegistrazioneNonTrovata &&
            !registrazionePresente && statiElaborazioni.isEmpty()
        val avviata = avvio is Esito.Ok &&
            (eliminazione as? Esito.Errore)?.errore is ErroreTrascrizione.ElaborazioneGiaAperta &&
            registrazionePresente && statiElaborazioni == listOf("IN_ATTESA")
        return when {
            eliminata -> "eliminata"
            avviata -> "avviata"
            else -> "ANOMALO eliminazione=$eliminazione avvio=$avvio presente=$registrazionePresente $statiElaborazioni"
        }
    }

    private companion object {
        const val RIPETIZIONI = 100
        const val ATTESA_S = 30L
        const val PROGETTO = "progetto-1"
    }
}
