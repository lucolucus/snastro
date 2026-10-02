package snastro.sintesi.applicazione.letture

import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.SegmentoRef
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguisticoFinta
import snastro.sintesi.applicazione.porte.LettoreIncontroFinta
import snastro.sintesi.applicazione.porte.LettoreNomiFinta
import snastro.sintesi.applicazione.porte.LettoreTrascrittoFinta
import snastro.sintesi.applicazione.porte.RiassuntoRepositoryFinta
import snastro.sintesi.applicazione.porte.SegmentoSintesi
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico
import snastro.sintesi.applicazione.porte.conAvvio
import snastro.sintesi.applicazione.porte.conCompletamento
import snastro.sintesi.applicazione.porte.unRiassunto
import snastro.sintesi.applicazione.porte.unaStruttura
import snastro.sintesi.dominio.BozzaElemento
import snastro.sintesi.dominio.BozzaRiassunto
import snastro.sintesi.dominio.StrutturaIncontro
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `riassunto-vista-incontro` (ADR 0037 §6): the view of an Incontro over its Parti, INV-I13 (a Voce or a Segmento that
 * is gone never throws and never shows a Nome), INV-I11 (superato), AC-I50 (the first blocking Parte). Multi-Parte
 * cases run on the fakes: the real adapters stay on one Parte until I2.
 */
class RiassuntoVisteLetturaIncontroTest {
    private val incontro = IncontroId("incontro-1")
    private val p1 = RegistrazioneId("parte-1")
    private val p2 = RegistrazioneId("parte-2")
    private val p3 = RegistrazioneId("parte-3")

    @Test
    fun `INV-I13 un Responsabile Voce 7 non piu nell Incontro e presente false senza Nome, anche se era attribuito`() {
        val bozza = bozza(azioni = listOf(BozzaElemento("Mandare il verbale.", listOf(1), voce = 7)))
        val riassunti = riassuntoPronto(bozza, mapOf(p1 to unaStruttura(1 to 7, 2 to 1)), listOf(p1 to 1))
        val nomi = LettoreNomiFinta(
            attribuzioni = mapOf(VoceRef(incontro, VoceId(7)) to "p-7", VoceRef(incontro, VoceId(1)) to "p-1"),
            nomiParlanti = mapOf("p-7" to "Anna", "p-1" to "Marco"),
        )

        // Voce 7 left the structure (the Parte was re-transcribed: Voci 1 only)
        val vista = vista(listOf(p1 to listOf(segmento(3, 1))), riassunti, nomi)
        val azione = checkNotNull(vista.mostrato).azioni.single()

        assertEquals(VoceVista(7, "Voce 7", null, presente = false), azione.responsabile)

        val ancoraPresente = vista(listOf(p1 to listOf(segmento(1, 7), segmento(2, 1))), riassunti, nomi)
        assertEquals(
            VoceVista(7, "Voce 7", "Anna", presente = true),
            checkNotNull(ancoraPresente.mostrato).azioni.single().responsabile,
        )
    }

    @Test
    fun `INV-I13 una Fonte il cui Segmento e sparito non lancia, e segmentoPresente false senza minuto, e superato`() {
        val bozza = bozza(decisioni = listOf(BozzaElemento("Si approva.", listOf(1, 2), null)))
        val riassunti = riassuntoPronto(bozza, mapOf(p1 to unaStruttura(1 to 1, 2 to 1)), listOf(p1 to 1, p1 to 2))

        // same-length re-transcription: ids are never reused (INV-I16), 3 and 4 replace 1 and 2
        val vista = vista(listOf(p1 to listOf(segmento(3, 1), segmento(4, 1))), riassunti)
        val mostrato = checkNotNull(vista.mostrato)

        assertTrue(mostrato.superato)
        val fonti = mostrato.decisioni.single().fonti
        assertEquals(listOf(1, 2), fonti.map { it.segmentoId })
        assertTrue(fonti.all { !it.segmentoPresente && it.inizioMs == null && it.voce == null })
        assertTrue(fonti.all { it.registrazioneId == p1 && it.numeroParte == 1 })
    }

    @Test
    fun `INV-I13 una Fonte di una Parte eliminata ha numeroParte null, una di una Parte presente numero e minuto`() {
        val bozza = bozza(decisioni = listOf(BozzaElemento("Si approva.", listOf(1, 2), null)))
        val riassunti = riassuntoPronto(
            bozza,
            mapOf(p1 to unaStruttura(1 to 1), p2 to unaStruttura(1 to 1)),
            listOf(p1 to 1, p2 to 1),
        )

        // the first Parte is gone: p2 is now Parte 1
        val vista = vista(listOf(p2 to listOf(segmento(1, 1, inizioMs = 7_000))), riassunti)
        val fonti = checkNotNull(vista.mostrato).decisioni.single().fonti

        assertEquals(1, vista.numParti)
        val perParte = fonti.map { it.numeroParte to it.inizioMs }.sortedBy { it.first == null }
        assertEquals(listOf(1 to 7_000L, null to null), perParte)
        assertEquals(setOf(p1, p2), fonti.map { it.registrazioneId }.toSet())
        assertEquals(listOf(true, false), fonti.sortedBy { !it.segmentoPresente }.map { it.segmentoPresente })
        assertTrue(checkNotNull(vista.mostrato).superato)
    }

    @Test
    fun `AC-S104 le Fonti di piu Parti sono ordinate per numero di parte e poi per minuto corrente`() {
        val bozza = bozza(decisioni = listOf(BozzaElemento("Si approva.", listOf(1, 2, 3), null)))
        val riassunti = riassuntoPronto(
            bozza,
            mapOf(p1 to unaStruttura(1 to 1, 2 to 1), p2 to unaStruttura(1 to 1)),
            listOf(p2 to 1, p1 to 2, p1 to 1),
        )

        val vista = vista(
            listOf(
                p1 to listOf(segmento(1, 1, inizioMs = 9_000), segmento(2, 1, inizioMs = 2_000)),
                p2 to listOf(segmento(1, 1, inizioMs = 1_000)),
            ),
            riassunti,
        )
        val fonti = checkNotNull(vista.mostrato).decisioni.single().fonti

        assertEquals(listOf(1 to 2_000L, 1 to 9_000L, 2 to 1_000L), fonti.map { it.numeroParte to it.inizioMs })
        assertFalse(checkNotNull(vista.mostrato).superato)
        assertEquals(2, vista.numParti)
    }

    @Test
    fun `INV-I11 INV-I3 superato e false sul Riassunto invariato anche con una Parte, vero dopo una ritrascrizione`() {
        val bozza = bozza(decisioni = listOf(BozzaElemento("Si approva.", listOf(1), null)))
        val riassunti = riassuntoPronto(bozza, mapOf(p1 to unaStruttura(1 to 1)), listOf(p1 to 1))

        assertFalse(checkNotNull(vista(listOf(p1 to listOf(segmento(1, 1))), riassunti).mostrato).superato)
        assertTrue(checkNotNull(vista(listOf(p1 to listOf(segmento(2, 1))), riassunti).mostrato).superato)
    }

    @Test
    fun `INV-I11 una Parte aggiunta senza Trascritto rende superato il Riassunto, una eliminata pure`() {
        val bozza = bozza(decisioni = listOf(BozzaElemento("Si approva.", listOf(1), null)))
        val riassunti = riassuntoPronto(bozza, mapOf(p1 to unaStruttura(1 to 1)), listOf(p1 to 1))

        val conNuova = vista(listOf(p1 to listOf(segmento(1, 1)), p2 to null), riassunti)

        assertTrue(checkNotNull(conNuova.mostrato).superato)
        assertEquals(2, conNuova.numParti)
    }

    @Test
    fun `AC-I50 disponibilita nomina la PRIMA Parte bloccante in ordine di Parte e numParti conta le Parti di ora`() {
        val tutte = listOf(p1, p2, p3)
        fun disponibilita(
            trascritti: Map<RegistrazioneId, List<SegmentoSintesi>>,
            aperte: Set<RegistrazioneId> = emptySet(),
            fallite: Set<RegistrazioneId> = emptySet(),
        ) =
            vistaDi(tutte, LettoreTrascrittoFinta(trascritti, aperte, fallite), RiassuntoRepositoryFinta())

        val manca2 = disponibilita(mapOf(p1 to listOf(segmento(1, 1)), p3 to listOf(segmento(1, 1))))
        assertEquals(
            DisponibilitaVista.NonDisponibile(MotivoNonDisponibile.PartiNonTrascritte(2)),
            manca2.disponibilita,
        )
        assertEquals(3, manca2.numParti)

        val aperta1 = disponibilita(
            mapOf(p1 to listOf(segmento(1, 1)), p2 to listOf(segmento(1, 1)), p3 to listOf(segmento(1, 1))),
            aperte = setOf(p1, p3),
        )
        assertEquals(
            DisponibilitaVista.NonDisponibile(MotivoNonDisponibile.ElaborazioneAperta(1)),
            aperta1.disponibilita,
        )

        val fallita2 = disponibilita(
            mapOf(p1 to listOf(segmento(1, 1)), p3 to listOf(segmento(1, 1))),
            fallite = setOf(p2),
        )
        assertEquals(DisponibilitaVista.NonDisponibile(MotivoNonDisponibile.PartiFallite(2)), fallita2.disponibilita)

        val tutteOk = disponibilita(tutte.associateWith { listOf(segmento(1, 1)) })
        assertEquals(DisponibilitaVista.Disponibile, tutteOk.disponibilita)
    }

    @Test
    fun `INV-I3 un Incontro di una Parte senza Trascritto non offre la scheda, mai Parte 1 non riuscita`() {
        val riassunti = RiassuntoRepositoryFinta()
        listOf(
            LettoreTrascrittoFinta(),
            LettoreTrascrittoFinta(fallite = setOf(p1)),
            LettoreTrascrittoFinta(aperte = setOf(p1)),
        ).forEach { trascritti ->
            assertNull(lettura(listOf(p1), trascritti, riassunti, LettoreNomiFinta()).di(incontro))
        }
    }

    @Test
    fun `un Incontro sconosciuto non ha vista`() {
        val lettura = lettura(emptyList(), LettoreTrascrittoFinta(), RiassuntoRepositoryFinta(), LettoreNomiFinta())

        assertNull(lettura.di(incontro))
    }

    // --- helpers ---

    private fun bozza(
        decisioni: List<BozzaElemento> = emptyList(),
        azioni: List<BozzaElemento> = emptyList(),
    ) = BozzaRiassunto(null, decisioni, emptyList(), azioni, emptyList())

    /** A `pronto` Riassunto of [incontro]: label k of the bozza = [etichette]`[k-1]` = (Parte, segmentoId). */
    private fun riassuntoPronto(
        bozza: BozzaRiassunto,
        strutture: Map<RegistrazioneId, snastro.sintesi.dominio.StrutturaTrascritto>,
        etichette: List<Pair<RegistrazioneId, Int>>,
    ): RiassuntoRepositoryFinta {
        val riassunto = unRiassunto("r-1", incontro).conAvvio().conCompletamento(
            bozza,
            StrutturaIncontro(strutture.map { (r, s) -> r to s }),
            etichette.map { (r, s) -> SegmentoRef(r, SegmentoId(s)) },
        )
        return RiassuntoRepositoryFinta().also { it.salva(riassunto).atteso() }
    }

    private fun vista(
        parti: List<Pair<RegistrazioneId, List<SegmentoSintesi>?>>,
        riassunti: RiassuntoRepositoryFinta,
        nomi: LettoreNomiFinta = LettoreNomiFinta(),
    ): RiassuntoVista {
        val trascritti = LettoreTrascrittoFinta(parti.mapNotNull { (r, s) -> s?.let { r to it } }.toMap())
        return checkNotNull(lettura(parti.map { it.first }, trascritti, riassunti, nomi).di(incontro))
    }

    private fun vistaDi(
        parti: List<RegistrazioneId>,
        trascritti: LettoreTrascrittoFinta,
        riassunti: RiassuntoRepositoryFinta,
    ) =
        checkNotNull(lettura(parti, trascritti, riassunti, LettoreNomiFinta()).di(incontro))

    private fun lettura(
        parti: List<RegistrazioneId>,
        trascritti: LettoreTrascrittoFinta,
        riassunti: RiassuntoRepositoryFinta,
        nomi: LettoreNomiFinta,
    ) = RiassuntoVisteLettura(
        UnitaDiLavoroFinta(),
        riassunti,
        trascritti,
        LettoreIncontroFinta(mapOf(incontro to parti)),
        nomi,
        DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.Installato),
    )

    private fun segmento(id: Int, voce: Int, inizioMs: Long = id * 1_000L) =
        SegmentoSintesi(SegmentoId(id), VoceId(voce), IntervalloMs(inizioMs, inizioMs + 500), "Testo $id.")
}
