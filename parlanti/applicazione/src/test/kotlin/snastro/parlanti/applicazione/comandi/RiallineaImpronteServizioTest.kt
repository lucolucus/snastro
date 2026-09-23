package snastro.parlanti.applicazione.comandi

import io.mockk.spyk
import io.mockk.verify
import snastro.kernel.CampioniAudio
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.Esito
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.parlanti.applicazione.eventi.ImpronteRiallineate
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.parlanti.applicazione.porte.LettoreVociFinta
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
import snastro.parlanti.applicazione.porte.RigaImpronta
import snastro.parlanti.applicazione.porte.VoceVista
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.ImprontaVocale
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.SorgenteImpronta
import snastro.parlanti.dominio.TipoParlante
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * [RiallineaImpronteServizio] against the ports' fakes (D1): AC-292..AC-299, AC-301 and the freshness half of
 * INV-15 (ADR 0012 Amendment (b) point 3). The ML Finte are built WITH the unit of work, so any decoding or
 * extraction inside a transaction throws (AC-297). MockK only spies the decoder to count calls (RC-9).
 */
class RiallineaImpronteServizioTest {
    private val parlanti = ParlanteRepositoryFinta()
    private val transazioni = UnitaDiLavoroFinta(parlanti)
    private val eventi = DispatcherEventiFinta(transazioni)
    private val decodificatore = spyk(DecodificatoreAudioFinta(unitaDiLavoro = transazioni))
    private val estrattore = EstrattoreImprontaFinta(unitaDiLavoro = transazioni)
    private val voci = mutableMapOf(REGISTRAZIONE to listOf(unaVoce(1, I1), unaVoce(2, I2), unaVoce(3, I3)))

    private fun servizio(
        decoder: DecodificatoreAudio = decodificatore,
        estrattore: EstrattoreImpronta = this.estrattore,
    ): RiallineaImpronteServizio =
        RiallineaImpronteServizio(eventi.unitaDiLavoro, LettoreVociFinta(voci), parlanti, decoder, estrattore, eventi)

    private fun esegui(servizio: RiallineaImpronteServizio = servizio()) =
        servizio.esegui(RiallineaImpronte(REGISTRAZIONE))

    /** Seeds a Parlante [id] holding one print per (voce, sorgente, modello). */
    private fun unParlanteConImpronte(id: String, vararg impronte: Triple<Int, String, String>): ParlanteId {
        val p = Parlante.crea(ParlanteId(id), PROGETTO, Nome.di(id).atteso(), TipoParlante.RICORRENTE).aggregato
        impronte.forEach { (voce, sorgente, modello) ->
            p.registraImpronta(ref(voce), VECCHIA, sorgente, modello).atteso()
        }
        parlanti.salva(p).atteso()
        return p.id
    }

    private fun impronta(id: ParlanteId, voce: Int): ImprontaVocale =
        assertNotNull(parlanti.trova(id)).impronte.single { it.voceRef == ref(voce) }

    private fun attesa(intervalli: List<IntervalloMs>): Impronta =
        EstrattoreImprontaFinta().estrai(DecodificatoreAudioFinta().campioni(REGISTRAZIONE, intervalli))

    private fun riallineate(): List<ImpronteRiallineate> = eventi.pubblicati.filterIsInstance<ImpronteRiallineate>()

    @Test
    fun `INV-15 solo le righe a sorgente o modello non correnti sono riderivate e nessuna riga e inserita`() {
        val id = unParlanteConImpronte(
            "Marco",
            Triple(1, chiave(I1), MODELLO),
            Triple(2, chiave(I1), MODELLO),
            Triple(3, chiave(I3), "altro-modello"),
        )
        val altro = unParlanteConImpronte("Anna")

        esegui().atteso()

        assertEquals(VECCHIA, impronta(id, 1).impronta, "fresca: sorgente e modello correnti")
        assertEquals(ImprontaVocale(ref(2), attesa(I2), chiave(I2), MODELLO), impronta(id, 2))
        assertEquals(ImprontaVocale(ref(3), attesa(I3), chiave(I3), MODELLO), impronta(id, 3))
        assertEquals(3, parlanti.righeImpronte(id))
        assertEquals(0, parlanti.righeImpronte(altro), "nessuna riga creata per chi non ne aveva")
    }

    @Test
    fun `AC-292 con una riga fresca una a sorgente cambiata e una a modello diverso decodifica solo le ultime due`() {
        val id = unParlanteConImpronte(
            "Marco",
            Triple(1, chiave(I1), MODELLO),
            Triple(2, "0-1000", MODELLO),
            Triple(3, chiave(I3), "altro-modello"),
        )

        esegui().atteso()

        verify(exactly = 0) { decodificatore.campioni(REGISTRAZIONE, I1) }
        verify(exactly = 1) { decodificatore.campioni(REGISTRAZIONE, I2) }
        verify(exactly = 1) { decodificatore.campioni(REGISTRAZIONE, I3) }
        assertEquals(chiave(I2), impronta(id, 2).sorgente)
        assertEquals(MODELLO, impronta(id, 3).modello)
    }

    @Test
    fun `AC-292 decodifica solo la SorgenteImpronta limitata della Voce non tutti i suoi intervalli`() {
        val lunghi = listOf(IntervalloMs(0, 25_000), IntervalloMs(30_000, 50_000))
        voci[REGISTRAZIONE] = listOf(unaVoce(1, lunghi))
        val id = unParlanteConImpronte("Marco", Triple(1, "0-1000", MODELLO))
        val sorgente = SorgenteImpronta.di(lunghi)

        esegui().atteso()

        verify(exactly = 1) { decodificatore.campioni(REGISTRAZIONE, sorgente.intervalli) }
        assertEquals(ImprontaVocale(ref(1), attesa(sorgente.intervalli), sorgente.chiave, MODELLO), impronta(id, 1))
    }

    @Test
    fun `AC-293 la scrittura e un compare-and-set in una transazione breve e il numero di righe non cambia`() {
        val id = unParlanteConImpronte("Marco", Triple(2, "0-1000", MODELLO), Triple(3, "0-1000", MODELLO))
        val osservato = ParlanteRepositoryOsservato(parlanti) { transazioni.transazioneAperta }
        val servizio = RiallineaImpronteServizio(
            eventi.unitaDiLavoro,
            LettoreVociFinta(voci),
            osservato,
            decodificatore,
            estrattore,
            eventi,
        )

        servizio.esegui(RiallineaImpronte(REGISTRAZIONE)).atteso()

        assertEquals(listOf(true, true), osservato.aggiornamentiInTransazione, "uno per Voce, in transazione")
        assertEquals(0, osservato.salvataggi, "mai salva (che inserirebbe): solo aggiornaImpronta")
        assertEquals(2, parlanti.righeImpronte(id))
        assertEquals(chiave(I2), impronta(id, 2).sorgente)
        assertEquals(chiave(I3), impronta(id, 3).sorgente)
    }

    @Test
    fun `AC-294 una riga cancellata tra estrazione e scrittura non e resuscitata`() {
        val id = unParlanteConImpronte("Marco", Triple(2, "0-1000", MODELLO))
        val cancellaDurante = EstrattoreConEffetto(estrattore) {
            val p = assertNotNull(parlanti.trova(id))
            p.rimuoviImpronta(ref(2))
            parlanti.salva(p).atteso()
        }

        esegui(servizio(estrattore = cancellaDurante)).atteso()

        assertEquals(0, parlanti.righeImpronte(id), "nessuna riga creata")
        assertEquals(emptyList(), riallineate())
    }

    @Test
    fun `AC-294 un Parlante rimosso tra estrazione e scrittura non riappare`() {
        val id = unParlanteConImpronte("Marco", Triple(2, "0-1000", MODELLO))
        val rimuoviDurante = EstrattoreConEffetto(estrattore) { parlanti.rimuovi(id) }

        esegui(servizio(estrattore = rimuoviDurante)).atteso()

        assertEquals(null, parlanti.trova(id))
        assertEquals(emptyList(), riallineate())
    }

    @Test
    fun `AC-295 se la Voce cambia durante l estrazione non scrive nulla e una seconda esecuzione converge`() {
        val id = unParlanteConImpronte("Marco", Triple(2, "0-1000", MODELLO))
        val nuovi = listOf(IntervalloMs(10_000, 12_000), IntervalloMs(14_000, 16_000))
        val cambiaDurante = EstrattoreConEffetto(estrattore) {
            voci[REGISTRAZIONE] = listOf(unaVoce(1, I1), unaVoce(2, nuovi), unaVoce(3, I3))
        }

        esegui(servizio(estrattore = cambiaDurante)).atteso()

        assertEquals(ImprontaVocale(ref(2), VECCHIA, "0-1000", MODELLO), impronta(id, 2))
        assertEquals(emptyList(), riallineate())

        esegui().atteso()

        assertEquals(ImprontaVocale(ref(2), attesa(nuovi), chiave(nuovi), MODELLO), impronta(id, 2))
        assertEquals(listOf(ImpronteRiallineate(REGISTRAZIONE)), riallineate())
    }

    @Test
    fun `AC-296 una seconda esecuzione subito dopo non decodifica non estrae non scrive e non pubblica`() {
        val id = unParlanteConImpronte("Marco", Triple(2, "0-1000", MODELLO), Triple(3, chiave(I3), "altro"))
        esegui().atteso()
        val dopoPrima = assertNotNull(parlanti.trova(id)).impronte
        val eventiDopoPrima = eventi.pubblicati.size
        val estrattoreDopo = spyk(estrattore)

        esegui(servizio(estrattore = estrattoreDopo)).atteso()

        verify(exactly = 1) { decodificatore.campioni(REGISTRAZIONE, I2) }
        verify(exactly = 1) { decodificatore.campioni(REGISTRAZIONE, I3) }
        verify(exactly = 0) { estrattoreDopo.estrai(any()) }
        assertEquals(dopoPrima, assertNotNull(parlanti.trova(id)).impronte)
        assertEquals(eventiDopoPrima, eventi.pubblicati.size)
    }

    @Test
    fun `AC-297 decodifica ed estrazione avvengono senza transazione aperta`() {
        val id = unParlanteConImpronte("Marco", Triple(1, "0-1000", MODELLO), Triple(2, chiave(I2), "altro"))
        val aperta = mutableListOf<Boolean>()
        val osserva = EstrattoreConEffetto(estrattore) { aperta += transazioni.transazioneAperta }

        esegui(servizio(estrattore = osserva)).atteso()

        assertEquals(listOf(false, false), aperta)
        assertEquals(chiave(I1), impronta(id, 1).sorgente)
        assertEquals(MODELLO, impronta(id, 2).modello)
    }

    @Test
    fun `AC-298 pubblica ImpronteRiallineate una volta dopo il commit se almeno una riga e aggiornata`() {
        unParlanteConImpronte("Marco", Triple(2, "0-1000", MODELLO))
        unParlanteConImpronte("Anna", Triple(3, "0-1000", MODELLO))

        esegui().atteso()

        assertEquals(listOf(ImpronteRiallineate(REGISTRAZIONE)), riallineate())
    }

    @Test
    fun `AC-298 non pubblica nulla se nessuna riga e obsoleta`() {
        unParlanteConImpronte("Marco", Triple(1, chiave(I1), MODELLO))

        esegui().atteso()

        assertEquals(emptyList(), eventi.pubblicati)
        verify(exactly = 0) { decodificatore.campioni(any(), any()) }
    }

    @Test
    fun `AC-299 una riga la cui Voce non esiste piu e saltata senza errore e senza scritture`() {
        val id = unParlanteConImpronte("Marco", Triple(9, "0-1000", MODELLO))

        esegui().atteso()

        val intatta = ImprontaVocale(ref(9), VECCHIA, "0-1000", MODELLO)
        assertEquals(intatta, impronta(id, 9), "la rimozione spetta alla revisione")
        verify(exactly = 0) { decodificatore.campioni(any(), any()) }
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-299 una Registrazione senza Trascritto e saltata senza errore e senza scritture`() {
        val id = unParlanteConImpronte("Marco", Triple(2, "0-1000", MODELLO))
        voci.clear()

        esegui().atteso()

        assertEquals(ImprontaVocale(ref(2), VECCHIA, "0-1000", MODELLO), impronta(id, 2))
        verify(exactly = 0) { decodificatore.campioni(any(), any()) }
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-301 un eccezione di estrazione si propaga dopo il commit delle Voci gia riallineate e nulla e scritto`() {
        val id = unParlanteConImpronte("Marco", Triple(1, "0-1000", MODELLO), Triple(2, "0-1000", MODELLO))
        val guasto = IllegalStateException("modello illeggibile")
        val fallisceSullaSeconda = object : EstrattoreImpronta by estrattore {
            private var chiamate = 0

            override fun estrai(c: CampioniAudio): Impronta {
                chiamate++
                if (chiamate == 2) throw guasto
                return estrattore.estrai(c)
            }
        }

        val lanciata = assertFailsWith<IllegalStateException> { esegui(servizio(estrattore = fallisceSullaSeconda)) }

        assertEquals(guasto, lanciata)
        assertEquals(ImprontaVocale(ref(1), attesa(I1), chiave(I1), MODELLO), impronta(id, 1), "Voce 1 committata")
        assertEquals(ImprontaVocale(ref(2), VECCHIA, "0-1000", MODELLO), impronta(id, 2), "Voce 2 intatta")
        assertEquals(listOf(ImpronteRiallineate(REGISTRAZIONE)), riallineate(), "la Voce 1 cambiata e annunciata")
    }

    @Test
    fun `AC-301 un eccezione di decodifica sulla prima Voce si propaga senza scritture ne eventi`() {
        val id = unParlanteConImpronte("Marco", Triple(1, "0-1000", MODELLO))
        val guasto = IllegalStateException("audio mancante")
        val rotto = object : DecodificatoreAudio {
            override fun campioni(id: RegistrazioneId, intervalli: List<IntervalloMs>): CampioniAudio = throw guasto
        }

        assertEquals(guasto, assertFailsWith<IllegalStateException> { esegui(servizio(decoder = rotto)) })

        assertEquals(ImprontaVocale(ref(1), VECCHIA, "0-1000", MODELLO), impronta(id, 1))
        assertEquals(emptyList(), eventi.pubblicati)
    }

    /** Runs [effetto] (the concurrent change under test) right before delegating the extraction to [finta]. */
    private class EstrattoreConEffetto(
        private val finta: EstrattoreImpronta,
        private val effetto: () -> Unit,
    ) : EstrattoreImpronta by finta {
        override fun estrai(c: CampioniAudio): Impronta {
            effetto()
            return finta.estrai(c)
        }
    }

    /** Records, per write, whether a transaction was open; delegates everything to [finta]. */
    private class ParlanteRepositoryOsservato(
        private val finta: ParlanteRepository,
        private val transazioneAperta: () -> Boolean,
    ) : ParlanteRepository by finta {
        val aggiornamentiInTransazione = mutableListOf<Boolean>()
        var salvataggi = 0

        override fun salva(p: Parlante): Esito<Unit> {
            salvataggi++
            return finta.salva(p)
        }

        override fun aggiornaImpronta(
            attesa: RigaImpronta,
            impronta: Impronta,
            sorgente: String,
            modello: String,
        ): Boolean {
            aggiornamentiInTransazione += transazioneAperta()
            return finta.aggiornaImpronta(attesa, impronta, sorgente, modello)
        }
    }

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        const val MODELLO = EstrattoreImprontaFinta.MODELLO
        val VECCHIA = Impronta(floatArrayOf(9f, 9f, 9f, 9f, 9f, 9f, 9f, 9f))
        val I1 = listOf(IntervalloMs(0, 2_000), IntervalloMs(5_000, 8_000))
        val I2 = listOf(IntervalloMs(10_000, 13_000))
        val I3 = listOf(IntervalloMs(20_000, 22_000))

        fun ref(voce: Int): VoceRef = VoceRef(REGISTRAZIONE, VoceId(voce))

        fun unaVoce(voce: Int, intervalli: List<IntervalloMs>): VoceVista = VoceVista(ref(voce), intervalli)

        fun chiave(intervalli: List<IntervalloMs>): String = SorgenteImpronta.di(intervalli).chiave
    }
}
