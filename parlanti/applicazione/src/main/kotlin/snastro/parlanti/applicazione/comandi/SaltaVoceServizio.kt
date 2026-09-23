package snastro.parlanti.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.VoceRef
import snastro.kernel.poi
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.porte.AttribuzioneRepository
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.LettoreRegistrazione
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.dominio.Attribuzione
import snastro.parlanti.dominio.AttribuzioneConfermata
import snastro.parlanti.dominio.ErroreParlanti
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.ParlanteCreato
import snastro.parlanti.dominio.SorgenteImpronta
import snastro.parlanti.dominio.TipoParlante
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import snastro.parlanti.applicazione.eventi.AttribuzioneConfermata as AttribuzioneConfermataPubblicata
import snastro.parlanti.applicazione.eventi.ParlanteCreato as ParlanteCreatoPubblicato

/**
 * `SaltaVoce` (AC-88/AC-89, AC-286..AC-290, [INV-19]): skipping `comando.voceRef` CONFIRMS it — a new
 * occasionale "Ospite del <DataRegistrazione>" [Parlante] is created (first free numeric suffix on a
 * normalized name clash, [ParlanteRepository.nomeAttivoInUso], ADR 0007) keeping the Voce's print
 * ([INV-14]), then the Voce is attributed to it. Refused, unchanged, on an already-attributed Voce
 * (`ErroreParlanti.VoceGiaAttribuita`, AC-89) — checked FIRST, before any lookup or native call.
 *
 * ADR 0012 Amendment (b) point 2: OUTSIDE any transaction the Voce's audio is bounded with
 * [SorgenteImpronta.di], decoded and its print extracted (a failure propagates, nothing written: AC-290);
 * THEN one transaction repeats the checks, re-reads the Voce — a different source is
 * [ErroreParlanti.VoceCambiata], nothing written — and writes Parlante + Attribuzione + print row
 * (`sorgente` = [SorgenteImpronta.chiave], `modello` = [EstrattoreImpronta.modello]). The guest's Nome is
 * derived ONCE from the Registrazione as read in that transaction: a later `ModificaDataRegistrazione`
 * never revisits an already-created guest's Nome (nothing re-derives it).
 */
// one parameter per collaborator: uow, id, 2 repos, 2 read ports, 2 technical ports, eventi
@Suppress("LongParameterList")
public class SaltaVoceServizio(
    private val uow: UnitaDiLavoro,
    private val generatoreId: GeneratoreId,
    private val parlanti: ParlanteRepository,
    private val attribuzioni: AttribuzioneRepository,
    private val registrazioni: LettoreRegistrazione,
    private val voci: LettoreVoci,
    private val decodificatore: DecodificatoreAudio,
    private val estrattore: EstrattoreImpronta,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(comando: SaltaVoce): Esito<Unit> {
        val voceRef = comando.voceRef
        return sorgenteDellaVoceLibera(voceRef).poi { sorgente ->
            val impronta = estrattore.estrai(decodificatore.campioni(voceRef.registrazioneId, sorgente.intervalli))
            uow.inTransazione {
                sorgenteDellaVoceLibera(voceRef).poi { attuale ->
                    if (attuale.chiave != sorgente.chiave) {
                        Esito.Errore(ErroreParlanti.VoceCambiata(voceRef)) // AC-286: edited since the extraction
                    } else {
                        creaOspite(voceRef, impronta, sorgente.chiave)
                    }
                }
            }
        }
    }

    /** AC-89 first, then the Voce's current [SorgenteImpronta] (TrascrittoNonTrovato / VoceNonTrovata). */
    private fun sorgenteDellaVoceLibera(voceRef: VoceRef): Esito<SorgenteImpronta> {
        if (attribuzioni.trova(voceRef) != null) return Esito.Errore(ErroreParlanti.VoceGiaAttribuita(voceRef))
        val vociTrascritto = voci.voci(voceRef.registrazioneId)
        val voce = vociTrascritto?.find { it.voceRef == voceRef }
        return when {
            vociTrascritto == null -> Esito.Errore(ErroreParlanti.TrascrittoNonTrovato(voceRef.registrazioneId))
            voce == null -> Esito.Errore(ErroreParlanti.VoceNonTrovata(voceRef))
            else -> Esito.Ok(SorgenteImpronta.di(voce.intervalli))
        }
    }

    private fun creaOspite(voceRef: VoceRef, impronta: Impronta, sorgente: String): Esito<Unit> {
        // Il Trascritto della Registrazione esiste (appena letto): la Registrazione stessa non puo mancare.
        val registrazione = checkNotNull(registrazioni.registrazione(voceRef.registrazioneId)) {
            "Registrazione ${voceRef.registrazioneId} assente pur avendo un Trascritto"
        }
        val nome = nomeOspiteLibero(registrazione.progettoId, registrazione.dataRegistrazione)
        val (parlante, evParlante) =
            Parlante.crea(ParlanteId(generatoreId.nuovo()), registrazione.progettoId, nome, TipoParlante.OCCASIONALE)
        // Un Parlante appena creato e sempre attivo: registraImpronta non puo rifiutare la richiesta.
        check(parlante.registraImpronta(voceRef, impronta, sorgente, estrattore.modello) is Esito.Ok)
        val (attribuzione, evAttribuzione) = Attribuzione.conferma(voceRef, registrazione.progettoId, parlante.id)

        // FK order: the new Parlante row before its Attribuzione.
        return parlanti.salva(parlante).poi {
            attribuzioni.salva(attribuzione)
            eventi.pubblica(evParlante.pubblicato())
            eventi.pubblica(evAttribuzione.pubblicato())
            Esito.Ok(Unit)
        }
    }

    /** [INV-19]: 'Ospite del dd/MM/yyyy', then the first free '(2)', '(3)', ... on a normalized clash. */
    private fun nomeOspiteLibero(progettoId: ProgettoId, dataRegistrazione: LocalDate): Nome {
        val base = "Ospite del ${FORMATO_DATA.format(dataRegistrazione)}"
        var suffisso = 1
        while (true) {
            val nome = nomeValido(if (suffisso == 1) base else "$base ($suffisso)")
            if (!parlanti.nomeAttivoInUso(progettoId, nome, escluso = null)) return nome
            suffisso++
        }
    }

    /** The generated guest text is never blank; a rejection here would be a programmer error. */
    private fun nomeValido(testo: String): Nome =
        when (val esito = Nome.di(testo)) {
            is Esito.Ok -> esito.valore
            is Esito.Errore -> error("Nome generato per l'ospite vuoto: \"$testo\"")
        }

    private companion object {
        val FORMATO_DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    }
}

private fun ParlanteCreato.pubblicato(): ParlanteCreatoPubblicato =
    ParlanteCreatoPubblicato(parlanteId, progettoId, nome, tipo.pubblicato())

private fun TipoParlante.pubblicato(): TipoParlanteVista =
    when (this) {
        TipoParlante.RICORRENTE -> TipoParlanteVista.RICORRENTE
        TipoParlante.OCCASIONALE -> TipoParlanteVista.OCCASIONALE
    }

private fun AttribuzioneConfermata.pubblicato(): AttribuzioneConfermataPubblicata =
    AttribuzioneConfermataPubblicata(voceRef, parlanteId, precedente)
