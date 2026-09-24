package snastro.trascrizione.adattatori.persistenza

import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.databaseInMemoria
import snastro.trascrizione.dominio.unTrascritto
import kotlin.test.Test
import kotlin.test.assertEquals

/** REWORK ADR 0019 §3 of [TrascrittoRepositorySql]: `segmento.confermato` is written and read back (AC-522). */
class ConfermatoRepositorySqlTest {
    private val db = databaseInMemoria().seminato()
    private val repo = TrascrittoRepositorySql(db)

    @Test
    fun `AC-522 un Trascritto con flag misti e salvato e riletto identico`() {
        val t = unTrascritto(voci = 2, segmentiPerVoce = 3, registrazioneId = R) // V1:S1,S3,S5 V2:S2,S4,S6
        t.riassegna(SegmentoId(3), VoceId(2)).atteso()
        t.confermaSegmento(SegmentoId(6), true).atteso()
        repo.salva(t)

        val riletto = checkNotNull(repo.trova(R))

        assertEquals(t.segmenti, riletto.segmenti)
        assertEquals(listOf(0L, 0L, 1L, 0L, 0L, 1L), colonnaConfermato())
        riletto.confermaSegmento(SegmentoId(3), false).atteso()
        repo.salva(riletto)
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 1L), colonnaConfermato())
    }

    @Test
    fun `AC-522 un Trascritto creato da crea salva ogni flag a 0`() {
        repo.salva(unTrascritto(voci = 3, segmentiPerVoce = 2, registrazioneId = R))

        assertEquals(List(6) { 0L }, colonnaConfermato())
    }

    @Test
    fun `AC-522 la sostituzione ADR 0018 scrive 0 ovunque`() {
        val vecchio = unTrascritto(voci = 2, segmentiPerVoce = 2, registrazioneId = R)
        vecchio.confermaSegmento(SegmentoId(1), true).atteso()
        vecchio.confermaSegmento(SegmentoId(2), true).atteso()
        repo.salva(vecchio)

        repo.salva(unTrascritto(voci = 2, segmentiPerVoce = 2, registrazioneId = R))

        assertEquals(List(4) { 0L }, colonnaConfermato())
        assertEquals(emptyList(), checkNotNull(repo.trova(R)).segmenti.filter { it.confermato })
    }

    private fun colonnaConfermato(): List<Long> =
        db.segmentoQueries.trovaDiTrascritto(R.valore).executeAsList().sortedBy { it.numero }.map { it.confermato }

    private fun SnastroDatabase.seminato(): SnastroDatabase = apply {
        progettoQueries.inserisci("progetto-1", "Progetto di prova")
        registrazioneQueries.inserisci(
            id = R.valore,
            progettoId = "progetto-1",
            titolo = "Registrazione di prova",
            riferimentoAudio = "audio/${R.valore}.wav",
            durataMs = 600_000L,
            dataRegistrazione = "2026-09-24",
            aggiuntaAlle = 0L,
        )
    }

    private companion object {
        val R = RegistrazioneId("registrazione-1")
    }
}
