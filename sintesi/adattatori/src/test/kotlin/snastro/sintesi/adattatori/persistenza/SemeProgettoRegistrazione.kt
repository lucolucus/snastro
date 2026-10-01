package snastro.sintesi.adattatori.persistenza

import app.cash.sqldelight.db.SqlDriver
import snastro.sintesi.applicazione.porte.PredisposizioneSintesi

/**
 * Seeds [predisposizione]'s `progetto`/`registrazione` parent rows (the `riassunto`/`impostazioni_sintesi` FKs)
 * with raw SQL on [driver] — never the generated `progettoQueries`/`registrazioneQueries`: ADR 0021's
 * `confini-sintesi` check (clause 2) forbids Sintesi from using another context's queries, even in a test.
 */
internal fun semina(driver: SqlDriver, predisposizione: PredisposizioneSintesi) {
    predisposizione.progetti.forEach {
        driver.execute(null, "INSERT INTO progetto(id, nome) VALUES ('${it.valore}', 'Progetto di prova')", 0)
    }
    predisposizione.registrazioni.forEach { (registrazioneId, progettoId) ->
        // ADR 0034: every Registrazione is a Parte of an Incontro; its id differs from the Registrazione's on purpose.
        driver.execute(
            null,
            "INSERT INTO incontro(id, progetto_id) " +
                "VALUES ('incontro-di-${registrazioneId.valore}', '${progettoId.valore}')",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO registrazione(id, progetto_id, incontro_id, titolo, riferimento_audio, durata_ms, " +
                "data_registrazione, aggiunta_alle) VALUES ('${registrazioneId.valore}', '${progettoId.valore}', " +
                "'incontro-di-${registrazioneId.valore}', 't', 'audio/${registrazioneId.valore}.wav', 1000, " +
                "'2026-09-26', 0)",
            0,
        )
    }
}
