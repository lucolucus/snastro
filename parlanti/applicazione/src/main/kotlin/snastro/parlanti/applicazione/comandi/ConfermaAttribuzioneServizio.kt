package snastro.parlanti.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.VoceRef
import snastro.kernel.mappa
import snastro.kernel.poi
import snastro.parlanti.applicazione.eventi.AttribuzioneConfermata
import snastro.parlanti.applicazione.eventi.ParlanteCreato
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.porte.AttribuzioneRepository
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.LettoreRegistrazione
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.dominio.Attribuzione
import snastro.parlanti.dominio.ErroreParlanti
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.TipoParlante

/**
 * Use-case `ConfermaAttribuzione` (AC-84..AC-87, [INV-15]/[INV-16]/[INV-17]/[INV-25]): confirms
 * `ConfermaAttribuzione.voceRef` onto an existing Parlante or a brand new one, extracts the Voce's
 * ImprontaVocale INSIDE the transaction (ADR 0012 R12), moves the print when the Attribuzione
 * changes and applies [INV-25] to a Parlante left without any Attribuzione. RC-1: every rule is
 * enforced through [Parlante] / [Attribuzione]; this service only pre-checks the [INV-16] set rule
 * (ADR 0007, backstopped by `ParlanteRepository.salva`) and looks up the ports it consumes.
 */
@Suppress("LongParameterList") // one parameter per collaborator: uow, id, 2 readers, 2 repos, 2 technical ports, eventi
public class ConfermaAttribuzioneServizio(
    private val uow: UnitaDiLavoro,
    private val generatoreId: GeneratoreId,
    private val registrazioni: LettoreRegistrazione,
    private val lettoreVoci: LettoreVoci,
    private val parlanti: ParlanteRepository,
    private val attribuzioni: AttribuzioneRepository,
    private val decodificatore: DecodificatoreAudio,
    private val estrattore: EstrattoreImpronta,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: ConfermaAttribuzione): Esito<Unit> = uow.inTransazione {
        val voceRef = c.voceRef
        val registrazioneId = voceRef.registrazioneId
        val registrazione = registrazioni.registrazione(registrazioneId)
            ?: return@inTransazione Esito.Errore(ErroreParlanti.TrascrittoNonTrovato(registrazioneId))
        val vociTrascritto = lettoreVoci.voci(registrazioneId)
            ?: return@inTransazione Esito.Errore(ErroreParlanti.TrascrittoNonTrovato(registrazioneId))
        val voce = vociTrascritto.find { it.voceRef == voceRef }
            ?: return@inTransazione Esito.Errore(ErroreParlanti.VoceNonTrovata(voceRef))

        risolviObiettivo(c.obiettivo, registrazione.progettoId).poi { risolto ->
            confermaSu(risolto, voceRef, voce.intervalli, registrazioneId)
        }
    }

    /**
     * [INV-17]: an existing target must be `attivo` (discovered through [Parlante.registraImpronta],
     * not duplicated here) and of the SAME Progetto (checked here — no aggregate call carries it).
     */
    private fun risolviObiettivo(obiettivo: ObiettivoAttribuzione, progettoId: ProgettoId): Esito<ObiettivoRisolto> =
        when (obiettivo) {
            is ObiettivoAttribuzione.ParlanteEsistente -> {
                val trovato = parlanti.trova(obiettivo.id)?.takeIf { it.progettoId == progettoId }
                if (trovato == null) {
                    Esito.Errore(ErroreParlanti.ParlanteNonTrovato(obiettivo.id))
                } else {
                    Esito.Ok(ObiettivoRisolto(trovato, parlanteCreato = null))
                }
            }
            is ObiettivoAttribuzione.NuovoParlante ->
                Nome.di(obiettivo.nome).poi { nome ->
                    if (parlanti.nomeAttivoInUso(progettoId, nome, escluso = null)) {
                        Esito.Errore(ErroreParlanti.NomeGiaInUso(nome.valore)) // INV-16 pre-check, ADR 0007
                    } else {
                        val creato = Parlante.crea(ParlanteId(generatoreId.nuovo()), progettoId, nome, obiettivo.tipo)
                        Esito.Ok(ObiettivoRisolto(creato.aggregato, creato.evento.pubblicato()))
                    }
                }
        }

    /** [AC-87]: `Attribuzione.conferma`/`cambia` decides whether anything changes (same Parlante = no-op, RC-1). */
    private fun risolviCambiamento(risolto: ObiettivoRisolto, voceRef: VoceRef): Esito<Cambiamento?> {
        val parlante = risolto.parlante
        val esistente = attribuzioni.trova(voceRef)
        return if (esistente == null) {
            val creato = Attribuzione.conferma(voceRef, parlante.progettoId, parlante.id)
            Esito.Ok(Cambiamento(creato.aggregato, creato.evento))
        } else {
            esistente.cambia(parlante.id).mappa { evento -> evento?.let { Cambiamento(esistente, it) } }
        }
    }

    /**
     * [INV-15]: only once [risolviCambiamento] decides something really changes, this extracts the
     * print — AC-86: an exception here rolls back everything, nothing was saved yet — and moves it
     * (removes the previous Parlante's print, applies [INV-25] to it).
     */
    private fun confermaSu(
        risolto: ObiettivoRisolto,
        voceRef: VoceRef,
        intervalli: List<IntervalloMs>,
        registrazioneId: RegistrazioneId,
    ): Esito<Unit> =
        risolviCambiamento(risolto, voceRef).poi { cambiamento ->
            if (cambiamento == null) {
                Esito.Ok(Unit) // AC-87: reconfirm, no change, no event
            } else {
                registraEDiffondi(risolto, cambiamento, voceRef, intervalli, registrazioneId)
            }
        }

    private fun registraEDiffondi(
        risolto: ObiettivoRisolto,
        cambiamento: Cambiamento,
        voceRef: VoceRef,
        intervalli: List<IntervalloMs>,
        registrazioneId: RegistrazioneId,
    ): Esito<Unit> {
        val campioni = decodificatore.campioni(registrazioneId, intervalli)
        val impronta = estrattore.estrai(campioni)
        return risolto.parlante.registraImpronta(voceRef, impronta)
            .poi {
                attribuzioni.salva(cambiamento.attribuzione)
                liberaPrecedenteSeServe(cambiamento.evento.precedente, voceRef)
            }
            .poi { parlanti.salva(risolto.parlante) }
            .poi { pubblica(risolto, cambiamento.evento) }
    }

    private fun liberaPrecedenteSeServe(precedenteId: ParlanteId?, voceRef: VoceRef): Esito<Unit> =
        if (precedenteId == null) Esito.Ok(Unit) else liberaPrecedente(precedenteId, voceRef)

    /**
     * [INV-15] removes the print; [INV-25]: an `attivo occasionale` left without any Attribuzione
     * ceases to exist — an already-`eliminato` tombstone (its print purged at elimination, ADR 0009)
     * is never re-removed here, it stays a tombstone (revisione-policy).
     */
    private fun liberaPrecedente(precedenteId: ParlanteId, voceRef: VoceRef): Esito<Unit> {
        val precedente = checkNotNull(parlanti.trova(precedenteId)) {
            "un'Attribuzione precedente riferisce un Parlante inesistente: $precedenteId"
        }
        precedente.rimuoviImpronta(voceRef)
        return if (precedente.attivo && precedente.occasionale && attribuzioni.diParlante(precedenteId).isEmpty()) {
            parlanti.rimuovi(precedenteId)
            Esito.Ok(Unit)
        } else {
            parlanti.salva(precedente)
        }
    }

    private fun pubblica(
        risolto: ObiettivoRisolto,
        evento: snastro.parlanti.dominio.AttribuzioneConfermata,
    ): Esito<Unit> {
        risolto.parlanteCreato?.let { eventi.pubblica(it) }
        eventi.pubblica(evento.pubblicato())
        return Esito.Ok(Unit)
    }
}

private data class ObiettivoRisolto(val parlante: Parlante, val parlanteCreato: ParlanteCreato?)

private data class Cambiamento(
    val attribuzione: Attribuzione,
    val evento: snastro.parlanti.dominio.AttribuzioneConfermata,
)

private fun TipoParlante.vista(): TipoParlanteVista =
    when (this) {
        TipoParlante.RICORRENTE -> TipoParlanteVista.RICORRENTE
        TipoParlante.OCCASIONALE -> TipoParlanteVista.OCCASIONALE
    }

private fun snastro.parlanti.dominio.ParlanteCreato.pubblicato(): ParlanteCreato =
    ParlanteCreato(parlanteId = parlanteId, progettoId = progettoId, nome = nome, tipo = tipo.vista())

private fun snastro.parlanti.dominio.AttribuzioneConfermata.pubblicato(): AttribuzioneConfermata =
    AttribuzioneConfermata(voceRef = voceRef, parlanteId = parlanteId, precedente = precedente)
