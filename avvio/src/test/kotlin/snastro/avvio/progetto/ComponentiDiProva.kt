package snastro.avvio.progetto

import kotlinx.coroutines.Dispatchers
import snastro.avvio.parlanti.AdattatoriParlanti
import snastro.avvio.sintesi.ModelloLinguisticoNonDisponibile
import snastro.avvio.trascrizione.AdattatoriMl
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguisticoFinta
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico
import snastro.trascrizione.applicazione.porte.DecodificatoreAudioFinta
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.RiconoscitoreParlatoFinta
import snastro.trascrizione.applicazione.porte.VadFinta
import kotlin.time.Duration
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta as DecodificatoreParlantiFinta

/**
 * The app-wide pieces of the single composition with every non-headless edge faked — what a [SessioneProgettoImpl]
 * test composes its projects over (the session's own lifecycle is the subject there, not the modules'). The same
 * production [apriProgetto] runs: nothing is re-wired by hand.
 */
internal fun componentiDiProva(
    adattatoriMl: () -> AdattatoriMl = { AdattatoriMl(DiarizzatoreFinta(), RiconoscitoreParlatoFinta(), VadFinta()) },
    scadenzaArresto: Duration = ComponentiApp.SCADENZA_ARRESTO,
): ComponentiApp = ComponentiApp(
    io = Dispatchers.IO,
    adattatoriMl = adattatoriMl,
    decodificatore = { DecodificatoreAudioFinta(emptyMap()) },
    adattatoriParlanti = {
        AdattatoriParlanti(EstrattoreImprontaFinta(), { DecodificatoreParlantiFinta() }, proposte = true)
    },
    modelliPronti = { true },
    modello = ModelloLinguisticoNonDisponibile,
    disponibilita = DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.Installato),
    scadenzaArresto = scadenzaArresto,
)
