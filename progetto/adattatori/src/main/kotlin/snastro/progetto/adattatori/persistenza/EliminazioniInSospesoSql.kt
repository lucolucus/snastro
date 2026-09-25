package snastro.progetto.adattatori.persistenza

import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.persistenza.SnastroDatabase
import snastro.progetto.applicazione.porte.EliminazioneInSospeso
import snastro.progetto.applicazione.porte.EliminazioniInSospeso
import java.time.Clock
import java.time.LocalDate
import migrations.Eliminazione_in_sospeso as EliminazioneRiga

/**
 * [EliminazioniInSospeso] on `eliminazione_in_sospeso` (5.sqm, ADR 0020 §4). `eliminata_alle` is not part of the
 * port type: it is minted here from [orologio] (epoch millis) and only orders [elenco] (then by id, in the query).
 * Never opens its own transaction: the caller's [snastro.kernel.UnitaDiLavoro] does (ADR 0012).
 */
public class EliminazioniInSospesoSql(
    private val db: SnastroDatabase,
    private val orologio: Clock,
) : EliminazioniInSospeso {
    override fun registra(e: EliminazioneInSospeso) {
        db.eliminazioneInSospesoQueries.inserisci(
            registrazioneId = e.registrazioneId.valore,
            titolo = e.titolo,
            dataRegistrazione = e.dataRegistrazione.toString(),
            riferimentoAudio = e.riferimentoAudio.percorsoRelativo,
            eliminataAlle = orologio.millis(),
        )
    }

    override fun elenco(): List<EliminazioneInSospeso> =
        db.eliminazioneInSospesoQueries.elenco().executeAsList().map { it.inPorta() }

    override fun concludi(id: RegistrazioneId) {
        db.eliminazioneInSospesoQueries.elimina(id.valore)
    }
}

private fun EliminazioneRiga.inPorta() = EliminazioneInSospeso(
    registrazioneId = RegistrazioneId(registrazione_id),
    titolo = titolo,
    dataRegistrazione = LocalDate.parse(data_registrazione),
    riferimentoAudio = RiferimentoAudio(riferimento_audio),
)
