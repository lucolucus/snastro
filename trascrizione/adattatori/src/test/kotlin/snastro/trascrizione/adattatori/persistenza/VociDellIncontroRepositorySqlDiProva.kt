package snastro.trascrizione.adattatori.persistenza

import snastro.kernel.LetturaCoerente
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.trascrizione.applicazione.porte.PredisposizioneTrascrizione
import snastro.trascrizione.applicazione.porte.ogniRegistrazioneNota

/**
 * The adapter under test on [db], reading the Parti as `seminaRegistrazioneDiProva` seeds them: each Registrazione the
 * one Parte of its own Incontro (`unIncontroDi`).
 */
internal fun repositorySql(db: SnastroDatabase, lettura: LetturaCoerente = UnitaDiLavoroSql(db)) =
    VociDellIncontroRepositorySql(db, lettura, ogniRegistrazioneNota())

/** The parent rows of every id a [PredisposizioneTrascrizione] names (seeded raw: Progetto has no port here). */
internal fun SnastroDatabase.seminaPredisposizione(predisposizione: PredisposizioneTrascrizione) {
    predisposizione.progetti.forEach { progettoQueries.inserisci(it.valore, "Progetto di prova") }
    predisposizione.registrazioni.forEach { (registrazioneId, progettoId) ->
        val incontroId = predisposizione.incontroDi(registrazioneId).valore
        incontroQueries.inserisci(id = incontroId, progettoId = progettoId.valore) // OR IGNORE: once per Incontro
        registrazioneQueries.inserisci(
            id = registrazioneId.valore,
            progettoId = progettoId.valore,
            incontroId = incontroId,
            titolo = "Registrazione di prova",
            riferimentoAudio = "audio/${registrazioneId.valore}.wav",
            durataMs = 600_000L,
            dataRegistrazione = "2026-09-23",
            aggiuntaAlle = 0L,
            oraDiInizio = null,
        )
    }
}
