package snastro.avvio.progetto

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import snastro.kernel.AbbonatoDopoCommit
import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.eventi.DataRegistrazioneModificata
import snastro.progetto.applicazione.eventi.OraDiInizioModificata
import snastro.progetto.applicazione.eventi.RegistrazioneAggiunta
import snastro.progetto.applicazione.eventi.RegistrazioneRinominata
import snastro.ui.AggiornamentiVista
import snastro.ui.Cambiamento

/**
 * [AggiornamentiVista] fed by `RegistrazioneAggiunta`/`DataRegistrazioneModificata`/`OraDiInizioModificata`/
 * `RegistrazioneRinominata` (AC-242, AC-366 — Progetto's Registrazione commands): an [AbbonatoDopoCommit] VALUE that
 * `ModuloProgetto` pairs with those four events (ADR 0030 §1) — AFTER commit, never on rollback, like every consumer of
 * `RegistrazioneAggiunta` (it has no synchronous subscriber: importing never auto-starts an Elaborazione, that policy
 * is removed, not deferred — ADR 0014). `replay = 1`: a collector that starts AFTER a [Cambiamento] already fired
 * (a screen mounted between the commit and its own `init`) still sees it once and refreshes — never stuck showing a
 * stale list.
 */
internal class AggiornamentiVistaEventi(
    /** ADR 0033 §4.1: the Parti of an Incontro (each may have its S3 open), `null` once it ceased. */
    private val partiDi: (IncontroId) -> List<RegistrazioneId>?,
) : AggiornamentiVista, AbbonatoDopoCommit {
    private val _cambiamenti = MutableSharedFlow<Cambiamento>(replay = 1, extraBufferCapacity = EXTRA_BUFFER)
    override val cambiamenti = _cambiamenti.asSharedFlow()

    override fun ricevi(evento: EventoPubblicato) {
        registrazioniDi(evento).forEach { _cambiamenti.tryEmit(Cambiamento(it)) }
    }

    /**
     * The Registrazioni to refresh. An event that changes the Parti of an Incontro (a Parte added, re-dated or
     * re-timed: the numbering, the switcher, and the Riassunto 'superato') refreshes EVERY Parte of it, so the S3
     * open on another Parte shows it too (AC-I90). A deletion needs none: the purges already refresh everything.
     */
    private fun registrazioniDi(evento: EventoPubblicato): List<RegistrazioneId> = when (evento) {
        is RegistrazioneAggiunta -> conParti(evento.registrazioneId, evento.incontroId)
        is DataRegistrazioneModificata -> conParti(evento.registrazioneId, evento.incontroId)
        is OraDiInizioModificata -> conParti(evento.registrazioneId, evento.incontroId)
        is RegistrazioneRinominata -> listOf(evento.registrazioneId)
        else -> emptyList()
    }

    private fun conParti(id: RegistrazioneId, incontroId: IncontroId): List<RegistrazioneId> =
        (listOf(id) + partiDi(incontroId).orEmpty()).distinct()

    private companion object {
        const val EXTRA_BUFFER = 8
    }
}
