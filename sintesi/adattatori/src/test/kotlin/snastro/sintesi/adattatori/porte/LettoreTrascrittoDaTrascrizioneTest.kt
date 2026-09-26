package snastro.sintesi.adattatori.porte

import org.junit.jupiter.api.Test
import snastro.kernel.CampioniAudio
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.Esito
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IntervalloMs
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.SegmentoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.sintesi.applicazione.porte.AmbienteLettoreTrascritto
import snastro.sintesi.applicazione.porte.LettoreTrascritto
import snastro.sintesi.applicazione.porte.LettoreTrascrittoContratto
import snastro.sintesi.applicazione.porte.SegmentoConiato
import snastro.sintesi.applicazione.porte.SegmentoSintesi
import snastro.sintesi.applicazione.porte.SemeTurno
import snastro.trascrizione.applicazione.comandi.AnnullaElaborazione
import snastro.trascrizione.applicazione.comandi.AnnullaElaborazioneServizio
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.comandi.AvviaElaborazioneServizio
import snastro.trascrizione.applicazione.comandi.EseguiProssimaElaborazione
import snastro.trascrizione.applicazione.comandi.EseguiProssimaElaborazioneServizio
import snastro.trascrizione.applicazione.comandi.PortePipeline
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmento
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmentoServizio
import snastro.trascrizione.applicazione.comandi.RisultatoAvanzamento
import snastro.trascrizione.applicazione.eventi.TrascrittoSostituito
import snastro.trascrizione.applicazione.letture.FasiInCorso
import snastro.trascrizione.applicazione.letture.StatiElaborazione
import snastro.trascrizione.applicazione.letture.VociDelTrascritto
import snastro.trascrizione.applicazione.porte.Allineatore
import snastro.trascrizione.applicazione.porte.DecodificatoreAudioFinta
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.applicazione.porte.LettoreRegistrazioneFinta
import snastro.trascrizione.applicazione.porte.RegistrazioneVista
import snastro.trascrizione.applicazione.porte.SegmentoGrezzo
import snastro.trascrizione.applicazione.porte.SegnalatoreFaseFinta
import snastro.trascrizione.applicazione.porte.TrascrittoRepositoryFinta
import snastro.trascrizione.applicazione.porte.Turno
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals

/**
 * D2 (dev-architecture-app.md#porta-contratto): [LettoreTrascrittoDaTrascrizione] passes
 * [LettoreTrascrittoContratto] real-on-real (AC-S50). The supplier is populated exclusively through
 * TRASCRIZIONE'S OWN commands — `AvviaElaborazione`/`EseguiProssimaElaborazione` (with fake ML
 * ports)/`RiassegnaSegmento`/`AnnullaElaborazione` — over its own in-memory port fakes (`applicazione`
 * testFixtures): the cross-context dependency rule (ADR 0002/0021, CR-1) allows `sintesi:adattatori`
 * to call only `trascrizione:applicazione`, never its `adattatori`'s SQL repositories.
 *
 * [AmbienteReale.avviaElaborazione] is a precondition check only: Trascrizione's public API has no
 * standalone command for JUST the `in_attesa -> in_corso` transition (only
 * [EseguiProssimaElaborazioneServizio] does it, bundled with the pipeline run and its completion), and
 * [LettoreTrascritto.elaborazioneAperta] cannot observe the difference either way (`true` for both
 * states). The real transition happens, together with the completion, inside
 * [AmbienteReale.completaElaborazione] / [AmbienteReale.fallisciElaborazione]'s own
 * [EseguiProssimaElaborazioneServizio] call (AC-S50).
 *
 * The clock TICKS 1 ms at every read ([OrologioCheAvanza]): the contract ranks the latest Elaborazione
 * by `(creataAlle, id)` (ADR 0018) — a FIXED Clock would make that ranking rely on
 * [GeneratoreIdFinto]'s lexical id order instead, which stops matching numeric/creation order once ids
 * reach two digits, making AC-S6's re-run scenarios nondeterministic (pre-release finding).
 */
class LettoreTrascrittoDaTrascrizioneTest : LettoreTrascrittoContratto() {
    override fun ambiente(): AmbienteLettoreTrascritto = AmbienteReale()

    /**
     * AC-S51: a synchronous subscriber of `TrascrittoSostituito`, registered on Trascrizione's own
     * dispatcher, reads [LettoreTrascritto.segmenti] from INSIDE the completion transaction of a
     * re-run — after ADR 0018 §2 has already replaced the Trascritto in that same transaction, before
     * it commits. The sostituzione-trascritto policy (`:sintesi:applicazione ..politiche`) relies on
     * this: it reads the NEW Trascritto, not the one being replaced.
     */
    @Test
    fun `AC-S51 nel completamento di una rielaborazione segmenti da gia il nuovo Trascritto`() {
        val a = AmbienteReale()
        val id = a.aggiungiRegistrazione()
        a.accodaElaborazione(id)
        a.avviaElaborazione(id)
        a.completaElaborazione(id, listOf(SemeTurno(0, IntervalloMs(0, 2_000), "Prima versione.")))

        var vistoDurante: List<SegmentoSintesi>? = null
        a.registraSincrono { evento -> vistoDurante = a.lettore.segmenti(evento.registrazioneId) }

        a.accodaElaborazione(id)
        a.avviaElaborazione(id)
        val nuovi = listOf(SemeTurno(0, IntervalloMs(10_000, 14_000), "Versione nuova."))
        val coniati = a.completaElaborazione(id, nuovi)

        val atteso = nuovi.zip(coniati) { t, c -> SegmentoSintesi(c.segmentoId, c.voceId, t.intervallo, t.testo) }
        assertEquals(atteso, vistoDurante)
    }

    /**
     * Plays the supplier through Trascrizione's own commands, over its own in-memory port fakes.
     * [registrazioniViste] stands in for Progetto's catalogue (Trascrizione's OWN consumer-owned
     * [snastro.trascrizione.applicazione.porte.LettoreRegistrazione] port): built directly (never
     * through Progetto's commands), because [LettoreTrascritto] never reads titolo/durata — only the
     * id, which this Ambiente mints itself with [generatoreId], exactly as Progetto would.
     */
    private class AmbienteReale : AmbienteLettoreTrascritto {
        private val clock = OrologioCheAvanza(Instant.parse("2026-09-23T10:00:00Z"))
        private val generatoreId = GeneratoreIdFinto()
        private val elaborazioni = ElaborazioneRepositoryFinta()
        private val trascritti = TrascrittoRepositoryFinta()
        private val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        private val registrazioniViste = mutableMapOf<RegistrazioneId, RegistrazioneVista>()
        private val registrazioni = LettoreRegistrazioneFinta(registrazioniViste)

        override val lettore: LettoreTrascritto = LettoreTrascrittoDaTrascrizione(
            VociDelTrascritto(trascritti),
            StatiElaborazione(elaborazioni, trascritti, FasiInCorso()),
        )

        override fun aggiungiRegistrazione(): RegistrazioneId {
            val id = RegistrazioneId(generatoreId.nuovo())
            registrazioniViste[id] = RegistrazioneVista(
                registrazioneId = id,
                progettoId = PROGETTO_ID,
                titolo = "Registrazione ${id.valore}",
                riferimentoAudio = RiferimentoAudio("audio/${id.valore}.wav"),
                dataRegistrazione = DATA_REGISTRAZIONE,
                durataMs = DURATA_MS,
            )
            return id
        }

        override fun accodaElaborazione(r: RegistrazioneId) {
            AvviaElaborazioneServizio(eventi.unitaDiLavoro, generatoreId, clock, registrazioni, elaborazioni)
                .esegui(AvviaElaborazione(r))
                .atteso()
        }

        override fun avviaElaborazione(r: RegistrazioneId) {
            check(elaborazioni.diRegistrazione(r).any { it.inAttesa }) {
                "nessuna Elaborazione in_attesa per ${r.valore}"
            }
        }

        override fun completaElaborazione(r: RegistrazioneId, turni: List<SemeTurno>): List<SegmentoConiato> {
            require(turni.isNotEmpty())
            eseguiProssima(r, turni)
            val trascritto = checkNotNull(trascritti.trova(r))
            return turni.map { t ->
                val segmento = trascritto.segmenti.single { it.intervallo == t.intervallo }
                SegmentoConiato(segmento.id, segmento.voceId)
            }
        }

        override fun fallisciElaborazione(r: RegistrazioneId) {
            // Nessun turno diarizzato -> Trascritto.crea rifiuta con NessunParlatoRilevato (AC-72).
            eseguiProssima(r, emptyList())
        }

        override fun annullaElaborazione(r: RegistrazioneId) {
            val id = elaborazioni.diRegistrazione(r).single { it.inAttesa }.id
            AnnullaElaborazioneServizio(eventi.unitaDiLavoro, elaborazioni, eventi)
                .esegui(AnnullaElaborazione(id))
                .atteso()
        }

        override fun riassegna(r: RegistrazioneId, segmento: SegmentoId, destinazione: VoceId?): VoceId =
            RiassegnaSegmentoServizio(eventi.unitaDiLavoro, trascritti, eventi)
                .esegui(RiassegnaSegmento(r, segmento, destinazione))
                .atteso()

        /** Test-only hook (AC-S51): registers a synchronous subscriber on Trascrizione's own dispatcher. */
        fun registraSincrono(azione: (TrascrittoSostituito) -> Unit) {
            eventi.registraSincrono { evento ->
                if (evento is TrascrittoSostituito) azione(evento)
                Esito.Ok(Unit)
            }
        }

        /**
         * `EseguiProssimaElaborazione` (AC-S50, "with fake ML"): runs the FIFO head through to
         * completion/failure in one call — the ONLY way Trascrizione's own API reaches in_corso, and it
         * always finishes it too (§ class KDoc). [esclusi] targets [r]'s OWN Elaborazione even when
         * another Registrazione has an older one still queued (several Ambiente calls may interleave
         * across Registrazioni, e.g. AC-S4's four scenarios).
         */
        private fun eseguiProssima(r: RegistrazioneId, turni: List<SemeTurno>) {
            val vista = registrazioniViste.getValue(r)
            val decodificatore = DecodificatoreAudioFinta(mapOf(vista.riferimentoAudio to vista.durataMs))
            val pipeline = PortePipeline(
                registrazioni,
                decodificatore,
                DiarizzatoreFinta(turni.map { Turno(it.intervallo, it.voceIndice) }),
                AllineatoreConTesto(turni.associate { it.intervallo to it.testo }),
                SegnalatoreFaseFinta(),
            )
            val altrove = elaborazioni.inAttesa().filter { it.registrazioneId != r }.mapTo(mutableSetOf()) { it.id }
            val servizio = EseguiProssimaElaborazioneServizio(
                eventi.unitaDiLavoro,
                clock,
                elaborazioni,
                trascritti,
                pipeline,
                eventi,
            )
            val risultato = servizio.esegui(EseguiProssimaElaborazione(esclusi = altrove)).atteso()
            check(risultato is RisultatoAvanzamento.Avviata) { "atteso Avviata per ${r.valore}, ottenuto $risultato" }
        }

        private companion object {
            val PROGETTO_ID = ProgettoId("progetto-1")
            val DATA_REGISTRAZIONE: LocalDate = LocalDate.of(2026, 9, 23)
            const val DURATA_MS = 60_000L
        }
    }
}

/** [Allineatore] that plays back [testoDi]`[intervallo]` verbatim instead of a fixed placeholder text. */
private class AllineatoreConTesto(private val testoDi: Map<IntervalloMs, String>) : Allineatore {
    override fun allinea(campioni: CampioniAudio, turni: List<Turno>): List<SegmentoGrezzo> =
        turni.map { SegmentoGrezzo(it.voceIndice, it.intervallo, testoDi.getValue(it.intervallo)) }
}

/** A [Clock] that moves forward 1 ms at every read (see the class KDoc above). */
private class OrologioCheAvanza(private var adesso: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = adesso.also { adesso = adesso.plus(Duration.ofMillis(1)) }
}
