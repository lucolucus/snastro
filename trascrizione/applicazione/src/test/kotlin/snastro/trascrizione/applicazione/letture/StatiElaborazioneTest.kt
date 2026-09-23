package snastro.trascrizione.applicazione.letture

import snastro.kernel.ElaborazioneId
import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.applicazione.porte.FaseElaborazione.DIARIZZAZIONE
import snastro.trascrizione.applicazione.porte.TrascrittoRepositoryFinta
import snastro.trascrizione.dominio.NumeroPersone
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.trascrizione.dominio.StatoElaborazione.COMPLETATA
import snastro.trascrizione.dominio.StatoElaborazione.FALLITA
import snastro.trascrizione.dominio.StatoElaborazione.IN_ATTESA
import snastro.trascrizione.dominio.StatoElaborazione.IN_CORSO
import snastro.trascrizione.dominio.unTrascritto
import snastro.trascrizione.dominio.unaElaborazione
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StatiElaborazioneTest {
    private val elaborazioni = ElaborazioneRepositoryFinta()
    private val trascritti = TrascrittoRepositoryFinta()
    private val fasi = FasiInCorso()
    private val stati = StatiElaborazione(elaborazioni, trascritti, fasi)

    @Test
    fun `AC-162 una Registrazione senza Elaborazione e non avviata con ogni altro campo nullo`() {
        val riga = stati.stati(listOf(REGISTRAZIONE)).single()

        assertEquals(
            StatoRegistrazioneVista(
                REGISTRAZIONE,
                StatoElaborazioneVista.NON_AVVIATA,
                fase = null,
                avviataAlle = null,
                motivoFallimento = null,
                posizioneInCoda = null,
                numVoci = null,
                numeroPersone = null,
            ),
            riga,
        )
    }

    @Test
    fun `AC-162 numeroPersone e quello dell ultima Elaborazione in ogni stato e nullo se assente`() {
        val registrazioni = StatoElaborazione.entries.map { RegistrazioneId("registrazione-${it.name}") }
        StatoElaborazione.entries.zip(registrazioni).forEachIndexed { i, (stato, id) ->
            val numero = NumeroPersone.di(i + 2).atteso()
            elaborazioni.salva(unaElaborazione(stato, id = idDi("el-$i"), registrazioneId = id, numeroPersone = numero))
        }
        val senzaNumero = RegistrazioneId("registrazione-senza-numero")
        elaborazioni.salva(unaElaborazione(FALLITA, id = idDi("el-senza"), registrazioneId = senzaNumero))

        val righe = stati.stati(registrazioni + senzaNumero + REGISTRAZIONE)

        assertEquals(listOf(2, 3, 4, 5, null, null), righe.map { it.numeroPersone })
    }

    @Test
    fun `AC-162 numeroPersone viene dall ultima Elaborazione, non da una fallita precedente`() {
        val tre = NumeroPersone.di(3).atteso()
        elaborazioni.salva(
            unaElaborazione(FALLITA, idDi("el-1"), REGISTRAZIONE, creataAlle = t(0), numeroPersone = tre),
        )
        elaborazioni.salva(unaElaborazione(IN_ATTESA, idDi("el-2"), REGISTRAZIONE, creataAlle = t(1)))

        assertNull(stati.stati(listOf(REGISTRAZIONE)).single().numeroPersone)
    }

    @Test
    fun `AC-162 restituisce una riga per ogni id richiesto nello stesso ordine`() {
        val altra = RegistrazioneId("registrazione-9")

        val righe = stati.stati(listOf(altra, REGISTRAZIONE))

        assertEquals(listOf(altra, REGISTRAZIONE), righe.map { it.registrazioneId })
    }

    @Test
    fun `AC-163 posizioneInCoda segue l ordine FIFO delle in_attesa, 1 e la prossima`() {
        val prima = RegistrazioneId("registrazione-1")
        val seconda = RegistrazioneId("registrazione-2")
        val terza = RegistrazioneId("registrazione-3")
        elaborazioni.salva(unaElaborazione(IN_ATTESA, id = idDi("el-1"), registrazioneId = prima, creataAlle = t(0)))
        elaborazioni.salva(unaElaborazione(IN_ATTESA, id = idDi("el-2"), registrazioneId = seconda, creataAlle = t(1)))
        elaborazioni.salva(unaElaborazione(IN_ATTESA, id = idDi("el-3"), registrazioneId = terza, creataAlle = t(2)))

        val righe = stati.stati(listOf(terza, prima, seconda)).associateBy { it.registrazioneId }

        assertEquals(1, righe.getValue(prima).posizioneInCoda)
        assertEquals(2, righe.getValue(seconda).posizioneInCoda)
        assertEquals(3, righe.getValue(terza).posizioneInCoda)
        righe.values.forEach { assertEquals(StatoElaborazioneVista.IN_ATTESA, it.stato) }
    }

    @Test
    fun `AC-164 fase e presente solo per in_corso e riflette l ultima fase segnalata`() {
        elaborazioni.salva(unaElaborazione(IN_CORSO, registrazioneId = REGISTRAZIONE))
        fasi.fase(REGISTRAZIONE, DIARIZZAZIONE)

        val riga = stati.stati(listOf(REGISTRAZIONE)).single()

        assertEquals(StatoElaborazioneVista.IN_CORSO, riga.stato)
        assertEquals(DIARIZZAZIONE, riga.fase)
    }

    @Test
    fun `AC-164 fase scompare quando l Elaborazione termina anche se FasiInCorso non e stata ripulita`() {
        elaborazioni.salva(unaElaborazione(COMPLETATA, registrazioneId = REGISTRAZIONE))
        fasi.fase(REGISTRAZIONE, DIARIZZAZIONE) // segnale rimasto per una race con terminata(): ignorato dalla vista

        val riga = stati.stati(listOf(REGISTRAZIONE)).single()

        assertEquals(StatoElaborazioneVista.COMPLETATA, riga.stato)
        assertNull(riga.fase)
    }

    @Test
    fun `AC-165 numVoci e presente solo per completata e conta le Voci del Trascritto`() {
        elaborazioni.salva(unaElaborazione(COMPLETATA, registrazioneId = REGISTRAZIONE))
        trascritti.salva(unTrascritto(voci = 3, segmentiPerVoce = 1, registrazioneId = REGISTRAZIONE))

        val riga = stati.stati(listOf(REGISTRAZIONE)).single()

        assertEquals(3, riga.numVoci)
    }

    @Test
    fun `AC-165 numVoci resta nullo per in_attesa, in_corso, fallita e non avviata`() {
        val inAttesa = RegistrazioneId("registrazione-attesa")
        val inCorso = RegistrazioneId("registrazione-corso")
        val fallitaId = RegistrazioneId("registrazione-fallita")
        val nonAvviata = RegistrazioneId("registrazione-nessuna")
        elaborazioni.salva(unaElaborazione(IN_ATTESA, id = idDi("el-attesa"), registrazioneId = inAttesa))
        elaborazioni.salva(unaElaborazione(IN_CORSO, id = idDi("el-corso"), registrazioneId = inCorso))
        elaborazioni.salva(unaElaborazione(FALLITA, id = idDi("el-fallita"), registrazioneId = fallitaId))

        val righe = stati.stati(listOf(inAttesa, inCorso, fallitaId, nonAvviata))

        righe.forEach { assertNull(it.numVoci) }
    }

    @Test
    fun `motivoFallimento e presente solo per fallita ed e passato invariato`() {
        val motivo = "impossibile leggere l'audio"
        elaborazioni.salva(unaElaborazione(FALLITA, registrazioneId = REGISTRAZIONE, motivo = motivo))

        val riga = stati.stati(listOf(REGISTRAZIONE)).single()

        assertEquals(StatoElaborazioneVista.FALLITA, riga.stato)
        assertEquals(motivo, riga.motivoFallimento)
        assertNull(riga.fase)
        assertNull(riga.posizioneInCoda)
    }

    @Test
    fun `la vista si basa sull ultima Elaborazione per creataAlle, un Riprova dopo una fallita vince`() {
        elaborazioni.salva(
            unaElaborazione(FALLITA, id = idDi("el-vecchia"), registrazioneId = REGISTRAZIONE, creataAlle = t(0)),
        )
        elaborazioni.salva(
            unaElaborazione(IN_ATTESA, id = idDi("el-nuova"), registrazioneId = REGISTRAZIONE, creataAlle = t(1)),
        )

        val riga = stati.stati(listOf(REGISTRAZIONE)).single()

        assertEquals(StatoElaborazioneVista.IN_ATTESA, riga.stato)
    }

    @Test
    fun `a parita di creataAlle il pareggio e per id, deterministico`() {
        elaborazioni.salva(
            unaElaborazione(
                FALLITA,
                id = idDi("el-a"),
                registrazioneId = REGISTRAZIONE,
                creataAlle = t(0),
                motivo = "prima",
            ),
        )
        elaborazioni.salva(
            unaElaborazione(
                FALLITA,
                id = idDi("el-b"),
                registrazioneId = REGISTRAZIONE,
                creataAlle = t(0),
                motivo = "seconda",
            ),
        )

        val riga = stati.stati(listOf(REGISTRAZIONE)).single()

        assertEquals("seconda", riga.motivoFallimento) // "el-b" > "el-a"
    }

    private companion object {
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        fun idDi(valore: String) = ElaborazioneId(valore)
        fun t(secondi: Long): Instant = Instant.parse("2026-09-23T10:00:00Z").plusSeconds(secondi)
    }
}
