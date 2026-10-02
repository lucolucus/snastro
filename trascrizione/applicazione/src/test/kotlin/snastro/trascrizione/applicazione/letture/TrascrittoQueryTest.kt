package snastro.trascrizione.applicazione.letture

import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.SegmentoId
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.kernel.unIncontroDi
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.applicazione.porte.LettoreRegistrazioneFinta
import snastro.trascrizione.applicazione.porte.RegistrazioneVista
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepositoryFinta
import snastro.trascrizione.dominio.DURATA_TRASCRITTO_MS
import snastro.trascrizione.dominio.NumeroPersone
import snastro.trascrizione.dominio.VociDellIncontro
import snastro.trascrizione.dominio.unSegmentoIniziale
import snastro.trascrizione.dominio.unaElaborazione
import snastro.trascrizione.dominio.unaRadice
import snastro.trascrizione.dominio.unaRadiceDa
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TrascrittoQueryTest {
    private val trascritti = VociDellIncontroRepositoryFinta()
    private val registrazioni = LettoreRegistrazioneFinta(mapOf(REGISTRAZIONE to UNA_REGISTRAZIONE))
    private val elaborazioni = ElaborazioneRepositoryFinta()
    private val query = TrascrittoQuery(trascritti, registrazioni, elaborazioni)

    @Test
    fun `AC-167 vista espone i campi della Registrazione, i segmenti in ordine di tempo e le voci etichettate`() {
        val radice = unaRadiceDa(
            REGISTRAZIONE,
            unIncontroDi(REGISTRAZIONE),
            DURATA_TRASCRITTO_MS,
            listOf(
                unSegmentoIniziale(voceIndice = 4, inizioMs = 2_000, fineMs = 4_000, testo = "seconda battuta"),
                unSegmentoIniziale(voceIndice = 2, inizioMs = 0, fineMs = 1_500, testo = "prima battuta"),
                unSegmentoIniziale(voceIndice = 4, inizioMs = 4_500, fineMs = 5_000, testo = "terza battuta"),
            ),
        )
        trascritti.salva(radice)

        val vista = query.vista(REGISTRAZIONE)

        assertEquals(
            TrascrittoView(
                registrazioneId = REGISTRAZIONE,
                incontroId = unIncontroDi(REGISTRAZIONE),
                numeroParte = 1,
                parti = listOf(ParteRef(REGISTRAZIONE, 1)),
                solaLettura = null,
                titolo = "Intervista",
                dataRegistrazione = LocalDate.of(2026, 9, 1),
                durataMs = 120_000,
                segmenti = listOf(
                    SegmentoTrascrittoView(SegmentoId(1), VoceId(1), 0, 1_500, "prima battuta"),
                    SegmentoTrascrittoView(SegmentoId(2), VoceId(2), 2_000, 4_000, "seconda battuta"),
                    SegmentoTrascrittoView(SegmentoId(3), VoceId(2), 4_500, 5_000, "terza battuta"),
                ),
                voci = listOf(
                    VoceTrascrittoView(VoceId(1), "Voce 1", emptyList()),
                    VoceTrascrittoView(VoceId(2), "Voce 2", emptyList()),
                ),
            ),
            vista,
        )
    }

    @Test
    fun `AC-167 segmenti in ordine di tempo anche quando le Voci si alternano`() {
        val radice = unaRadiceDa(
            REGISTRAZIONE,
            unIncontroDi(REGISTRAZIONE),
            DURATA_TRASCRITTO_MS,
            listOf(
                unSegmentoIniziale(voceIndice = 1, inizioMs = 0, fineMs = 1_000, testo = "v1 turno 1"),
                unSegmentoIniziale(voceIndice = 2, inizioMs = 1_000, fineMs = 2_000, testo = "v2 turno 1"),
                unSegmentoIniziale(voceIndice = 1, inizioMs = 2_000, fineMs = 3_000, testo = "v1 turno 2"),
            ),
        )
        trascritti.salva(radice)

        val vista = query.vista(REGISTRAZIONE)

        // Grouping by Voce (V1's segments, then V2's) would yield [1, 3, 2]: this asserts the TIME order.
        assertEquals(
            listOf(
                SegmentoTrascrittoView(SegmentoId(1), VoceId(1), 0, 1_000, "v1 turno 1"),
                SegmentoTrascrittoView(SegmentoId(2), VoceId(2), 1_000, 2_000, "v2 turno 1"),
                SegmentoTrascrittoView(SegmentoId(3), VoceId(1), 2_000, 3_000, "v1 turno 2"),
            ),
            vista?.segmenti,
        )
    }

    @Test
    fun `AC-167 etichetta 'Voce n' usa il VoceId anche con un buco nella sequenza`() {
        val radice = unaRadiceDa(
            REGISTRAZIONE,
            unIncontroDi(REGISTRAZIONE),
            DURATA_TRASCRITTO_MS,
            listOf(
                unSegmentoIniziale(voceIndice = 10, inizioMs = 0, fineMs = 1_000, testo = "a"),
                unSegmentoIniziale(voceIndice = 20, inizioMs = 1_000, fineMs = 2_000, testo = "b"),
                unSegmentoIniziale(voceIndice = 30, inizioMs = 2_000, fineMs = 3_000, testo = "c"),
            ),
        )
        // VoceId(1) is absorbed into VoceId(2): only VoceId(2) and VoceId(3) survive — a gap at 1.
        val fuso = radice.unisci(sopravvive = VoceId(2), rimossa = VoceId(1)).atteso()
        check(fuso.sopravvissuta == VoceId(2) && fuso.rimossa == VoceId(1))
        trascritti.salva(radice)

        val vista = query.vista(REGISTRAZIONE)

        // Labelling by position ("Voce ${i+1}") would yield "Voce 1"/"Voce 2": this asserts the VoceId.
        assertEquals(
            listOf(
                VoceTrascrittoView(VoceId(2), "Voce 2", emptyList()),
                VoceTrascrittoView(VoceId(3), "Voce 3", emptyList()),
            ),
            vista?.voci,
        )
    }

    @Test
    fun `AC-167 dopo una Revisione (dividi) i segmenti restano in ordine di tempo e portano il nuovo voceId`() {
        val radice = unaRadice(voci = 2, segmentiPerVoce = 3, registrazioneId = REGISTRAZIONE)
        // Origine VoceId(1) owns segmenti {1, 3, 5} (INV-7 order); split off segmento 3 into a new Voce.
        radice.dividi(origine = VoceId(1), segmenti = setOf(SegmentoRef(REGISTRAZIONE, SegmentoId(3)))).atteso()
        trascritti.salva(radice)

        val vista = query.vista(REGISTRAZIONE)

        assertEquals(
            listOf(
                SegmentoTrascrittoView(SegmentoId(1), VoceId(1), 0, 1_000, "testo 0@0"),
                SegmentoTrascrittoView(SegmentoId(2), VoceId(2), 1_000, 2_000, "testo 1@1000"),
                SegmentoTrascrittoView(SegmentoId(3), VoceId(3), 2_000, 3_000, "testo 0@2000", confermato = true),
                SegmentoTrascrittoView(SegmentoId(4), VoceId(2), 3_000, 4_000, "testo 1@3000"),
                SegmentoTrascrittoView(SegmentoId(5), VoceId(1), 4_000, 5_000, "testo 0@4000"),
                SegmentoTrascrittoView(SegmentoId(6), VoceId(2), 5_000, 6_000, "testo 1@5000"),
            ),
            vista?.segmenti,
        )
    }

    @Test
    fun `AC-523 la vista espone confermato per Segmento come salvato`() {
        val radice = unaRadice(voci = 2, segmentiPerVoce = 2, registrazioneId = REGISTRAZIONE)
        radice.confermaSegmento(SegmentoRef(REGISTRAZIONE, SegmentoId(2)), true).atteso()
        radice.confermaSegmento(SegmentoRef(REGISTRAZIONE, SegmentoId(4)), true).atteso()
        trascritti.salva(radice)

        val vista = query.vista(REGISTRAZIONE)

        assertEquals(listOf(false, true, false, true), vista?.segmenti?.map { it.confermato })
        assertEquals((1..4).map(::SegmentoId), vista?.segmenti?.map { it.segmentoId })
    }

    @Test
    fun `AC-168 vista senza Trascritto restituisce null`() {
        assertNull(query.vista(REGISTRAZIONE))
    }

    @Test
    fun `AC-168 vista con Trascritto ma senza Registrazione nota restituisce null`() {
        trascritti.salva(unaRadice(voci = 1, segmentiPerVoce = 1, registrazioneId = ALTRA_REGISTRAZIONE))

        assertNull(query.vista(ALTRA_REGISTRAZIONE))
    }

    @Test
    fun `AC-I43 la Parte 2 di 3 ha numeroParte, parti e solo le Voci che vi parlano, la Voce 2 con altreParti 1 e 3`() {
        val (_, query) = treParti()

        val vista = checkNotNull(query.vista(P2))

        assertEquals(2, vista.numeroParte)
        assertEquals(listOf(1, 2, 3), vista.parti.map { it.numero })
        assertEquals(listOf(P1, P2, P3), vista.parti.map { it.registrazioneId })
        assertEquals(listOf(VoceId(2), VoceId(3)), vista.voci.map { it.voceId })
        assertEquals(listOf(listOf(1, 3), emptyList()), vista.voci.map { it.altreParti })
        assertEquals(setOf(VoceId(2), VoceId(3)), vista.segmenti.map { it.voceId }.toSet())
    }

    @Test
    fun `una vista che non e' di una Parte del suo Incontro e' rifiutata`() {
        fun vista(numero: Int, parti: List<ParteRef>) = TrascrittoView(
            P2, INCONTRO, "t", LocalDate.of(2026, 10, 3), 1_000, emptyList(), emptyList(), numero, parti,
        )

        assertFailsWith<IllegalArgumentException> { vista(1, emptyList()) }
        assertFailsWith<IllegalArgumentException> { vista(1, listOf(ParteRef(P1, 1), ParteRef(P2, 2))) }
        assertEquals(2, vista(2, listOf(ParteRef(P1, 1), ParteRef(P2, 2))).numeroParte)
    }

    @Test
    fun `AC-I44 un Ritrascrivi in coda sulla Parte 3 rende sola lettura anche la Parte 1`() {
        val (_, query, elaborazioni) = treParti()
        elaborazioni.salva(unaElaborazione(registrazioneId = P3))

        assertEquals(RitrascrizioneInCorso(3), query.vista(P1)?.solaLettura)
        assertEquals(RitrascrizioneInCorso(3), query.vista(P3)?.solaLettura)
    }

    @Test
    fun `AC-I44 la prima trascrizione di una Parte appena importata non rende sola lettura`() {
        val p4 = RegistrazioneId("p4")
        val (_, query, elaborazioni) = treParti(ordine = listOf(P1, P2, P3, p4))
        elaborazioni.salva(unaElaborazione(registrazioneId = p4))

        val vista = checkNotNull(query.vista(P1))
        assertNull(vista.solaLettura)
        assertEquals(4, vista.parti.single { it.registrazioneId == p4 }.numero)
    }

    @Test
    fun `INV-I3 la vista di un Incontro di una sola Parte ha una parte e solaLettura con parte null`() {
        trascritti.salva(unaRadice(voci = 1, segmentiPerVoce = 1, registrazioneId = REGISTRAZIONE))
        elaborazioni.salva(unaElaborazione(registrazioneId = REGISTRAZIONE))

        val vista = checkNotNull(query.vista(REGISTRAZIONE))

        assertEquals(listOf(ParteRef(REGISTRAZIONE, 1)), vista.parti)
        assertEquals(1, vista.numeroParte)
        assertEquals(RitrascrizioneInCorso(null), vista.solaLettura)
        assertEquals(emptyList(), vista.voci.single().altreParti)
    }

    @Test
    fun `AC-I45 VociIncontro elenca ogni Voce con le Parti in cui parla, per voceId crescente, e numVoci torna`() {
        val (_, query) = treParti()

        val voci = checkNotNull(query.vociIncontro(INCONTRO))

        assertEquals(listOf(1, 2, 3, 5).map { VoceId(it) }, voci.voci.map { it.voceId })
        assertEquals(listOf(listOf(1), listOf(1, 2, 3), listOf(2), listOf(3)), voci.voci.map { it.parti })
        assertEquals(4, voci.numVoci)
    }

    @Test
    fun `numeroPersonePrecompilato della vista e quello dell'ultima Elaborazione sull'Incontro`() {
        val (_, query, elaborazioni) = treParti()
        assertNull(query.numeroPersonePrecompilato(INCONTRO))
        elaborazioni.salva(unaElaborazione(registrazioneId = P2, numeroPersone = NumeroPersone.di(3).atteso()))

        assertEquals(3, query.numeroPersonePrecompilato(INCONTRO))
    }

    private fun treParti(
        ordine: List<RegistrazioneId> = listOf(P1, P2, P3),
    ): Triple<VociDellIncontro, TrascrittoQuery, ElaborazioneRepositoryFinta> {
        val radice = VociDellIncontro.crea(INCONTRO)
        listOf(P1, P2, P3).forEach { r ->
            radice.completaParte(
                r,
                listOf(unSegmentoIniziale(0, 0), unSegmentoIniziale(1, 2_000)),
                DURATA_TRASCRITTO_MS,
            ).atteso()
        }
        // Voci 1,2 | 3,4 | 5,6 -> Voce 2 speaks in all three Parti (merged by the user), 4 and 6 are gone.
        radice.unisci(VoceId(2), VoceId(4)).atteso()
        radice.unisci(VoceId(2), VoceId(6)).atteso()
        val repo = VociDellIncontroRepositoryFinta()
        repo.salva(radice)
        val el = ElaborazioneRepositoryFinta()
        val lettore = LettoreRegistrazioneFinta(
            listOf(P1, P2, P3, RegistrazioneId("p4")).associateWith { unaVistaDi(it) },
            ordine = mapOf(INCONTRO to ordine),
        )
        return Triple(radice, TrascrittoQuery(repo, lettore, el), el)
    }

    private fun unaVistaDi(r: RegistrazioneId) = UNA_REGISTRAZIONE.copy(registrazioneId = r, incontroId = INCONTRO)

    private companion object {
        val INCONTRO = IncontroId("incontro-3-parti")
        val P1 = RegistrazioneId("p1")
        val P2 = RegistrazioneId("p2")
        val P3 = RegistrazioneId("p3")
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        val ALTRA_REGISTRAZIONE = RegistrazioneId("registrazione-2")
        val UNA_REGISTRAZIONE = RegistrazioneVista(
            registrazioneId = REGISTRAZIONE,
            incontroId = unIncontroDi(REGISTRAZIONE),
            progettoId = ProgettoId("progetto-1"),
            titolo = "Intervista",
            riferimentoAudio = RiferimentoAudio("audio/registrazione-1.wav"),
            dataRegistrazione = LocalDate.of(2026, 9, 1),
            durataMs = 120_000,
        )
    }
}
