package snastro.ui.registrazioni

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.parlanti.applicazione.letture.IdentificazioneIncontro
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.comandi.EliminaRegistrazione
import snastro.progetto.applicazione.comandi.ModificaDataRegistrazione
import snastro.progetto.applicazione.comandi.RinominaRegistrazione
import snastro.progetto.applicazione.letture.IncontroDelProgettoVista
import snastro.progetto.applicazione.letture.RegistrazioneDelProgettoVista
import snastro.trascrizione.applicazione.comandi.AnnullaElaborazione
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.comandi.AvviaElaborazioniDellIncontro
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.ui.AggiornamentiVista
import snastro.ui.coda.PosizioniCoda
import snastro.ui.lettore.LettoreAudio
import java.time.Clock
import java.time.LocalTime

/**
 * The ONE place S2's tests build a [RegistrazioniPresenter]: every collaborator as a flat named argument, grouped here
 * into the presenter's bundles ([ComandiRigaS2]), so a regrouping of the constructor changes only this function.
 */
@Suppress("LongParameterList") // one named argument per collaborator, as the tests pass them
internal fun registrazioniPresenterDiProva(
    scope: CoroutineScope,
    io: CoroutineDispatcher,
    progettoId: ProgettoId,
    registrazioni: () -> List<RegistrazioneDelProgettoVista>,
    incontri: () -> List<IncontroDelProgettoVista>,
    aggiungiRegistrazione: (AggiungiRegistrazione) -> Esito<Unit>,
    modificaDataRegistrazione: (ModificaDataRegistrazione) -> Esito<Unit>,
    rinominaRegistrazione: (RinominaRegistrazione) -> Esito<Unit>,
    lettore: LettoreAudio,
    aggiornamenti: AggiornamentiVista,
    clock: Clock,
    statiElaborazione: (List<RegistrazioneId>) -> List<StatoRegistrazioneVista>,
    avviaElaborazione: (AvviaElaborazione) -> Esito<Unit>,
    apriRegistrazione: (RegistrazioneId) -> Unit,
    identificazioniIncontri: (List<IncontroId>) -> Map<IncontroId, IdentificazioneIncontro>,
    ritrascrivi: (AvviaElaborazione) -> Esito<Unit>,
    annullaElaborazione: (AnnullaElaborazione) -> Esito<Unit>,
    eliminaRegistrazione: (EliminaRegistrazione) -> Esito<Unit>,
    posizioniNellaCoda: () -> PosizioniCoda,
    avviaElaborazioniDellIncontro: (AvviaElaborazioniDellIncontro) -> Esito<Unit>,
    modificaOraDiInizioRegistrazione: (RegistrazioneId, LocalTime?) -> Esito<Unit>,
    numeroPersonePrecompilato: (IncontroId) -> Int?,
): RegistrazioniPresenter = RegistrazioniPresenter(
    scope = scope,
    io = io,
    progettoId = progettoId,
    registrazioni = registrazioni,
    incontri = incontri,
    aggiungiRegistrazione = aggiungiRegistrazione,
    lettore = lettore,
    aggiornamenti = aggiornamenti,
    clock = clock,
    statiElaborazione = statiElaborazione,
    apriRegistrazione = apriRegistrazione,
    identificazioniIncontri = identificazioniIncontri,
    eliminaRegistrazione = eliminaRegistrazione,
    posizioniNellaCoda = posizioniNellaCoda,
    avviaElaborazioniDellIncontro = avviaElaborazioniDellIncontro,
    comandiRiga = ComandiRigaS2(
        modificaData = modificaDataRegistrazione,
        rinomina = rinominaRegistrazione,
        avviaElaborazione = avviaElaborazione,
        ritrascrivi = ritrascrivi,
        annullaElaborazione = annullaElaborazione,
        modificaOraDiInizio = modificaOraDiInizioRegistrazione,
    ),
    numeroPersonePrecompilato = numeroPersonePrecompilato,
)
