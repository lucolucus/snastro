package snastro.parlanti.applicazione.comandi

import snastro.kernel.CampioniAudio
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.parlanti.applicazione.eventi.AttribuzioneConfermata
import snastro.parlanti.applicazione.eventi.ImpronteRiallineate
import snastro.parlanti.applicazione.porte.AttribuzioneRepositoryFinta
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.parlanti.applicazione.porte.LettoreRegistrazione
import snastro.parlanti.applicazione.porte.LettoreRegistrazioneFinta
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.applicazione.porte.LettoreVociFinta
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
import snastro.parlanti.applicazione.porte.ParteDiIncontroParlanti
import snastro.parlanti.applicazione.porte.RegistrazioneVista
import snastro.parlanti.applicazione.porte.SegmentoDiVoce
import snastro.parlanti.applicazione.porte.VoceVista
import snastro.parlanti.dominio.ErroreParlanti
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.SorgenteImpronta
import snastro.parlanti.dominio.TipoParlante
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * ConfermaAttribuzione / SaltaVoce / RiallineaImpronte on an Incontro of TWO Parti (ADR 0035 §6, [INV-I8], [INV-19]):
 * the Incontro is built on the fakes of the ports, so it is the same in every supplier.
 */
class AttribuzioneIncontroTest {
    private val parlanti = ParlanteRepositoryFinta()
    private val attribuzioni = AttribuzioneRepositoryFinta()
    private val uow = UnitaDiLavoroFinta(parlanti, attribuzioni)
    private val eventi = DispatcherEventiFinta(uow)
    private val decodificate = mutableListOf<Pair<RegistrazioneId, List<IntervalloMs>>>()
    private val decodificatore = object : DecodificatoreAudio {
        private val finto = DecodificatoreAudioFinta(uow)

        override fun campioni(id: RegistrazioneId, intervalli: List<IntervalloMs>): CampioniAudio {
            decodificate += id to intervalli
            return finto.campioni(id, intervalli)
        }
    }
    private val estrattore = EstrattoreImprontaFinta(unitaDiLavoro = uow, modello = "modello-x")

    private val registrazioni = mutableMapOf(A to vista(A, DATA_A), B to vista(B, DATA_B))
    private val voci = mutableMapOf(
        INCONTRO to listOf(
            VoceVista(voce(1), mapOf(A to listOf(IntervalloMs(0, 1_000)), B to listOf(IntervalloMs(2_000, 4_000)))),
            VoceVista(voce(2), mapOf(B to listOf(IntervalloMs(5_000, 6_000)))),
        ),
    )

    private fun lettoreRegistrazione() = LettoreRegistrazioneFinta(registrazioni)

    private fun lettoreVoci() = LettoreVociFinta(voci)

    private fun conferma(
        lettore: LettoreVoci = lettoreVoci(),
        catalogo: LettoreRegistrazione = lettoreRegistrazione(),
    ) = ConfermaAttribuzioneServizio(
        eventi.unitaDiLavoro, GeneratoreIdFinto(), catalogo, lettore, parlanti, attribuzioni,
        decodificatore, estrattore, eventi,
    )

    private val generatore = GeneratoreIdFinto()

    private fun salta(lettore: LettoreVoci = lettoreVoci()) = SaltaVoceServizio(
        eventi.unitaDiLavoro, generatore, parlanti, attribuzioni, lettoreRegistrazione(), lettore,
        decodificatore, estrattore, eventi,
    )

    private fun riallinea() = RiallineaImpronteServizio(
        eventi.unitaDiLavoro,
        lettoreVoci(),
        lettoreRegistrazione(),
        parlanti,
        decodificatore,
        estrattore,
        eventi,
    )

    private fun unParlante(id: String, progetto: ProgettoId = PROGETTO): Parlante =
        Parlante.crea(ParlanteId(id), progetto, Nome.di(id).atteso(), TipoParlante.RICORRENTE).aggregato
            .also { parlanti.salva(it).atteso() }

    private fun chiave(vararg intervalli: IntervalloMs) = SorgenteImpronta.di(intervalli.toList()).chiave

    @Test
    fun `INV-I8 ConfermaAttribuzione di una Voce che parla in A e in B scrive una impronta per Parte`() {
        val anna = unParlante("Anna")

        conferma().esegui(ConfermaAttribuzione(voce(1), ObiettivoAttribuzione.ParlanteEsistente(anna.id))).atteso()

        assertEquals(anna.id, attribuzioni.trova(voce(1))?.parlanteId)
        val impronte = assertNotNull(parlanti.trova(anna.id)).impronte
        assertEquals(listOf(A, B), impronte.map { it.parte })
        assertEquals(setOf(voce(1)), impronte.map { it.voceRef }.toSet())
        assertEquals(chiave(IntervalloMs(0, 1_000)), impronte.single { it.parte == A }.sorgente)
        assertEquals(chiave(IntervalloMs(2_000, 4_000)), impronte.single { it.parte == B }.sorgente)
        assertEquals(listOf(A to listOf(IntervalloMs(0, 1_000)), B to listOf(IntervalloMs(2_000, 4_000))), decodificate)
    }

    @Test
    fun `INV-I8 una Voce solo in B ha una sola impronta, della Parte B`() {
        val anna = unParlante("Anna")

        conferma().esegui(ConfermaAttribuzione(voce(2), ObiettivoAttribuzione.ParlanteEsistente(anna.id))).atteso()

        assertEquals(listOf(B), assertNotNull(parlanti.trova(anna.id)).impronte.map { it.parte })
        assertEquals(listOf(B), decodificate.map { it.first })
    }

    @Test
    fun `AC-282 se la sorgente di una sola Parte cambia e VoceCambiata e nulla e scritto`() {
        val anna = unParlante("Anna")
        val cambiaB = object : LettoreVoci {
            private var letture = 0
            private val dopo = conB(voce(1), IntervalloMs(2_000, 4_500))

            override fun voci(incontroId: IncontroId) = if (letture++ == 0) lettoreVoci().voci(incontroId) else dopo

            override fun segmenti(incontroId: IncontroId): List<SegmentoDiVoce> = error("non usato")
        }

        val errore = conferma(cambiaB)
            .esegui(ConfermaAttribuzione(voce(1), ObiettivoAttribuzione.ParlanteEsistente(anna.id)))
            .erroreAtteso<ErroreParlanti.VoceCambiata>()

        assertEquals(ErroreParlanti.VoceCambiata(voce(1)), errore)
        assertNull(attribuzioni.trova(voce(1)))
        assertEquals(emptyList(), assertNotNull(parlanti.trova(anna.id)).impronte)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-282 le Parti rilette in un altro ordine nella transazione non sono VoceCambiata`() {
        val anna = unParlante("Anna")
        val riordinato = object : LettoreRegistrazione by lettoreRegistrazione() {
            private var letture = 0

            override fun parti(incontroId: IncontroId) =
                lettoreRegistrazione().parti(incontroId)?.let { if (letture++ == 0) it else it.reversed() }
        }

        conferma(catalogo = riordinato)
            .esegui(ConfermaAttribuzione(voce(1), ObiettivoAttribuzione.ParlanteEsistente(anna.id)))
            .atteso()

        assertEquals(anna.id, attribuzioni.trova(voce(1))?.parlanteId)
        assertEquals(setOf(A, B), assertNotNull(parlanti.trova(anna.id)).impronte.map { it.parte }.toSet())
    }

    @Test
    fun `AC-I206 un Incontro elencato senza Parti e VoceNonTrovata e nulla e scritto`() {
        val anna = unParlante("Anna")
        val senzaParti = object : LettoreRegistrazione by lettoreRegistrazione() {
            override fun parti(incontroId: IncontroId) = emptyList<ParteDiIncontroParlanti>()
        }

        val errore = conferma(catalogo = senzaParti)
            .esegui(ConfermaAttribuzione(voce(1), ObiettivoAttribuzione.ParlanteEsistente(anna.id)))
            .erroreAtteso<ErroreParlanti.VoceNonTrovata>()

        assertEquals(ErroreParlanti.VoceNonTrovata(voce(1)), errore)
        assertNull(attribuzioni.trova(voce(1)))
        assertEquals(emptyList(), decodificate)
    }

    @Test
    fun `INV-17 un Parlante di un altro Progetto e rifiutato anche per una Voce di piu Parti`() {
        val estraneo = unParlante("Estraneo", ProgettoId("altro"))

        val errore = conferma()
            .esegui(ConfermaAttribuzione(voce(1), ObiettivoAttribuzione.ParlanteEsistente(estraneo.id)))
            .erroreAtteso<ErroreParlanti.ParlanteNonTrovato>()

        assertEquals(ErroreParlanti.ParlanteNonTrovato(estraneo.id), errore)
        assertNull(attribuzioni.trova(voce(1)))
        assertEquals(emptyList(), assertNotNull(parlanti.trova(estraneo.id)).impronte)
    }

    @Test
    fun `INV-15 spostare l Attribuzione da P a Q sposta le impronte di tutte le Parti`() {
        val p = unParlante("Piero")
        val q = unParlante("Quinto")
        conferma().esegui(ConfermaAttribuzione(voce(1), ObiettivoAttribuzione.ParlanteEsistente(p.id))).atteso()

        conferma().esegui(ConfermaAttribuzione(voce(1), ObiettivoAttribuzione.ParlanteEsistente(q.id))).atteso()

        assertEquals(emptyList(), assertNotNull(parlanti.trova(p.id)).impronte)
        assertEquals(listOf(A, B), assertNotNull(parlanti.trova(q.id)).impronte.map { it.parte })
        assertEquals(AttribuzioneConfermata(voce(1), q.id, precedente = p.id), eventi.pubblicati.last())
    }

    @Test
    fun `INV-19 SaltaVoce usa la data della prima Parte e una data modificata dopo non rinomina l Ospite`() {
        salta().esegui(SaltaVoce(voce(2))).atteso()

        val ospite = parlanti.delProgetto(PROGETTO).single()
        assertEquals("Ospite del 30/09/2026", ospite.nome.valore)
        assertEquals(listOf(B), ospite.impronte.map { it.parte })

        registrazioni[A] = vista(A, LocalDate.of(2026, 9, 29))
        salta().esegui(SaltaVoce(voce(1))).atteso()

        val nomi = parlanti.delProgetto(PROGETTO).map { it.nome.valore }.toSet()
        assertEquals(setOf("Ospite del 30/09/2026", "Ospite del 29/09/2026"), nomi)
    }

    @Test
    fun `INV-I8 SaltaVoce di una Voce in A e in B conserva una impronta per Parte`() {
        salta().esegui(SaltaVoce(voce(1))).atteso()

        val ospite = parlanti.delProgetto(PROGETTO).single()
        assertEquals(listOf(A, B), ospite.impronte.map { it.parte })
        assertEquals(ospite.id, attribuzioni.trova(voce(1))?.parlanteId)
    }

    @Test
    fun `AC-286 SaltaVoce con la sorgente di una Parte cambiata e VoceCambiata e nulla e scritto`() {
        val cambiaB = object : LettoreVoci {
            private var letture = 0
            private val dopo = listOf(VoceVista(voce(1), mapOf(A to listOf(IntervalloMs(0, 1_000)))))

            override fun voci(incontroId: IncontroId) = if (letture++ == 0) lettoreVoci().voci(incontroId) else dopo

            override fun segmenti(incontroId: IncontroId): List<SegmentoDiVoce> = error("non usato")
        }

        salta(cambiaB).esegui(SaltaVoce(voce(1))).erroreAtteso<ErroreParlanti.VoceCambiata>()

        assertNull(attribuzioni.trova(voce(1)))
        assertEquals(emptyList(), parlanti.delProgetto(PROGETTO))
    }

    @Test
    fun `INV-15 RiallineaImpronte dell Incontro riderivano solo le righe stale, ciascuna dalla sua Parte`() {
        val anna = unParlante("Anna")
        conferma().esegui(ConfermaAttribuzione(voce(1), ObiettivoAttribuzione.ParlanteEsistente(anna.id))).atteso()
        val primaDiA = assertNotNull(parlanti.trova(anna.id)).impronte.single { it.parte == A }
        // B's Segmento of Voce 1 is edited: its print is stale, A's is current.
        voci[INCONTRO] = conB(voce(1), IntervalloMs(2_000, 4_400))

        riallinea().esegui(RiallineaImpronte(INCONTRO)).atteso()

        val dopo = assertNotNull(parlanti.trova(anna.id)).impronte
        assertEquals(primaDiA, dopo.single { it.parte == A }, "A era corrente: invariata")
        assertEquals(chiave(IntervalloMs(2_000, 4_400)), dopo.single { it.parte == B }.sorgente)
        assertEquals(listOf(ImpronteRiallineate(INCONTRO)), eventi.pubblicati.filterIsInstance<ImpronteRiallineate>())
    }

    @Test
    fun `AC-299 RiallineaImpronte di un Incontro sconosciuto non fa nulla`() {
        riallinea().esegui(RiallineaImpronte(IncontroId("altrove"))).atteso()

        assertEquals(emptyList(), eventi.pubblicati)
    }

    /** The Voci of the Incontro with [voce] speaking in B over [intervallo] instead. */
    private fun conB(voce: VoceRef, intervallo: IntervalloMs): List<VoceVista> =
        voci.getValue(INCONTRO).map {
            val partiNuove = it.intervalliPerParte + (B to listOf(intervallo))
            if (it.voceRef == voce) it.copy(intervalliPerParte = partiNuove) else it
        }

    private fun voce(n: Int) = VoceRef(INCONTRO, VoceId(n))

    private fun vista(id: RegistrazioneId, data: LocalDate) = RegistrazioneVista(
        registrazioneId = id,
        incontroId = INCONTRO,
        progettoId = PROGETTO,
        titolo = "Riunione",
        riferimentoAudio = RiferimentoAudio("audio/${id.valore}.wav"),
        dataRegistrazione = data,
        durataMs = 60_000L,
    )

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val INCONTRO = IncontroId("incontro-1")
        val A = RegistrazioneId("parte-a")
        val B = RegistrazioneId("parte-b")
        val DATA_A: LocalDate = LocalDate.of(2026, 9, 30)
        val DATA_B: LocalDate = LocalDate.of(2026, 10, 1)
    }
}
