package snastro.parlanti.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
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
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.SorgenteImpronta
import snastro.parlanti.dominio.TipoParlante

/**
 * Use-case `ConfermaAttribuzione` (AC-84..AC-87, AC-282..AC-285, [INV-15]/[INV-16]/[INV-17]/[INV-25]):
 * confirms `ConfermaAttribuzione.voceRef` onto an existing Parlante or a brand new one, moves the print
 * when the Attribuzione changes and applies [INV-25] to a Parlante left without any Attribuzione.
 *
 * ADR 0012 Amendment (b) point 2: OUTSIDE any transaction it reads the Voce, bounds its audio with
 * [SorgenteImpronta.di] and extracts the print (after the cheap refusals: Registrazione/Trascritto/Voce
 * not found, re-confirm of the same Parlante); THEN one transaction re-reads the Voce — a different
 * source is [ErroreParlanti.VoceCambiata], nothing written — runs every check authoritatively and writes
 * Attribuzione + print row (`sorgente` = [SorgenteImpronta.chiave], `modello` = [EstrattoreImpronta.modello]).
 * RC-1: every rule is enforced through [Parlante] / [Attribuzione]; this service only pre-checks the
 * [INV-16] set rule (ADR 0007, backstopped by `ParlanteRepository.salva`) and looks up the ports it consumes.
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
    public fun esegui(c: ConfermaAttribuzione): Esito<Unit> =
        leggiVoceNelleParti(c.voceRef, registrazioni, lettoreVoci).poi { voce ->
            if (riconfermaDelloStessoParlante(c)) {
                Esito.Ok(Unit) // AC-87 cheap refusal: nothing changes, no extraction, no event
            } else {
                // AC-86: a decode/extract failure propagates here, before any transaction is opened.
                val estratte = voce.parti.map { estrai(it) }
                uow.inTransazione { confermaInTransazione(c, voce.sorgenti(), estratte) }
            }
        }

    /** [INV-I8]: one print per Parte where the Voce speaks, each from its own bounded source. */
    private fun estrai(inParte: VoceInParte): ImprontaEstratta {
        val campioni = decodificatore.campioni(inParte.parte, inParte.sorgente.intervalli)
        return ImprontaEstratta(estrattore.estrai(campioni), inParte.sorgente.chiave, inParte.parte)
    }

    private fun confermaInTransazione(
        c: ConfermaAttribuzione,
        sorgentiEstratte: List<Pair<RegistrazioneId, String>>,
        estratte: List<ImprontaEstratta>,
    ): Esito<Unit> =
        leggiVoceNelleParti(c.voceRef, registrazioni, lettoreVoci).poi { voce ->
            if (voce.sorgenti() != sorgentiEstratte) {
                Esito.Errore(ErroreParlanti.VoceCambiata(c.voceRef)) // AC-282: edited since the extraction
            } else {
                risolviObiettivo(c.obiettivo, voce.progettoId).poi { confermaSu(it, c.voceRef, estratte) }
            }
        }

    /** AC-87 before extracting: the Voce is already attributed to the very Parlante being confirmed. */
    private fun riconfermaDelloStessoParlante(c: ConfermaAttribuzione): Boolean {
        val obiettivo = c.obiettivo as? ObiettivoAttribuzione.ParlanteEsistente ?: return false
        return attribuzioni.trova(c.voceRef)?.parlanteId == obiettivo.id
    }

    /**
     * [INV-17]: an existing target must be `attivo` (discovered through [Parlante.aggiungiImpronta],
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

    /** [INV-15]: writes the print only when [risolviCambiamento] decides something really changes. */
    private fun confermaSu(risolto: ObiettivoRisolto, voceRef: VoceRef, estratte: List<ImprontaEstratta>): Esito<Unit> =
        risolviCambiamento(risolto, voceRef).poi { cambiamento ->
            if (cambiamento == null) {
                Esito.Ok(Unit) // AC-87: reconfirm, no change, no event
            } else {
                registraEDiffondi(risolto, cambiamento, voceRef, estratte)
            }
        }

    private fun registraEDiffondi(
        risolto: ObiettivoRisolto,
        cambiamento: Cambiamento,
        voceRef: VoceRef,
        estratte: List<ImprontaEstratta>,
    ): Esito<Unit> =
        // the Parlante MUST be saved before the Attribuzione: persistenza-schema's
        // attribuzione.parlante_id REFERENCES parlante(id) is an immediate FK (SQLite
        // foreign_keys=ON) — for a brand new Parlante the row must exist first.
        estratte.fold<ImprontaEstratta, Esito<Unit>>(Esito.Ok(Unit)) { esito, e ->
            esito.poi {
                risolto.parlante.aggiungiImpronta(voceRef, e.parte, e.impronta, e.sorgente, estrattore.modello)
            }
        }
            .poi { parlanti.salva(risolto.parlante) }
            .poi {
                attribuzioni.salva(cambiamento.attribuzione)
                liberaPrecedente(cambiamento.evento.precedente, voceRef)
            }
            .poi { pubblica(risolto, cambiamento.evento) }

    /**
     * [INV-15] removes the print; [INV-25]: an `attivo occasionale` left without any Attribuzione
     * ceases to exist — an already-`eliminato` tombstone (its print purged at elimination, ADR 0009)
     * is never re-removed here, it stays a tombstone (revisione-policy). No-op on a first confirmation.
     */
    private fun liberaPrecedente(precedenteId: ParlanteId?, voceRef: VoceRef): Esito<Unit> {
        if (precedenteId == null) return Esito.Ok(Unit)
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

private class ImprontaEstratta(val impronta: Impronta, val sorgente: String, val parte: RegistrazioneId)

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
