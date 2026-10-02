package snastro.trascrizione.adattatori.persistenza

import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.kernel.unIncontroDi
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.databaseInMemoria
import snastro.persistenza.seminaRegistrazioneDiProva
import snastro.trascrizione.dominio.DURATA_TRASCRITTO_MS
import snastro.trascrizione.dominio.unSegmentoIniziale
import snastro.trascrizione.dominio.unaRadice
import kotlin.test.Test
import kotlin.test.assertEquals

/** REWORK ADR 0019 §3 of [VociDellIncontroRepositorySql]: `segmento.confermato` is written and read back (AC-522). */
class ConfermatoRepositorySqlTest {
    private val db = databaseInMemoria().seminato()
    private val repo = repositorySql(db)

    @Test
    fun `AC-522 un Trascritto con flag misti e salvato e riletto identico`() {
        val t = unaRadice(voci = 2, segmentiPerVoce = 3, registrazioneId = R) // V1:S1,S3,S5 V2:S2,S4,S6
        t.riassegna(ref(3), VoceId(2)).atteso()
        t.confermaSegmento(ref(6), true).atteso()
        repo.salva(t)

        val riletto = checkNotNull(repo.trova(unIncontroDi(R)))

        assertEquals(t.trascritto(R)?.segmenti, riletto.trascritto(R)?.segmenti)
        assertEquals(listOf(0L, 0L, 1L, 0L, 0L, 1L), colonnaConfermato())
        riletto.confermaSegmento(ref(3), false).atteso()
        repo.salva(riletto)
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 1L), colonnaConfermato())
    }

    @Test
    fun `AC-522 un Trascritto appena completato salva ogni flag a 0`() {
        repo.salva(unaRadice(voci = 3, segmentiPerVoce = 2, registrazioneId = R))

        assertEquals(List(6) { 0L }, colonnaConfermato())
    }

    @Test
    fun `AC-522 la sostituzione ADR 0018 scrive 0 ovunque`() {
        val radice = unaRadice(voci = 2, segmentiPerVoce = 2, registrazioneId = R)
        radice.confermaSegmento(ref(1), true).atteso()
        radice.confermaSegmento(ref(2), true).atteso()
        repo.salva(radice)

        val turni = (0 until 4).map { unSegmentoIniziale(voceIndice = it % 2, inizioMs = it * 1_000L) }
        radice.completaParte(R, turni, DURATA_TRASCRITTO_MS).atteso() // ADR 0018 / INV-I5: the Parte replaced
        repo.salva(radice)

        assertEquals(List(4) { 0L }, colonnaConfermato())
        assertEquals(emptyList(), checkNotNull(repo.trascritto(R)).segmenti.filter { it.confermato })
    }

    private fun colonnaConfermato(): List<Long> =
        db.segmentoQueries.trovaDiTrascritto(R.valore).executeAsList().sortedBy { it.numero }.map { it.confermato }

    private fun SnastroDatabase.seminato(): SnastroDatabase = apply {
        progettoQueries.inserisci("progetto-1", "Progetto di prova")
        seminaRegistrazioneDiProva(
            id = R.valore,
            progettoId = "progetto-1",
            titolo = "Registrazione di prova",
            riferimentoAudio = "audio/${R.valore}.wav",
            durataMs = 600_000L,
            dataRegistrazione = "2026-09-24",
            aggiuntaAlle = 0L,
        )
    }

    private fun ref(numero: Int) = SegmentoRef(R, SegmentoId(numero))

    private companion object {
        val R = RegistrazioneId("registrazione-1")
    }
}
