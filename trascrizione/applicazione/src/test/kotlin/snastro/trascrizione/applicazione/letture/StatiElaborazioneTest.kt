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
                trascrittoDisponibile = false,
                elaborazioneId = null,
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
    fun `AC-165 numVoci conta le Voci del Trascritto quando esiste`() {
        elaborazioni.salva(unaElaborazione(COMPLETATA, registrazioneId = REGISTRAZIONE))
        trascritti.salva(unTrascritto(voci = 3, segmentiPerVoce = 1, registrazioneId = REGISTRAZIONE))

        val riga = stati.stati(listOf(REGISTRAZIONE)).single()

        assertEquals(3, riga.numVoci)
    }

    @Test
    fun `AC-165 senza Trascritto numVoci e nullo per in_attesa, in_corso, fallita e non avviata`() {
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

    // --- ADR 0018 (+ Amendment (b)): trascrittoDisponibile, elaborazioneId, numVoci follows the Trascritto ---

    @Test
    fun `AC-447 stato e campi dall ULTIMA Elaborazione, trascrittoDisponibile e numVoci dal Trascritto`() {
        fasi.fase(r("completata-in-corso"), DIARIZZAZIONE)
        storia("completata", COMPLETATA, trascritto = 3)
        storia("completata-in-attesa", COMPLETATA, IN_ATTESA, trascritto = 3)
        storia("completata-in-corso", COMPLETATA, IN_CORSO, trascritto = 3)
        storia("completata-fallita", COMPLETATA, FALLITA, trascritto = 3)
        storia("fallita", FALLITA)
        storia("completata-completata", COMPLETATA, COMPLETATA, trascritto = 2)

        val righe = stati.stati(CASI.map(::r) + r("nessuna")).associateBy { it.registrazioneId.valore }

        fun atteso(caso: String) = righe.getValue("registrazione-$caso").let {
            listOf(it.stato, it.trascrittoDisponibile, it.numVoci, it.posizioneInCoda, it.fase, it.motivoFallimento)
        }
        val v = StatoElaborazioneVista.entries.associateBy { it.name }
        assertEquals(listOf(v["COMPLETATA"], true, 3, null, null, null), atteso("completata"))
        assertEquals(listOf(v["IN_ATTESA"], true, 3, 1, null, null), atteso("completata-in-attesa"))
        assertEquals(listOf(v["IN_CORSO"], true, 3, null, DIARIZZAZIONE, null), atteso("completata-in-corso"))
        assertEquals(listOf(v["FALLITA"], true, 3, null, null, MOTIVO), atteso("completata-fallita"))
        assertEquals(listOf(v["FALLITA"], false, null, null, null, MOTIVO), atteso("fallita"))
        assertEquals(listOf(v["COMPLETATA"], true, 2, null, null, null), atteso("completata-completata"))
        assertEquals(listOf(v["NON_AVVIATA"], false, null, null, null, null), atteso("nessuna"))
    }

    @Test
    fun `AC-474 elaborazioneId e l id dell ultima Elaborazione e nullo per NON_AVVIATA`() {
        storia("completata-in-attesa", COMPLETATA, IN_ATTESA, trascritto = 3)

        val righe = stati.stati(listOf(r("completata-in-attesa"), r("nessuna")))

        assertEquals(listOf(idDi("completata-in-attesa-1"), null), righe.map { it.elaborazioneId })
    }

    @Test
    fun `AC-474 dopo un annullamento la riga torna allo stato della sua ultima Elaborazione rimasta`() {
        storia("prima", IN_ATTESA)
        storia("ritrascrizione", COMPLETATA, IN_ATTESA, trascritto = 4)
        storia("riprova", FALLITA, IN_ATTESA)
        elaborazioni.salva(unaElaborazione(IN_ATTESA, idDi("in-coda-0"), r("in-coda"), creataAlle = t(5))).atteso()
        assertEquals(4, stati.stati(listOf(r("in-coda"))).single().posizioneInCoda, "prima: dietro tre in coda")
        listOf("prima-0", "ritrascrizione-1", "riprova-1").forEach { elaborazioni.rimuoviInAttesa(idDi(it)).atteso() }

        val righe = stati.stati(listOf("prima", "ritrascrizione", "riprova", "in-coda").map(::r))

        val campi = righe.map {
            listOf(it.stato, it.trascrittoDisponibile, it.numVoci, it.elaborazioneId, it.motivoFallimento)
        }
        val v = StatoElaborazioneVista.entries.associateBy { it.name }
        assertEquals(listOf(v["NON_AVVIATA"], false, null, null, null), campi[0], "prima trascrizione annullata")
        val ritrascrizione = listOf(v["COMPLETATA"], true, 4, idDi("ritrascrizione-0"), null)
        assertEquals(ritrascrizione, campi[1], "Ritrascrivi annullato")
        assertEquals(listOf(v["FALLITA"], false, null, idDi("riprova-0"), MOTIVO), campi[2], "Riprova annullata")
        assertEquals(1, righe[3].posizioneInCoda, "la coda rimasta e rinumerata da 1")
    }

    /**
     * A Registrazione `registrazione-<caso>` whose Elaborazioni `<caso>-0`, `<caso>-1`, … have [stati], in creation
     * order; with [trascritto] Voci, it also has a Trascritto.
     */
    private fun storia(caso: String, vararg stati: StatoElaborazione, trascritto: Int? = null) {
        stati.forEachIndexed { i, stato ->
            elaborazioni.salva(
                unaElaborazione(stato, idDi("$caso-$i"), r(caso), creataAlle = t(i.toLong()), motivo = MOTIVO),
            ).atteso()
        }
        trascritto?.let { trascritti.salva(unTrascritto(voci = it, segmentiPerVoce = 1, registrazioneId = r(caso))) }
    }

    private companion object {
        const val MOTIVO = "impossibile leggere l'audio"
        val CASI = listOf(
            "completata",
            "completata-in-attesa",
            "completata-in-corso",
            "completata-fallita",
            "fallita",
            "completata-completata",
        )

        fun r(caso: String) = RegistrazioneId("registrazione-$caso")

        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        fun idDi(valore: String) = ElaborazioneId(valore)
        fun t(secondi: Long): Instant = Instant.parse("2026-09-23T10:00:00Z").plusSeconds(secondi)
    }
}
