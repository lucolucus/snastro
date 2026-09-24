package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import snastro.kernel.Esito
import snastro.kernel.EstrattoRef
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.letture.Candidato
import snastro.parlanti.applicazione.letture.ParlanteAttivo
import snastro.parlanti.applicazione.letture.PropostaDiUnione
import snastro.parlanti.applicazione.letture.PropostaVista
import snastro.parlanti.applicazione.letture.VoceIdentificata
import snastro.parlanti.applicazione.porte.Fascia
import snastro.trascrizione.applicazione.comandi.DividiVoce
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmento
import snastro.trascrizione.applicazione.comandi.UnisciVoci
import snastro.trascrizione.applicazione.letture.SegmentoTrascrittoView
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.trascrizione.applicazione.letture.TrascrittoView
import snastro.trascrizione.applicazione.letture.VoceTrascrittoView
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.ApriEsternoFinta
import snastro.ui.lettore.LettoreAudioFinta
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Collections

internal val REG = RegistrazioneId("id-1")
internal val V1 = VoceId(1)
internal val V2 = VoceId(2)
internal val V3 = VoceId(3)
internal val MARCO = ParlanteAttivo(ParlanteId("p-marco"), "Marco", TipoParlanteVista.RICORRENTE)
internal val GIULIA = ParlanteAttivo(ParlanteId("p-giulia"), "Giulia", TipoParlanteVista.OCCASIONALE)

internal fun ref(voce: VoceId) = VoceRef(REG, voce)

internal fun unCandidato(parlante: ParlanteAttivo = MARCO, fascia: Fascia = Fascia.FORTE) = Candidato(
    parlante.parlanteId,
    parlante.nome,
    parlante.tipoParlante,
    fascia,
    EstrattoRef(RegistrazioneId("id-0"), listOf(IntervalloMs(0, 1_000))),
)

private fun seg(n: Int, voce: VoceId, testo: String) =
    SegmentoTrascrittoView(SegmentoId(n), voce, n * 1_000L, n * 1_000L + 900, testo)

/** Voce 1: Segmenti 1 and 3 · Voce 2: Segmento 2 · Voce 3: Segmento 4. */
internal fun unTrascritto(
    segmenti: List<SegmentoTrascrittoView> = listOf(
        seg(1, V1, "Buongiorno."),
        seg(2, V2, "Salve."),
        seg(3, V1, "Iniziamo."),
        seg(4, V3, "Va bene."),
    ),
) = TrascrittoView(
    registrazioneId = REG,
    titolo = "Seduta del 12 marzo",
    dataRegistrazione = LocalDate.of(2026, 3, 12),
    durataMs = 10_000,
    segmenti = segmenti,
    voci = segmenti.map { it.voceId }.distinct().sortedBy { it.numero }
        .map { VoceTrascrittoView(it, "Voce ${it.numero}") },
)

internal fun spostato(vista: TrascrittoView, segmento: Int, voce: VoceId) = unTrascritto(
    vista.segmenti.map { if (it.segmentoId == SegmentoId(segmento)) it.copy(voceId = voce) else it },
)

/** A [Clock] on the test scheduler's virtual time (AC-412/AC-415). */
@OptIn(ExperimentalCoroutinesApi::class)
internal class OrologioVirtuale(private val scheduler: TestCoroutineScheduler) : Clock() {
    override fun instant(): Instant = Instant.ofEpochMilli(scheduler.currentTime)

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this
}

/**
 * The R2 collaborators of S3, all hand-written fakes over Published-Language values (RC-9). A card
 * command that succeeds ALSO applies its effect to [identificate] (what the real one writes).
 */
@Suppress("LongParameterList")
internal class AmbienteVoci(
    progetto: CoroutineScope,
    clock: Clock,
    var vista: TrascrittoView = unTrascritto(),
    var identificate: List<VoceIdentificata> = listOf(VoceIdentificata(V1), VoceIdentificata(V2), VoceIdentificata(V3)),
    var attivi: List<ParlanteAttivo> = listOf(GIULIA, MARCO),
    var unioni: List<PropostaDiUnione> = emptyList(),
    var proposta: (VoceRef) -> PropostaVista? = { PropostaVista(it.voceId, listOf(unCandidato())) },
) {
    val chiamateProposta: MutableList<VoceRef> = Collections.synchronizedList(mutableListOf())
    val revisioni: MutableList<Any> = mutableListOf()
    var esitoRevisione: (Any) -> Esito<Unit> = { Esito.Ok(Unit) }
    var esitoComando: (ComandoVoce) -> Esito<Unit> = { Esito.Ok(Unit) }
    var identificazioneRotta = false

    /** ADR 0018 Amendment (b) §2 (AC-452/454): `null` unless a test opts in via `presenter(conStati = true)`. */
    var statoElaborazione: StatoRegistrazioneVista? = null
    val aggiornamenti = AggiornamentiVistaFinta()
    val lettore = LettoreAudioFinta()
    val comandi = ComandiVoceFinta(progetto, clock) { c -> esitoComando(c).also { if (it is Esito.Ok) applica(c) } }
    val sorgenti = SorgentiParlanti(
        identificazione = {
            check(!identificazioneRotta) { "lettura rotta" }
            identificate
        },
        proposta = { r ->
            chiamateProposta += r
            proposta(r)
        },
        unioni = { unioni },
        parlantiAttivi = { attivi },
        estratto = { r -> EstrattoRef(r.registrazioneId, listOf(IntervalloMs(0, 500))) },
        comandi = comandi,
        unisci = { c -> revisione(c) },
        dividi = { c -> revisione(c) },
        riassegna = { c -> revisione(c) },
        aggiornamenti = aggiornamenti,
        clock = clock,
    )

    private fun revisione(c: Any): Esito<Unit> {
        revisioni += c
        return esitoRevisione(c).also { if (it is Esito.Ok) applicaRevisione(c) }
    }

    private fun nuovaVoce() = VoceId(vista.voci.maxOf { it.voceId.numero } + 1)

    private fun applicaRevisione(c: Any) {
        when (c) {
            is RiassegnaSegmento -> vista = spostato(vista, c.segmento.numero, c.destinazione ?: nuovaVoce())
            is DividiVoce -> {
                val nuova = nuovaVoce()
                c.segmenti.forEach { vista = spostato(vista, it.numero, nuova) }
            }
            is UnisciVoci -> vista.segmenti.filter { it.voceId == c.rimossa }.forEach {
                vista = spostato(vista, it.segmentoId.numero, c.sopravvive)
            }
        }
    }

    private fun applica(c: ComandoVoce) {
        val parlante = when (c) {
            is ComandoVoce.Conferma -> attivi.single { it.parlanteId == c.parlanteId }
            is ComandoVoce.Nuovo -> ParlanteAttivo(ParlanteId("p-nuovo"), c.nome, c.tipo)
            is ComandoVoce.Salta ->
                ParlanteAttivo(ParlanteId("p-ospite"), "Ospite del 12/03/2026", TipoParlanteVista.OCCASIONALE)
        }
        identificate = identificate.map {
            if (it.voceId == c.voceRef.voceId) {
                VoceIdentificata(it.voceId, parlante.parlanteId, parlante.nome, parlante.tipoParlante)
            } else {
                it
            }
        }
    }

    /**
     * [conStati] opts a test into the ADR 0018 Amendment (b) §2 wiring — [statoElaborazione] as the
     * optional `stati` source, and [aggiornamenti] (the SAME instance [sorgenti] already collects) as
     * the base presenter's own reload trigger too. `false` by default: every pre-existing test stays
     * on the untouched R2 wiring (no `stati`, no base-level `aggiornamenti`).
     */
    fun presenter(scope: CoroutineScope, io: CoroutineDispatcher, conStati: Boolean = false) = RegistrazionePresenter(
        scope = scope,
        io = io,
        registrazioneId = REG,
        trascritto = { vista },
        documento = { "/progetti/demo.snastro/documenti/seduta.md" },
        lettore = lettore,
        apriEsterno = ApriEsternoFinta(),
        parlanti = sorgenti,
        stati = if (conStati) ({ statoElaborazione }) else null,
        aggiornamenti = if (conStati) aggiornamenti else null,
    )
}
