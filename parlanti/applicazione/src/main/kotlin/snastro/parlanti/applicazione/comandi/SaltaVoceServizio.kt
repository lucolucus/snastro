package snastro.parlanti.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.UnitaDiLavoro
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
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.ParlanteCreato
import snastro.parlanti.dominio.TipoParlante
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import snastro.parlanti.applicazione.eventi.AttribuzioneConfermata as AttribuzioneConfermataPubblicata
import snastro.parlanti.applicazione.eventi.ParlanteCreato as ParlanteCreatoPubblicato

/**
 * `SaltaVoce` (AC-88/AC-89, [INV-19]): skipping `comando.voceRef` CONFIRMS it — a new occasionale
 * "Ospite del <DataRegistrazione>" [Parlante] is created (first free numeric suffix on a normalized
 * name clash, [ParlanteRepository.nomeAttivoInUso], ADR 0007), the Voce's own audio is decoded and its
 * print extracted and kept on the new Parlante ([INV-14]), then the Voce is attributed to it. Refused,
 * unchanged, on an already-attributed Voce (`ErroreParlanti.VoceGiaAttribuita`, AC-89) — checked FIRST,
 * before any lookup or native call, so an already-attributed Voce never touches audio. The guest's
 * Nome is derived ONCE from the Registrazione as read right now: a later `ModificaDataRegistrazione`
 * never revisits an already-created guest's Nome (nothing re-derives it). The embedding extraction
 * runs inside this transaction like the pipeline's, serialized in `:ml-sherpa`/`:avvio` (ADR 0012 R12).
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
    public fun esegui(comando: SaltaVoce): Esito<Unit> = uow.inTransazione {
        val voceRef = comando.voceRef
        if (attribuzioni.trova(voceRef) != null) {
            return@inTransazione Esito.Errore(ErroreParlanti.VoceGiaAttribuita(voceRef))
        }
        val vociTrascritto = voci.voci(voceRef.registrazioneId)
            ?: return@inTransazione Esito.Errore(ErroreParlanti.TrascrittoNonTrovato(voceRef.registrazioneId))
        val voce = vociTrascritto.find { it.voceRef == voceRef }
            ?: return@inTransazione Esito.Errore(ErroreParlanti.VoceNonTrovata(voceRef))
        // Il Trascritto della Registrazione esiste (appena letto sopra): la Registrazione stessa non puo mancare.
        val registrazione = checkNotNull(registrazioni.registrazione(voceRef.registrazioneId)) {
            "Registrazione ${voceRef.registrazioneId} assente pur avendo un Trascritto"
        }

        val nome = nomeOspiteLibero(registrazione.progettoId, registrazione.dataRegistrazione)
        val (parlante, evParlante) =
            Parlante.crea(ParlanteId(generatoreId.nuovo()), registrazione.progettoId, nome, TipoParlante.OCCASIONALE)
        val campioni = decodificatore.campioni(voceRef.registrazioneId, voce.intervalli)
        // Un Parlante appena creato e sempre attivo: registraImpronta non puo rifiutare la richiesta.
        // TODO(option-c follow-up): extraction still inside the transaction; sorgente = the intervals decoded above.
        val sorgente = chiaveSorgenteProvvisoria(voce.intervalli)
        check(parlante.registraImpronta(voceRef, estrattore.estrai(campioni), sorgente, estrattore.modello) is Esito.Ok)
        val (attribuzione, evAttribuzione) = Attribuzione.conferma(voceRef, registrazione.progettoId, parlante.id)

        parlanti.salva(parlante).poi {
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
