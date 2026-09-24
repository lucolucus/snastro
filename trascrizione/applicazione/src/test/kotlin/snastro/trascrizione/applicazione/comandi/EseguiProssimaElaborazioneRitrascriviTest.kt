package snastro.trascrizione.applicazione.comandi

import snastro.kernel.CampioniAudio
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.ElaborazioneId
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.IntervalloMs
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.trascrizione.applicazione.eventi.ElaborazioneAvviata
import snastro.trascrizione.applicazione.eventi.ElaborazioneCompletata
import snastro.trascrizione.applicazione.eventi.ElaborazioneFallita
import snastro.trascrizione.applicazione.eventi.TrascrittoSostituito
import snastro.trascrizione.applicazione.porte.Allineatore
import snastro.trascrizione.applicazione.porte.AllineatoreFinta
import snastro.trascrizione.applicazione.porte.DecodificatoreAudio
import snastro.trascrizione.applicazione.porte.DecodificatoreAudioFinta
import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione
import snastro.trascrizione.applicazione.porte.LettoreRegistrazioneFinta
import snastro.trascrizione.applicazione.porte.RegistrazioneVista
import snastro.trascrizione.applicazione.porte.SegmentoGrezzo
import snastro.trascrizione.applicazione.porte.SegnalatoreFaseFinta
import snastro.trascrizione.applicazione.porte.TrascrittoRepositoryFinta
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.dominio.Elaborazione
import snastro.trascrizione.dominio.NumeroPersone
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.trascrizione.dominio.Trascritto
import snastro.trascrizione.dominio.unTrascritto
import snastro.trascrizione.dominio.unaElaborazione
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REWORK ADR 0018 (+ Amendment (b)): a completion over an existing Trascritto replaces it whole in the SAME
 * transaction and publishes [TrascrittoSostituito] strictly before [ElaborazioneCompletata]; a first completion
 * and every failure leave things as before; a row cancelled while queued is never claimed. Split from
 * [EseguiProssimaElaborazioneServizioTest] (`LargeClass`).
 */
class EseguiProssimaElaborazioneRitrascriviTest {
    @Test
    fun `AC-437 un primo completamento pubblica ElaborazioneCompletata e mai TrascrittoSostituito`() {
        val s = Scenario()
        s.elaborazioni.salva(Elaborazione.accoda(RITRASCRIZIONE, R, DOPO, null).aggregato).atteso()

        s.servizio().esegui(EseguiProssimaElaborazione()).atteso()

        assertEquals(NUOVO, s.trascritti.trova(R)?.let(::forma))
        assertEquals(listOf(ElaborazioneAvviata(R, OROLOGIO.instant()), ElaborazioneCompletata(R)), s.eventi.pubblicati)
        assertEquals(listOf(ElaborazioneAvviata(R, OROLOGIO.instant()), ElaborazioneCompletata(R)), s.sincroni)
    }

    @Test
    fun `AC-438 sostituisce il Trascritto esistente intero in una transazione e TrascrittoSostituito precede`() {
        val s = Scenario().giaTrascritta()

        s.servizio().esegui(EseguiProssimaElaborazione()).atteso()

        assertEquals(2, s.contata.transazioni, "presa in carico + UNA transazione di completamento")
        assertEquals(NUOVO, s.trascritti.trova(R)?.let(::forma), "Voci da 1, contatori del nuovo crea")
        val attese = listOf(
            ElaborazioneAvviata(R, OROLOGIO.instant()),
            TrascrittoSostituito(R),
            ElaborazioneCompletata(R),
        )
        assertEquals(attese, s.eventi.pubblicati)
        assertEquals(attese, s.sincroni)
        assertEquals(listOf(1, 2, 2), s.transazioneDiOgniSincrono, "Sostituito e Completata nella stessa transazione")
        assertEquals(NUOVO, s.trascrittoAlSostituito, "il sincrono vede gia il nuovo Trascritto")
        assertTrue(s.elaborazioni.trova(RITRASCRIZIONE)?.completata == true)
        assertTrue(s.elaborazioni.trova(COMPLETATA_1)?.completata == true, "la completata precedente resta completata")
    }

    @Test
    fun `INV-5 ogni completata riscrive il Trascritto intero e nessun altro esito lo tocca`() {
        val completata = Scenario().giaTrascritta()
        completata.servizio().esegui(EseguiProssimaElaborazione()).atteso()
        assertEquals(NUOVO, completata.trascritti.trova(R)?.let(::forma))

        val fallita = Scenario().giaTrascritta()
        fallita.servizio(fallita.pipeline(allineatore = AllineatoreMuto())).esegui(EseguiProssimaElaborazione())
            .atteso()
        assertEquals(FORMA_VECCHIO, fallita.trascritti.trova(R)?.let(::forma))
    }

    @Test
    fun `AC-439 mentre la pipeline diarizza il vecchio Trascritto resta leggibile e nessuna transazione e aperta`() {
        val s = Scenario().giaTrascritta()
        val osservato = mutableListOf<Pair<FormaTrascritto?, Boolean>>()
        val diarizzatore = object : Diarizzatore {
            private val reale = DiarizzatoreFinta(TURNI)

            override fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno> {
                osservato += s.trascritti.trova(R)?.let(::forma) to s.uow.transazioneAperta
                return reale.diarizza(c, numeroPersone)
            }
        }

        s.servizio(s.pipeline(diarizzatore = diarizzatore)).esegui(EseguiProssimaElaborazione()).atteso()

        assertEquals(listOf<Pair<FormaTrascritto?, Boolean>>(FORMA_VECCHIO to false), osservato)
    }

    @Test
    fun `AC-440 una ritrascrizione fallita non pubblica TrascrittoSostituito e il vecchio Trascritto non cambia`() {
        val casi: Map<String, Scenario.() -> Unit> = mapOf(
            "lettura" to { esegui(pipeline(registrazioni = LettoreRegistrazioneFinta(emptyMap()))) },
            "decodifica" to { esegui(pipeline(decodificatore = DecodificatoreAudioFinta(emptyMap()))) },
            "diarizzazione" to { esegui(pipeline(diarizzatore = DiarizzatoreGuasto())) },
            "trascrizione" to { esegui(pipeline(allineatore = AllineatoreGuasto())) },
            "nessun parlato rilevato" to { esegui(pipeline(allineatore = AllineatoreMuto())) },
            "Trascritto.crea rifiuta" to { esegui(pipeline(allineatore = AllineatoreOltreLaDurata())) },
            "interrotta" to {
                val ritrascrizione = checkNotNull(elaborazioni.trova(RITRASCRIZIONE))
                ritrascrizione.avvia(DOPO).atteso()
                elaborazioni.salva(ritrascrizione).atteso()
                RecuperaElaborazioniInterrotteServizio(contata, elaborazioni, eventi)
                    .esegui(RecuperaElaborazioniInterrotte).atteso()
            },
        )

        casi.forEach { (caso, esegui) ->
            val s = Scenario().giaTrascritta()

            s.esegui()

            assertTrue(s.eventi.pubblicati.none { it is TrascrittoSostituito }, caso)
            assertTrue(s.eventi.pubblicati.none { it is ElaborazioneCompletata }, caso)
            assertEquals(FORMA_VECCHIO, s.trascritti.trova(R)?.let(::forma), "$caso: vecchio Trascritto invariato")
            assertTrue(s.elaborazioni.trova(RITRASCRIZIONE)?.fallita == true, caso)
            assertTrue(s.elaborazioni.trova(COMPLETATA_1)?.completata == true, "$caso: la completata resta")
        }
    }

    @Test
    fun `AC-441 un sincrono che rifiuta TrascrittoSostituito annulla tutto il completamento e compensa a fallita`() {
        val s = Scenario().giaTrascritta()
        s.eventi.registraSincrono { evento ->
            if (evento is TrascrittoSostituito) {
                Esito.Errore(ErroreDiProva.Fallito("pulizia Parlanti"))
            } else {
                Esito.Ok(Unit)
            }
        }

        s.servizio().esegui(EseguiProssimaElaborazione()).atteso()

        assertEquals(FORMA_VECCHIO, s.trascritti.trova(R)?.let(::forma), "vecchio Trascritto intatto")
        val fallita = checkNotNull(s.elaborazioni.trova(RITRASCRIZIONE))
        assertTrue(fallita.fallita)
        assertEquals("salvataggio del risultato non riuscito", fallita.motivoFallimento)
        assertEquals(
            listOf(
                ElaborazioneAvviata(R, OROLOGIO.instant()),
                ElaborazioneFallita(R, fallita.motivoFallimento.orEmpty()),
            ),
            s.eventi.pubblicati,
            "nessuna ElaborazioneCompletata arriva ai dopo-commit",
        )
        assertTrue(s.elaborazioni.trova(COMPLETATA_1)?.completata == true)
    }

    @Test
    fun `AC-469 un in_attesa rimossa da rimuoviInAttesa non parte mai e parte la successiva`() {
        val s = Scenario()
        s.elaborazioni.salva(Elaborazione.accoda(RITRASCRIZIONE, R, PRIMA, null).aggregato).atteso()
        s.elaborazioni.salva(Elaborazione.accoda(ElaborazioneId("successiva"), R2, DOPO, null).aggregato).atteso()
        s.elaborazioni.rimuoviInAttesa(RITRASCRIZIONE).atteso()

        val risultato = s.servizio().esegui(EseguiProssimaElaborazione()).atteso()

        assertEquals(RisultatoAvanzamento.Avviata(ElaborazioneId("successiva")), risultato)
        assertEquals(emptyList(), s.segnalatore.fasi(R), "nessuna porta invocata per la rimossa")
        assertEquals(listOf(R2), s.segnalatore.terminate)
        assertTrue(s.elaborazioni.trova(ElaborazioneId("successiva"))?.completata == true)
    }

    @Test
    fun `AC-469 se l unica in_attesa e stata rimossa il comando non ha effetti`() {
        val s = Scenario()
        s.elaborazioni.salva(Elaborazione.accoda(RITRASCRIZIONE, R, PRIMA, null).aggregato).atteso()
        s.elaborazioni.rimuoviInAttesa(RITRASCRIZIONE).atteso()

        val risultato = s.servizio().esegui(EseguiProssimaElaborazione()).atteso()

        assertEquals(RisultatoAvanzamento.NessunElemento, risultato)
        assertEquals(emptyList(), s.eventi.pubblicati)
        assertEquals(emptyList(), s.segnalatore.terminate)
    }

    /** One fresh world: fakes, a counting unit of work, a recording synchronous subscriber. */
    private class Scenario {
        val elaborazioni = ElaborazioneRepositoryFinta()
        val trascritti = TrascrittoRepositoryFinta()
        val uow = UnitaDiLavoroFinta(elaborazioni, trascritti)
        val eventi = DispatcherEventiFinta(uow)
        val contata = UnitaDiLavoroContata(eventi.unitaDiLavoro)
        val segnalatore = SegnalatoreFaseFinta()
        val sincroni = mutableListOf<EventoPubblicato>()
        val transazioneDiOgniSincrono = mutableListOf<Int>()
        var trascrittoAlSostituito: FormaTrascritto? = null

        init {
            eventi.registraSincrono { evento ->
                sincroni += evento
                transazioneDiOgniSincrono += contata.transazioni
                if (evento is TrascrittoSostituito) trascrittoAlSostituito = trascritti.trova(R)?.let(::forma)
                Esito.Ok(Unit)
            }
        }

        /** R already transcribed (completata + [VECCHIO]) with a 'Ritrascrivi' queued. */
        fun giaTrascritta(): Scenario = apply {
            val completata = unaElaborazione(StatoElaborazione.COMPLETATA, COMPLETATA_1, R, creataAlle = PRIMA)
            elaborazioni.salva(completata).atteso()
            trascritti.salva(VECCHIO)
            elaborazioni.salva(Elaborazione.accoda(RITRASCRIZIONE, R, DOPO, null).aggregato).atteso()
        }

        fun pipeline(
            registrazioni: LettoreRegistrazione = LettoreRegistrazioneFinta(mapOf(R to vista(R), R2 to vista(R2))),
            decodificatore: DecodificatoreAudio =
                DecodificatoreAudioFinta(mapOf(riferimento(R) to DURATA, riferimento(R2) to DURATA)),
            diarizzatore: Diarizzatore = DiarizzatoreFinta(TURNI),
            allineatore: Allineatore = AllineatoreFinta(),
        ): PortePipeline = PortePipeline(registrazioni, decodificatore, diarizzatore, allineatore, segnalatore)

        fun servizio(pipeline: PortePipeline = pipeline()): EseguiProssimaElaborazioneServizio =
            EseguiProssimaElaborazioneServizio(contata, OROLOGIO, elaborazioni, trascritti, pipeline, eventi)

        fun esegui(pipeline: PortePipeline) {
            servizio(pipeline).esegui(EseguiProssimaElaborazione()).atteso()
        }
    }

    private companion object {
        val R = RegistrazioneId("registrazione-1")
        val R2 = RegistrazioneId("registrazione-2")
        val COMPLETATA_1 = ElaborazioneId("completata-1")
        val RITRASCRIZIONE = ElaborazioneId("ritrascrizione")
        const val DURATA = 2_000L
        val OROLOGIO: Clock = Clock.fixed(Instant.parse("2026-09-24T10:10:00Z"), ZoneOffset.UTC)
        val PRIMA: Instant = Instant.parse("2026-09-24T08:00:00Z")
        val DOPO: Instant = Instant.parse("2026-09-24T09:00:00Z")

        /** Diarizer voice 1 speaks first, so it becomes Voce 1 (AC-71). */
        val TURNI = listOf(
            Turno(IntervalloMs(0, 800), voceIndice = 1),
            Turno(IntervalloMs(1_000, 1_800), voceIndice = 0),
        )

        /** The old generation: Voci 1..5, two Segmenti each, counters 6 / 11. */
        val VECCHIO: Trascritto = unTrascritto(voci = 5, segmentiPerVoce = 2, registrazioneId = R)
        val FORMA_VECCHIO = forma(VECCHIO)

        /** The new generation the pipeline produces from [TURNI], numbered from 1 again. */
        val NUOVO = FormaTrascritto(
            segmenti = listOf(
                listOf(1, 1, IntervalloMs(0, 800), "voce 1 0-800"),
                listOf(2, 2, IntervalloMs(1_000, 1_800), "voce 0 1000-1800"),
            ),
            prossimaVoce = 3,
            prossimoSegmento = 3,
        )

        fun riferimento(id: RegistrazioneId) = RiferimentoAudio("audio/${id.valore}.m4a")

        fun vista(id: RegistrazioneId) = RegistrazioneVista(
            registrazioneId = id,
            progettoId = ProgettoId("progetto-1"),
            titolo = "Riunione",
            riferimentoAudio = riferimento(id),
            dataRegistrazione = LocalDate.of(2026, 9, 20),
            durataMs = DURATA,
        )
    }
}

/** The observable state of a [Trascritto]: every Segmento (id, Voce, interval, text) and both counters. */
private data class FormaTrascritto(val segmenti: List<List<Any>>, val prossimaVoce: Int, val prossimoSegmento: Int)

private fun forma(t: Trascritto) = FormaTrascritto(
    t.segmenti.map { listOf(it.id.numero, it.voceId.numero, it.intervallo, it.testo) },
    t.prossimaVoce,
    t.prossimoSegmento,
)

/** Counts the OUTER transactions opened through it (the service's own, AC-438). */
private class UnitaDiLavoroContata(private val delegata: UnitaDiLavoro) : UnitaDiLavoro {
    var transazioni = 0
        private set

    override fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T> {
        transazioni++
        return delegata.inTransazione(blocco)
    }
}

private class DiarizzatoreGuasto : Diarizzatore {
    override fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno> = error("modello guasto")
}

private class AllineatoreGuasto : Allineatore {
    override fun allinea(campioni: CampioniAudio, turni: List<Turno>): List<SegmentoGrezzo> = error("modello guasto")
}

private class AllineatoreMuto : Allineatore {
    override fun allinea(campioni: CampioniAudio, turni: List<Turno>): List<SegmentoGrezzo> = emptyList()
}

/** A Segmento past the decoded duration: `Trascritto.crea` refuses it (INV-7). */
private class AllineatoreOltreLaDurata : Allineatore {
    override fun allinea(campioni: CampioniAudio, turni: List<Turno>): List<SegmentoGrezzo> =
        listOf(SegmentoGrezzo(0, IntervalloMs(0, 5_000), "oltre"))
}
