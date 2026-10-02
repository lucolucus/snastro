package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.SegmentoRef
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.trascrizione.applicazione.eventi.SegmentoConfermato
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione
import snastro.trascrizione.applicazione.porte.ParteDiIncontro
import snastro.trascrizione.applicazione.porte.RegistrazioneVista
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepositoryFinta
import snastro.trascrizione.applicazione.porte.unaRegistrazioneVista
import snastro.trascrizione.dominio.DURATA_TRASCRITTO_MS
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.trascrizione.dominio.SpostamentoSegmento
import snastro.trascrizione.dominio.VociDellIncontro
import snastro.trascrizione.dominio.unSegmentoIniziale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Revisione across the Parti of ONE Incontro (I1, INV-I7): the commands stay keyed by the Parte, the root is the
 * Incontro's. Parte A holds Voce 1 (S1, S3) and Voce 2 (S2, S4); Parte B holds Voce 3, 4, 5.
 */
class RevisioneIncontroTest {
    private val trascritti = VociDellIncontroRepositoryFinta()
    private val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(trascritti))
    private val registrazioni = object : LettoreRegistrazione {
        override fun registrazione(id: RegistrazioneId): RegistrazioneVista =
            unaRegistrazioneVista(id).copy(incontroId = INCONTRO)

        override fun parti(incontroId: IncontroId): List<ParteDiIncontro> =
            listOf(ParteDiIncontro(A, 1), ParteDiIncontro(B, 2))
    }
    private val unisci = UnisciVociServizio(eventi.unitaDiLavoro, trascritti, registrazioni, eventi)
    private val dividi = DividiVoceServizio(eventi.unitaDiLavoro, trascritti, registrazioni, eventi)
    private val riassegna = RiassegnaSegmentoServizio(eventi.unitaDiLavoro, trascritti, registrazioni, eventi)
    private val riassegnaBlocco = RiassegnaSegmentiServizio(eventi.unitaDiLavoro, trascritti, registrazioni, eventi)
    private val conferma = ConfermaSegmentoServizio(eventi.unitaDiLavoro, trascritti, registrazioni, eventi)

    private fun incontroConDueParti() {
        val radice = VociDellIncontro.crea(INCONTRO)
        radice.completaParte(A, turni(0, 1), DURATA_TRASCRITTO_MS).atteso()
        radice.completaParte(B, turni(0, 1, 2), DURATA_TRASCRITTO_MS).atteso()
        trascritti.salva(radice)
    }

    private fun turni(vararg ordine: Int) = (0 until 2).flatMap { giro ->
        ordine.mapIndexed { i, c -> unSegmentoIniziale(c, ((giro * ordine.size + i) * 1_000).toLong()) }
    }

    private fun voceDi(r: RegistrazioneId, s: Int): VoceId =
        assertNotNull(trascritti.trascritto(r)).segmenti.first { it.id == SegmentoId(s) }.voceId

    @Test
    fun `UnisciVoci di una Voce della Parte A con una della Parte B da una sola Voce con Segmenti in entrambe`() {
        incontroConDueParti()

        unisci.esegui(UnisciVoci(B, sopravvive = VoceId(2), rimossa = VoceId(5), incontroDelleVoci = INCONTRO)).atteso()

        assertTrue(assertNotNull(trascritti.trascritto(A)).segmenti.any { it.voceId == VoceId(2) })
        assertTrue(assertNotNull(trascritti.trascritto(B)).segmenti.any { it.voceId == VoceId(2) })
        assertTrue(assertNotNull(trascritti.trovaRadice()).voci.none { it == VoceId(5) })
        assertEquals(listOf(VociUnite(INCONTRO, sopravvissuta = VoceId(2), rimossa = VoceId(5))), eventi.pubblicati)
    }

    @Test
    fun `UnisciVoci con una Voce sconosciuta e VoceNonTrovata e non cambia nulla`() {
        incontroConDueParti()
        val prima = trascritti.trovaRadice().voci

        val errore = unisci.esegui(UnisciVoci(A, VoceId(2), VoceId(9)))
            .erroreAtteso<ErroreTrascrizione.VoceNonTrovata>()

        assertEquals(VoceId(9), errore.voceId)
        assertEquals(prima, trascritti.trovaRadice().voci)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `INV-I7 una Voce di un altro Incontro e VoceNonTrovata in ogni comando e nulla cambia ne si pubblica`() {
        incontroConDueParti()
        val altro = IncontroId("incontro-2")
        val prima = assertNotNull(trascritti.trascritto(B)).segmenti

        unisci.esegui(UnisciVoci(B, VoceId(2), VoceId(5), altro)).erroreAtteso<ErroreTrascrizione.VoceNonTrovata>()
        dividi.esegui(DividiVoce(B, VoceId(3), setOf(SegmentoId(1)), altro))
            .erroreAtteso<ErroreTrascrizione.SegmentoNonTrovato>()
        riassegna.esegui(RiassegnaSegmento(B, SegmentoId(1), VoceId(4), altro))
            .erroreAtteso<ErroreTrascrizione.SegmentoNonTrovato>()
        val piano = listOf(SpostamentoSegmento(SegmentoId(1), VoceId(3), VoceId(4), IntervalloMs(0, 1_000)))
        riassegnaBlocco.esegui(RiassegnaSegmenti(B, piano, altro)).erroreAtteso<ErroreTrascrizione.SegmentoNonTrovato>()

        assertEquals(prima, assertNotNull(trascritti.trascritto(B)).segmenti)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `Incontro dichiarato diverso da un Segmento e SegmentoNonTrovato col SegmentoRef della Parte`() {
        incontroConDueParti()
        val altro = IncontroId("incontro-2")
        val prima = assertNotNull(trascritti.trascritto(B)).segmenti
        val atteso = SegmentoRef(B, SegmentoId(1))
        val piano = listOf(SpostamentoSegmento(SegmentoId(1), VoceId(3), VoceId(4), IntervalloMs(0, 1_000)))

        val errori = listOf(
            dividi.esegui(DividiVoce(B, VoceId(3), setOf(SegmentoId(1)), altro)),
            riassegna.esegui(RiassegnaSegmento(B, SegmentoId(1), VoceId(4), altro)),
            riassegnaBlocco.esegui(RiassegnaSegmenti(B, piano, altro)),
            conferma.esegui(ConfermaSegmento(B, SegmentoId(1), true, altro)),
        ).map { it.erroreAtteso<ErroreTrascrizione.SegmentoNonTrovato>().segmento }

        assertEquals(List(4) { atteso }, errori)
        assertEquals(prima, assertNotNull(trascritti.trascritto(B)).segmenti)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `un Incontro dichiarato diverso e rifiutato prima di toccare la radice anche con ogni Voce null`() {
        incontroConDueParti()
        val prima = assertNotNull(trascritti.trascritto(B)).segmenti

        val comando = RiassegnaSegmento(B, SegmentoId(1), destinazione = null, incontroDelleVoci = IncontroId("altro"))
        riassegna.esegui(comando)
            .erroreAtteso<ErroreTrascrizione.SegmentoNonTrovato>()

        assertEquals(prima, assertNotNull(trascritti.trascritto(B)).segmenti)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `DividiVoce con Segmenti della Parte B si applica e VoceDivisa porta i SegmentoRef di B`() {
        incontroConDueParti()
        // Voce 3 in B: S1, S4 (clusters 0,1,2 x 2 giri: S1=c0, S2=c1, S3=c2, S4=c0, ...)
        val voce = voceDi(B, 1)

        dividi.esegui(DividiVoce(B, voce, setOf(SegmentoId(4)), INCONTRO)).atteso()

        val e = eventi.pubblicati.single() as VoceDivisa
        assertEquals(INCONTRO, e.incontroId)
        assertEquals(listOf(SegmentoRef(B, SegmentoId(4))), e.spostati)
    }

    @Test
    fun `DividiVoce che prenderebbe tutta la Voce da un errore che dice di quale Parte sono i Segmenti`() {
        incontroConDueParti()
        val voce = voceDi(B, 1)

        val errore = dividi.esegui(DividiVoce(B, voce, setOf(SegmentoId(1), SegmentoId(4))))
            .erroreAtteso<ErroreTrascrizione.DivisioneNonAmmessa>()

        assertEquals(setOf(SegmentoRef(B, SegmentoId(1)), SegmentoRef(B, SegmentoId(4))), errore.segmenti)
    }

    @Test
    fun `RiassegnaSegmento di un Segmento della Parte B pubblica SegmentoRiassegnato con il SegmentoRef`() {
        incontroConDueParti()

        val destinazione = riassegna.esegui(RiassegnaSegmento(B, SegmentoId(2), VoceId(2), INCONTRO)).atteso()

        assertEquals(VoceId(2), destinazione)
        val e = eventi.pubblicati.single() as SegmentoRiassegnato
        assertEquals(SegmentoRef(B, SegmentoId(2)), e.segmento)
        assertEquals(INCONTRO, e.incontroId)
    }

    @Test
    fun `un Segmento sconosciuto della Parte B da SegmentoNonTrovato con il SegmentoRef della Parte`() {
        incontroConDueParti()

        val errore = riassegna.esegui(RiassegnaSegmento(B, SegmentoId(99), VoceId(2)))
            .erroreAtteso<ErroreTrascrizione.SegmentoNonTrovato>()

        assertEquals(SegmentoRef(B, SegmentoId(99)), errore.segmento)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `RiassegnaSegmenti con un piano vecchio e un errore e non applica nulla`() {
        incontroConDueParti()
        val prima = assertNotNull(trascritti.trascritto(B)).segmenti
        val vecchio = listOf(SpostamentoSegmento(SegmentoId(1), VoceId(3), VoceId(4), IntervalloMs(5, 6)))

        riassegnaBlocco.esegui(RiassegnaSegmenti(B, vecchio, INCONTRO))
            .erroreAtteso<ErroreTrascrizione.TrascrittoCambiato>()

        assertEquals(prima, assertNotNull(trascritti.trascritto(B)).segmenti)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `ConfermaSegmento su un Segmento della Parte B commuta il flag e pubblica SegmentoConfermato`() {
        incontroConDueParti()

        conferma.esegui(ConfermaSegmento(B, SegmentoId(1), confermato = true)).atteso()

        assertEquals(listOf(SegmentoConfermato(INCONTRO, SegmentoRef(B, SegmentoId(1)), true)), eventi.pubblicati)
        assertTrue(assertNotNull(trascritti.trascritto(B)).segmenti.first { it.id == SegmentoId(1) }.confermato)
        assertTrue(!assertNotNull(trascritti.trascritto(A)).segmenti.any { it.confermato })
    }

    private fun VociDellIncontroRepositoryFinta.trovaRadice(): VociDellIncontro = assertNotNull(trova(INCONTRO))

    private companion object {
        val INCONTRO = IncontroId("incontro-1")
        val A = RegistrazioneId("parte-a")
        val B = RegistrazioneId("parte-b")
    }
}
