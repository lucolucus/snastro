package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import snastro.kernel.Esito
import snastro.kernel.SegmentoId
import snastro.parlanti.applicazione.letture.VoceIdentificata
import snastro.trascrizione.applicazione.letture.SegmentoTrascrittoView
import snastro.trascrizione.applicazione.letture.TrascrittoView
import snastro.trascrizione.applicazione.letture.VoceTrascrittoView
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.lettore.LettoreUiStato
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val TRASCRITTO_UNA_VOCE = TrascrittoView(
    registrazioneId = REG,
    titolo = "Seduta del 12 marzo",
    dataRegistrazione = LocalDate.of(2026, 3, 12),
    durataMs = 10_000,
    segmenti = listOf(SegmentoTrascrittoView(SegmentoId(1), V1, 0, 900, "Buongiorno.")),
    voci = listOf(VoceTrascrittoView(V1, "Voce 1")),
)

private fun unoStatoVuoto(trascritto: TrascrittoView) = MutableStateFlow<RegistrazioneUiStato>(
    RegistrazioneUiStato.Dati(
        titolo = trascritto.titolo,
        dataRegistrazione = trascritto.dataRegistrazione,
        durataMs = trascritto.durataMs,
        segmenti = emptyList(),
        barra = LettoreUiStato.Inattivo,
        audioDisponibile = true,
        documentoPercorso = null,
    ),
)

/**
 * L665a: [StatoVoci.ricaricaParlanti] is started by several call sites — two card commands settling
 * around the same time, a Cambiamento arriving mid-read, a Revisione's own reload — and nothing serialised
 * them. Real threads + latches (the same recipe as `LettorePresenterTest`'s HIGH-1) drive the ACTUAL
 * race: the OLDER read is put in flight first and made to finish LAST, after the newer one already wrote
 * its (fresher) result — this is the "write the failing test first" delicate part named for this batch.
 */
class StatoVociRicaricaTest {
    /** The FIRST call (the OLD read) blocks until [viaLiberaVecchia], well after the SECOND (the NEW
     * read) has already returned and been applied. */
    @Suppress("LongParameterList")
    private fun unaSorgenteConLetturaVecchiaBloccata(
        progetto: CoroutineScope,
        entrataVecchia: CountDownLatch,
        viaLiberaVecchia: CountDownLatch,
        marco: VoceIdentificata,
    ): SorgentiParlanti {
        var chiamate = 0
        return SorgentiParlanti(
            identificazione = {
                val numero = synchronized(this) { ++chiamate }
                if (numero == 1) {
                    entrataVecchia.countDown()
                    assertTrue(viaLiberaVecchia.await(5, TimeUnit.SECONDS), "timeout in attesa del via libera")
                    listOf(VoceIdentificata(V1))
                } else {
                    listOf(marco)
                }
            },
            proposta = { null },
            unioni = { emptyList() },
            parlantiAttivi = { listOf(MARCO) },
            estratto = { null },
            comandi = ComandiVoceFinta(progetto, Clock.systemUTC()),
            unisci = { Esito.Ok(Unit) },
            dividi = { Esito.Ok(Unit) },
            riassegna = { Esito.Ok(Unit) },
            aggiornamenti = AggiornamentiVistaFinta(),
            clock = Clock.systemUTC(),
        )
    }

    private fun RegistrazioneUiStato?.contenutoUnicaCarta(): ContenutoCarta? =
        assertIs<RegistrazioneUiStato.Dati>(this).pannello?.carte?.single()?.contenuto

    @Test
    fun `L665a una lettura vecchia che si conclude per ultima non sovrascrive quella nuova gia applicata`() {
        val eseguitori = Executors.newFixedThreadPool(4)
        val ioReale = eseguitori.asCoroutineDispatcher()
        try {
            val entrataVecchia = CountDownLatch(1)
            val viaLiberaVecchia = CountDownLatch(1)
            val vecchiaConclusa = CountDownLatch(1)
            val nuovaConclusa = CountDownLatch(1)
            val trascritto = TRASCRITTO_UNA_VOCE
            val marco = VoceIdentificata(V1, MARCO.parlanteId, MARCO.nome, MARCO.tipoParlante)
            val fresca = ContenutoCarta.Attribuita(MARCO.parlanteId, MARCO.nome, MARCO.tipoParlante)
            val statoFlow = unoStatoVuoto(trascritto)
            val progetto = CoroutineScope(SupervisorJob() + ioReale)
            val sorgenti = unaSorgenteConLetturaVecchiaBloccata(progetto, entrataVecchia, viaLiberaVecchia, marco)
            val voci = StatoVoci(sorgenti, progetto, ioReale, REG, { trascritto }, statoFlow) { emptyList() }
            voci.vista = trascritto

            progetto.launch {
                voci.ricaricaParlanti()
                vecchiaConclusa.countDown()
            }
            assertTrue(entrataVecchia.await(5, TimeUnit.SECONDS), "la lettura vecchia non e partita")

            progetto.launch {
                voci.ricaricaParlanti()
                nuovaConclusa.countDown()
            }
            assertTrue(nuovaConclusa.await(5, TimeUnit.SECONDS), "la lettura nuova non si e conclusa")
            assertEquals(fresca, statoFlow.value.contenutoUnicaCarta())

            // Release the OLD read only NOW — strictly after the NEW one already won.
            viaLiberaVecchia.countDown()
            assertTrue(vecchiaConclusa.await(5, TimeUnit.SECONDS), "la lettura vecchia non si e conclusa")

            // L665a: the OLD read finishing LAST must never overwrite the fresher one already applied.
            assertEquals(fresca, statoFlow.value.contenutoUnicaCarta())
        } finally {
            eseguitori.shutdownNow()
        }
    }
}
