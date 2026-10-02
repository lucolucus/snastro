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
import snastro.kernel.unIncontroDi
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
import snastro.trascrizione.applicazione.comandi.DividiVoce
import snastro.trascrizione.applicazione.comandi.DividiVoceServizio
import snastro.trascrizione.applicazione.comandi.EseguiProssimaElaborazione
import snastro.trascrizione.applicazione.comandi.EseguiProssimaElaborazioneServizio
import snastro.trascrizione.applicazione.comandi.PortePipeline
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmento
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmentoServizio
import snastro.trascrizione.applicazione.comandi.RisultatoAvanzamento
import snastro.trascrizione.applicazione.comandi.UnisciVoci
import snastro.trascrizione.applicazione.comandi.UnisciVociServizio
import snastro.trascrizione.applicazione.eventi.TrascrittoSostituito
import snastro.trascrizione.applicazione.eventi.VoceDivisa
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
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepositoryFinta
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
 * [AmbienteReale.avviaElaborazione] performs the REAL `in_attesa -> in_corso` transition itself
 * (rework 1): Trascrizione's public API has no standalone command for it (only
 * [EseguiProssimaElaborazioneServizio] does, bundled with the pipeline run and its completion), so it
 * calls the aggregate's own `Elaborazione.avvia` directly and `salva`s it — the one instance method
 * accessed through inference, never imported by name (CR-1: `sintesi:adattatori` never imports
 * `trascrizione.dominio`) — so [StatiElaborazione] genuinely reports `IN_CORSO` in between (dropping it
 * from `LettoreTrascrittoDaTrascrizione.APERTI` now turns AC-I29's "in_corso" assertion red).
 * [AmbienteReale.fallisciElaborazione] takes that SAME `Elaborazione` to `fallita` the same way
 * (`Elaborazione.fallisci`, Trascritto untouched). [AmbienteReale.completaElaborazione] cannot: only
 * [EseguiProssimaElaborazioneServizio] reaches `Trascritto.crea` (a `trascrizione.dominio` factory this
 * module has no edge to, ADR 0021 §2), and it only ever claims a FIFO `in_attesa` head — never an
 * already-`in_corso` one. So it first closes the `in_corso` Elaborazione itself (`Elaborazione.completa`,
 * freeing the one-open-per-Registrazione slot, INV-4) then re-queues a fresh one for the SAME
 * Registrazione and runs THAT one through [EseguiProssimaElaborazioneServizio] as before — two
 * `Elaborazione` rows land `completata`, the second carrying the real Trascritto; several `completata`
 * rows per Registrazione are already normal (ADR 0018) and [LettoreTrascritto] only ever reads the
 * latest one, so this is unobservable through the port.
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
        private val trascritti = VociDellIncontroRepositoryFinta()
        private val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        private val registrazioniViste = mutableMapOf<RegistrazioneId, RegistrazioneVista>()
        private val registrazioni = LettoreRegistrazioneFinta(registrazioniViste)

        override val lettore: LettoreTrascritto = LettoreTrascrittoDaTrascrizione(
            VociDelTrascritto(trascritti, registrazioni),
            StatiElaborazione(elaborazioni, trascritti, FasiInCorso()),
        )

        override fun aggiungiRegistrazione(): RegistrazioneId {
            val id = RegistrazioneId(generatoreId.nuovo())
            registrazioniViste[id] = RegistrazioneVista(
                registrazioneId = id,
                incontroId = unIncontroDi(id),
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
            val elaborazione = elaborazioni.diRegistrazione(r).singleOrNull { it.inAttesa }
                ?: error("nessuna Elaborazione in_attesa per ${r.valore}")
            elaborazione.avvia(clock.instant()).atteso()
            elaborazioni.salva(elaborazione).atteso()
        }

        /**
         * Closes the `in_corso` Elaborazione ([avviaElaborazione]) as `completata` itself (freeing the
         * one-open-per-Registrazione slot, INV-4), then re-queues a FRESH one for [r] and runs THAT one
         * through [eseguiProssima] — the only path that reaches `Trascritto.crea` (class KDoc). Multiple
         * `completata` rows per Registrazione are normal (ADR 0018); [LettoreTrascritto] reads only the
         * latest one, so the extra row is invisible through the port.
         */
        override fun completaElaborazione(r: RegistrazioneId, turni: List<SemeTurno>): List<SegmentoConiato> {
            require(turni.isNotEmpty())
            val chiusa = elaborazioneInCorsoDi(r)
            chiusa.completa().atteso()
            elaborazioni.salva(chiusa).atteso()
            accodaElaborazione(r)
            eseguiProssima(r, turni)
            val trascritto = checkNotNull(trascritti.trascritto(r))
            return turni.map { t ->
                val segmento = trascritto.segmenti.single { it.intervallo == t.intervallo }
                SegmentoConiato(segmento.id, segmento.voceId)
            }
        }

        override fun fallisciElaborazione(r: RegistrazioneId) {
            val elaborazione = elaborazioneInCorsoDi(r)
            elaborazione.fallisci(MOTIVO_FALLIMENTO_TEST).atteso()
            elaborazioni.salva(elaborazione).atteso()
        }

        /** The [r] Elaborazione [avviaElaborazione] left `in_corso` — never imported by name (CR-1). */
        private fun elaborazioneInCorsoDi(r: RegistrazioneId) =
            elaborazioni.inCorso().singleOrNull { it.registrazioneId == r }
                ?: error("nessuna Elaborazione in_corso per ${r.valore}")

        override fun annullaElaborazione(r: RegistrazioneId) {
            val id = elaborazioni.diRegistrazione(r).single { it.inAttesa }.id
            AnnullaElaborazioneServizio(eventi.unitaDiLavoro, elaborazioni, eventi)
                .esegui(AnnullaElaborazione(id))
                .atteso()
        }

        override fun riassegna(r: RegistrazioneId, segmento: SegmentoId, destinazione: VoceId?): VoceId =
            RiassegnaSegmentoServizio(eventi.unitaDiLavoro, trascritti, registrazioni, eventi)
                .esegui(RiassegnaSegmento(r, segmento, destinazione))
                .atteso()

        override fun unisciVoci(r: RegistrazioneId, sopravvive: VoceId, rimossa: VoceId) {
            UnisciVociServizio(eventi.unitaDiLavoro, trascritti, registrazioni, eventi)
                .esegui(UnisciVoci(r, sopravvive, rimossa))
                .atteso()
        }

        override fun dividiVoce(r: RegistrazioneId, origine: VoceId, segmenti: Set<SegmentoId>): VoceId {
            DividiVoceServizio(eventi.unitaDiLavoro, trascritti, registrazioni, eventi)
                .esegui(DividiVoce(r, origine, segmenti))
                .atteso()
            return eventi.pubblicati.filterIsInstance<VoceDivisa>().last().nuova
        }

        /** Test-only hook (AC-S51): registers a synchronous subscriber on Trascrizione's own dispatcher. */
        fun registraSincrono(azione: (TrascrittoSostituito) -> Unit) {
            eventi.registraSincrono { evento ->
                if (evento is TrascrittoSostituito) azione(evento)
                Esito.Ok(Unit)
            }
        }

        /**
         * `EseguiProssimaElaborazione` (AC-S50, "with fake ML"): runs the FIFO head — the FRESH
         * Elaborazione [completaElaborazione] just re-queued for [r] — through avvio, the fake pipeline
         * and completion in one call; the only path that reaches `Trascritto.crea` (class KDoc).
         * [esclusi] targets [r]'s OWN head even when another Registrazione has an older one still queued
         * (several Ambiente calls may interleave across Registrazioni, e.g. AC-S4's four scenarios).
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

            /** [fallisciElaborazione]'s fixed reason: never read back through [LettoreTrascritto] (AC-S4/S6). */
            const val MOTIVO_FALLIMENTO_TEST = "fallimento di test"
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
