package snastro.trascrizione.adattatori.persistenza

import snastro.kernel.LetturaCoerente
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.trascrizione.applicazione.porte.ogniRegistrazioneNota

/**
 * The adapter under test on [db], reading the Parti as `seminaRegistrazioneDiProva` seeds them: each Registrazione the
 * one Parte of its own Incontro (`unIncontroDi`).
 */
internal fun repositorySql(db: SnastroDatabase, lettura: LetturaCoerente = UnitaDiLavoroSql(db)) =
    VociDellIncontroRepositorySql(db, lettura, ogniRegistrazioneNota())
