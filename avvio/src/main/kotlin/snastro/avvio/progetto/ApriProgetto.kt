package snastro.avvio.progetto

import snastro.avvio.Abbonamento
import snastro.avvio.ArrestoProgetto
import snastro.avvio.Avviabile
import snastro.avvio.ModuloComposizione
import snastro.avvio.coda.Campanello
import snastro.avvio.coda.CodaCondivisa
import snastro.avvio.coda.FonteCoda
import snastro.avvio.parlanti.CollaboratoriParlanti
import snastro.avvio.parlanti.ModuloParlanti
import snastro.avvio.sbobinatura.ModuloSbobinatura
import snastro.avvio.segnalazioneApp
import snastro.avvio.sintesi.ModuloSintesi
import snastro.avvio.trascrizione.CollaboratoriTrascrizione
import snastro.avvio.trascrizione.ModuloTrascrizione
import snastro.avvio.unisci
import snastro.kernel.AbbonatoDopoCommit
import snastro.kernel.AbbonatoSincrono
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione

/**
 * The ONLY code with order (ADR 0030 §1/§2, AC-C69..AC-C71): composes ONE open project over its [porte], top to
 * bottom —
 * 1. a [Campanello] (the queue's wake-up handle), then the modules, each built from [porte];
 * 2. the synchronous subscribers, registered from ONE declared list: Sintesi → Parlanti → Trascrizione (ADR 0030 §2,
 *    AC-S143 — correctness does not depend on it: all run in the command's single transaction);
 * 3. the after-commit subscribers, from ONE declared list: Sintesi → Parlanti → Trascrizione → Sbobinatura → Progetto
 *    (a Proposta is invalidated before ANY module's subscriber hears of the change, AC-317/AC-I49: the cache
 *    invalidations are the modules' `abbonatiDopoCommitPrioritari`, registered before every other);
 * 4. the shared queue ([CodaCondivisa]) over the sources of every module (+ [fontiAggiuntive], a test seam only);
 * 5. the startup recoveries of every source (AC-S145/AC-C71: before the first claim, and before the Parlanti
 *    realignment of step 6, AC-316);
 * 6. `avvia` on every module and then on the queue, in the project's scope.
 * A reorder is a one-line diff reviewed against ADR 0030. Nothing here is ever re-wired by a test: `AmbienteProgetto`
 * runs this very function.
 */
internal fun apriProgetto(
    porte: PorteProgetto,
    apertura: AperturaProgetto,
    app: ComponentiApp,
    fontiAggiuntive: List<FonteCoda> = emptyList(),
): ProgettoComposto {
    val campanello = Campanello()
    // 1. the modules
    val trascrizione = ModuloTrascrizione(porte, apertura, app, campanello)
    val sbobinatura = ModuloSbobinatura(porte, apertura, app)
    val parlanti = ModuloParlanti(porte, apertura, app, trascrizione.collaboratori)
    val sintesi = ModuloSintesi(porte, apertura, app, campanello)
    val progetto = ModuloProgetto(porte, apertura, app, sbobinatura.politica)
    // The start order (step 6): Sbobinatura's worker before Progetto's CompletaEliminazioniRegistrazioni (ADR 0020 §4).
    val moduli = listOf(trascrizione, sbobinatura, parlanti, sintesi, progetto)
    // 2. + 3. the declared lists
    val sincroni: List<ModuloComposizione> = listOf(sintesi, parlanti, trascrizione)
    val dopoCommit: List<ModuloComposizione> = listOf(sintesi, parlanti, trascrizione, sbobinatura, progetto)
    check(moduli.filterNot { it in sincroni }.all { it.abbonatiSincroni().isEmpty() }) {
        "un modulo fuori dalla lista sincrona dichiarata ha abbonati sincroni: vanno dichiarati (ADR 0030 §2)"
    }
    registraAbbonati(porte.dispatcher, sincroni, dopoCommit)
    // 4. the shared queue, woken through the Campanello
    val coda = CodaCondivisa(
        fonti = moduli.flatMap { it.fontiCoda() } + fontiAggiuntive,
        campanello = campanello,
        // AC-C54/AC-C57/AC-C58 (ADR 0028 §7.5): the ONE JUL-backed Segnalazione. A NON-NULL, descriptive cause is
        // what makes it log the exclusion at WARNING instead of the INFO it reserves for a cause-less recovery.
        segnalaBloccato = { id ->
            segnalazioneApp.segnala("elemento della coda condivisa escluso", ElementoCodaEscluso(id))
        },
        segnalaSfuggito = { e -> segnalazioneApp.segnala("elemento della coda condivisa sfuggito", e) },
    )
    // 5. the recoveries
    coda.recupera()
    // 6. avvia
    val avviati: List<Avviabile> = moduli + coda
    avviati.forEach { it.avvia(apertura.scope) }
    val collaboratoriProgetto = progetto.collaboratori
    val collaboratori = CollaboratoriProgetto(
        progettoId = apertura.progettoId,
        registrazioni = collaboratoriProgetto.registrazioni,
        incontri = collaboratoriProgetto.incontri,
        aggiungiRegistrazione = collaboratoriProgetto.aggiungiRegistrazione,
        modificaDataRegistrazione = collaboratoriProgetto.modificaDataRegistrazione,
        modificaOraDiInizio = collaboratoriProgetto.modificaOraDiInizio,
        rinominaRegistrazione = collaboratoriProgetto.rinominaRegistrazione,
        eliminaRegistrazione = collaboratoriProgetto.eliminaRegistrazione,
        lettoreAudio = apertura.lettoreAudio,
        pulizia = progetto.pulizia,
        trascrizione = trascrizione.collaboratori,
        parlanti = parlanti.collaboratori,
        sintesi = sintesi.collaboratori,
        sbobinatura = sbobinatura.collaboratori,
        avviaElaborazione = avviaEScarta(trascrizione.collaboratori, parlanti.collaboratori),
        posizioniNellaCoda = coda,
        aggiornamentiVista = unisci(
            listOf(
                sintesi.aggiornamentiVista,
                parlanti.aggiornamentiVista,
                trascrizione.aggiornamentiVista,
                progetto.aggiornamentiVista,
            ),
        ),
        scope = apertura.scope,
    )
    val arresto = ArrestoProgetto(app.scadenzaArresto)
    return ProgettoComposto(porte, collaboratori, coda, sincroni, dopoCommit, avviati, arresto)
}

/** 'Trascrivi'/'Riprova'/'Ritrascrivi': Trascrizione's command, then that Registrazione's similarity is dropped. */
private fun avviaEScarta(
    trascrizione: CollaboratoriTrascrizione,
    parlanti: CollaboratoriParlanti,
): (AvviaElaborazione) -> Esito<Unit> = { comando ->
    trascrizione.avviaElaborazione(comando).also {
        if (it is Esito.Ok) parlanti.somiglianza.scarta(comando.registrazioneId)
    }
}

/** Steps 2 and 3: every declared pair, in the declared module order, each delivering only its event type. */
internal fun registraAbbonati(
    dispatcher: DispatcherEventiInMemoria,
    sincroni: List<ModuloComposizione>,
    dopoCommit: List<ModuloComposizione>,
) {
    sincroni.flatMap { it.abbonatiSincroni() }.forEach { dispatcher.registraSincrono(IscrizioneSincrona(it)) }
    // The priority subscribers (cache invalidations) of EVERY module first, then the declared order.
    val prioritari = dopoCommit.flatMap { it.abbonatiDopoCommitPrioritari() }
    val ordinari = dopoCommit.flatMap { it.abbonatiDopoCommit() }
    (prioritari + ordinari).forEach { dispatcher.registraDopoCommit(IscrizioneDopoCommit(it)) }
}

/** A registered synchronous [Abbonamento]: delivers only events of its type (the rest are Ok, untouched). */
private class IscrizioneSincrona(private val abbonamento: Abbonamento<AbbonatoSincrono>) : AbbonatoSincrono {
    override fun ricevi(evento: EventoPubblicato): Esito<Unit> =
        if (abbonamento.evento.isInstance(evento)) abbonamento.abbonato.ricevi(evento) else Esito.Ok(Unit)
}

/** A registered after-commit [Abbonamento]: delivers only events of its type. */
private class IscrizioneDopoCommit(private val abbonamento: Abbonamento<AbbonatoDopoCommit>) : AbbonatoDopoCommit {
    override fun ricevi(evento: EventoPubblicato) {
        if (abbonamento.evento.isInstance(evento)) abbonamento.abbonato.ricevi(evento)
    }
}

/**
 * AC-C54: [CodaCondivisa]'s `segnalaBloccato` report needs a non-null `causa` so `segnalazioneApp` logs it at
 * WARNING — never a real thrown exception (nothing threw), so a descriptive marker type with no stack trace.
 */
private class ElementoCodaEscluso(id: String) : Exception("elemento '$id' escluso dalla coda", null, false, false)
