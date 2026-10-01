package snastro.sbobinatura.applicazione.porte

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of [LettoreTrascritto] (boundary `porte-sbobinatura`): one
 * subclass per implementation — [LettoreTrascrittoFinta] (D1) and `LettoreTrascrittoDaTrascrizione`
 * (D2, real-on-real). The multi-Parte cases are registered only when
 * [AmbienteLettoreTrascritto.piuPartiPerIncontro] (D-0037), never skipped.
 */
public abstract class LettoreTrascrittoContratto {
    /** A fresh supplier: one Progetto, no Registrazione. */
    protected abstract fun ambiente(): AmbienteLettoreTrascritto

    @Test
    public fun `AC-49 senza Trascritto restituisce null`() {
        val a = ambiente()
        val mai = a.aggiungiRegistrazione(RIUNIONE)
        val fallita = a.aggiungiRegistrazione(INTERVISTA)
        a.fallisciElaborazione(fallita)

        assertNull(a.lettore.trascritto(SCONOSCIUTA))
        assertNull(a.lettore.trascritto(mai))
        assertNull(a.lettore.trascritto(fallita))
    }

    @Test
    public fun `AC-49 i segmenti sono ordinati per inizio attraverso le Voci con titolo e data della Registrazione`() {
        val a = ambiente()
        val id = a.aggiungiRegistrazione(RIUNIONE)
        val turni = listOf(
            SemeTurno(1, IntervalloMs(12_000, 15_000), "Ok, ci sono."),
            SemeTurno(0, IntervalloMs(0, 4_000), "Buongiorno, iniziamo."),
            SemeTurno(0, IntervalloMs(20_000, 26_000), "Perfetto, chiudiamo qui."),
            SemeTurno(1, IntervalloMs(4_000, 9_000), "Io ho finito il parser."),
        )
        val c = a.completaElaborazione(id, turni)

        assertEquals(
            TrascrittoTesto(
                registrazioneId = id,
                incontroId = a.incontroDi(id),
                titolo = RIUNIONE.titolo,
                dataRegistrazione = RIUNIONE.dataRegistrazione,
                segmenti = listOf(1, 3, 0, 2).map { vista(turni[it], c[it]) },
            ),
            a.lettore.trascritto(id),
        )
    }

    @Test
    public fun `AC-49 a parita di inizio ordina per segmentoId anche dopo una Revisione`() {
        val a = ambiente()
        val id = a.aggiungiRegistrazione(RIUNIONE)
        val turni = listOf(
            SemeTurno(0, IntervalloMs(0, 2_000), "Buongiorno a tutti."),
            SemeTurno(0, IntervalloMs(5_000, 8_000), "Partiamo dal budget."),
            SemeTurno(1, IntervalloMs(5_000, 9_000), "Sure, go ahead."),
        )
        val c = a.completaElaborazione(id, turni)
        // The Segmento that starts together with another moves to a new Voce, numbered after both:
        // ordering by Voce would now swap the two, ordering by segmentoId must not.
        val nuovaVoce = a.riassegna(id, c[1].segmentoId, destinazione = null)

        assertEquals(
            listOf(
                vista(turni[0], c[0]),
                vista(turni[1], c[1].copy(voceId = nuovaVoce)),
                vista(turni[2], c[2]),
            ),
            a.lettore.trascritto(id)?.segmenti,
        )
    }

    @Test
    public fun `AC-49 il testo resta verbatim con italiano e inglese misti`() {
        val a = ambiente()
        val id = a.aggiungiRegistrazione(RIUNIONE)
        val testi = listOf(
            "Facciamo il deploy on Friday, se la pipeline è green — d'accordo?",
            "Yes, let's ship it: però prima la code review… «ok» (àèéìòù) 100%!",
        )
        a.completaElaborazione(
            id,
            listOf(
                SemeTurno(0, IntervalloMs(0, 3_000), testi[0]),
                SemeTurno(1, IntervalloMs(3_000, 7_000), testi[1]),
            ),
        )

        assertEquals(testi, a.lettore.trascritto(id)?.segmenti?.map { it.testo })
    }

    @Test
    public fun `AC-49 ogni Registrazione restituisce il proprio Trascritto`() {
        val a = ambiente()
        val riunione = a.aggiungiRegistrazione(RIUNIONE)
        val intervista = a.aggiungiRegistrazione(INTERVISTA)
        val turnoRiunione = SemeTurno(0, IntervalloMs(1_000, 2_000), "Riunione.")
        val turnoIntervista = SemeTurno(0, IntervalloMs(500, 1_500), "Intervista.")
        val cRiunione = a.completaElaborazione(riunione, listOf(turnoRiunione))
        val cIntervista = a.completaElaborazione(intervista, listOf(turnoIntervista))

        assertEquals(
            TrascrittoTesto(
                intervista,
                a.incontroDi(intervista),
                INTERVISTA.titolo,
                INTERVISTA.dataRegistrazione,
                listOf(vista(turnoIntervista, cIntervista[0])),
            ),
            a.lettore.trascritto(intervista),
        )
        assertEquals(
            TrascrittoTesto(
                riunione,
                a.incontroDi(riunione),
                RIUNIONE.titolo,
                RIUNIONE.dataRegistrazione,
                listOf(vista(turnoRiunione, cRiunione[0])),
            ),
            a.lettore.trascritto(riunione),
        )
    }

    @Test
    public fun `AC-50 senza Elaborazioni completate registrazioniConTrascritto e vuota`() {
        val a = ambiente()
        assertTrue(a.lettore.registrazioniConTrascritto().isEmpty())

        a.aggiungiRegistrazione(RIUNIONE)
        a.fallisciElaborazione(a.aggiungiRegistrazione(INTERVISTA))
        assertTrue(a.lettore.registrazioniConTrascritto().isEmpty())
    }

    @Test
    public fun `AC-50 registrazioniConTrascritto elenca solo le Registrazioni con Elaborazione completata`() {
        val a = ambiente()
        val prima = a.aggiungiRegistrazione(RIUNIONE)
        a.aggiungiRegistrazione(INTERVISTA)
        val fallita = a.aggiungiRegistrazione(INTERVISTA)
        val seconda = a.aggiungiRegistrazione(INTERVISTA)
        a.fallisciElaborazione(fallita)
        a.completaElaborazione(prima, listOf(SemeTurno(0, IntervalloMs(0, 1_000), "Uno.")))
        a.completaElaborazione(seconda, listOf(SemeTurno(0, IntervalloMs(0, 1_000), "Due.")))

        val elencate = a.lettore.registrazioniConTrascritto()
        assertEquals(setOf(prima, seconda), elencate.toSet())
        assertEquals(2, elencate.size, "ogni Registrazione una sola volta: $elencate")
        assertOgniElencataHaTrascritto(a.lettore)
    }

    @Test
    public fun `AC-50 una Elaborazione completata dopo una fallita da il Trascritto ed e elencata`() {
        val a = ambiente()
        val id = a.aggiungiRegistrazione(RIUNIONE)
        a.fallisciElaborazione(id)
        assertNull(a.lettore.trascritto(id))

        val turno = SemeTurno(0, IntervalloMs(0, 1_000), "Riproviamo.")
        val c = a.completaElaborazione(id, listOf(turno))

        assertEquals(
            TrascrittoTesto(
                id,
                a.incontroDi(id),
                RIUNIONE.titolo,
                RIUNIONE.dataRegistrazione,
                listOf(vista(turno, c[0])),
            ),
            a.lettore.trascritto(id),
        )
        assertEquals(listOf(id), a.lettore.registrazioniConTrascritto())
        assertOgniElencataHaTrascritto(a.lettore)
    }

    @Test
    public fun `AC-I26 partiConTrascritto elenca la Parte solo quando ha un Trascritto`() {
        val a = ambiente()
        val mai = a.aggiungiRegistrazione(RIUNIONE)
        val fallita = a.aggiungiRegistrazione(INTERVISTA)
        val trascritta = a.aggiungiRegistrazione(INTERVISTA)
        a.fallisciElaborazione(fallita)
        assertEquals(emptyList(), a.lettore.partiConTrascritto(INCONTRO_SCONOSCIUTO))
        assertEquals(emptyList(), a.lettore.partiConTrascritto(a.incontroDi(trascritta)))

        a.completaElaborazione(trascritta, listOf(SemeTurno(0, IntervalloMs(0, 1_000), "Uno.")))

        assertEquals(listOf(trascritta), a.lettore.partiConTrascritto(a.incontroDi(trascritta)))
        assertEquals(emptyList(), a.lettore.partiConTrascritto(a.incontroDi(mai)))
        assertEquals(emptyList(), a.lettore.partiConTrascritto(a.incontroDi(fallita)))
        assertEquals(a.incontroDi(trascritta), a.lettore.trascritto(trascritta)?.incontroId)
        assertEquals(3, setOf(a.incontroDi(mai), a.incontroDi(fallita), a.incontroDi(trascritta)).size)
    }

    /** AC-I26 on an Incontro of several Parti (registered only when the supplier can seed one, D-0037). */
    @TestFactory
    public fun `AC-I26 Incontro con piu Parti`(): List<DynamicTest> =
        if (!ambiente().piuPartiPerIncontro) {
            emptyList()
        } else {
            listOf(
                dynamicTest("AC-I26 partiConTrascritto elenca solo le Parti trascritte nell ordine delle Parti") {
                    soloLePartiTrascritteInOrdine()
                },
                dynamicTest("AC-I26 ogni Parte porta l Incontro e i numeri di Voce dell Incontro") {
                    numeriDiVoceDellIncontro()
                },
            )
        }

    private fun soloLePartiTrascritteInOrdine() {
        val a = ambiente()
        val seconda = a.aggiungiRegistrazione(RIUNIONE.copy(dataRegistrazione = LocalDate.of(2026, 9, 22)))
        val incontro = a.incontroDi(seconda)
        val prima = a.aggiungiParte(incontro, RIUNIONE.copy(dataRegistrazione = LocalDate.of(2026, 9, 21)))
        val terza = a.aggiungiParte(incontro, RIUNIONE.copy(dataRegistrazione = LocalDate.of(2026, 9, 23)))
        val altra = a.aggiungiRegistrazione(INTERVISTA)
        val turno = listOf(SemeTurno(0, IntervalloMs(0, 1_000), "Uno."))
        a.completaElaborazione(altra, turno)
        a.completaElaborazione(terza, turno)
        a.completaElaborazione(seconda, turno)

        assertEquals(listOf(seconda, terza), a.lettore.partiConTrascritto(incontro))

        a.completaElaborazione(prima, turno)

        assertEquals(listOf(prima, seconda, terza), a.lettore.partiConTrascritto(incontro))
        assertEquals(listOf(altra), a.lettore.partiConTrascritto(a.incontroDi(altra)))
    }

    private fun numeriDiVoceDellIncontro() {
        val a = ambiente()
        val prima = a.aggiungiRegistrazione(RIUNIONE)
        val incontro = a.incontroDi(prima)
        val seconda = a.aggiungiParte(incontro, RIUNIONE.copy(dataRegistrazione = LocalDate.of(2026, 9, 22)))
        val cPrima = a.completaElaborazione(
            prima,
            listOf(
                SemeTurno(0, IntervalloMs(0, 1_000), "Buongiorno."),
                SemeTurno(1, IntervalloMs(1_000, 2_000), "Ciao."),
            ),
        )
        val turniSeconda = listOf(
            SemeTurno(0, IntervalloMs(0, 1_000), "Riprendiamo."),
            SemeTurno(0, IntervalloMs(1_000, 2_000), "Dicevo."),
        )
        val cSeconda = a.completaElaborazione(seconda, turniSeconda)

        assertEquals(incontro, a.incontroDi(seconda))
        assertEquals(incontro, a.lettore.trascritto(seconda)?.incontroId)
        assertTrue(cSeconda.none { c -> cPrima.any { it.voceId == c.voceId } }, "Voce nuova dell Incontro: $cSeconda")

        // Riassegnato a una Voce dell'Incontro che parla solo nella prima Parte: la seconda porta il suo numero.
        val voceDellaPrima: VoceId = cPrima[0].voceId
        assertEquals(voceDellaPrima, a.riassegna(seconda, cSeconda[1].segmentoId, voceDellaPrima))

        assertEquals(
            listOf(
                vista(turniSeconda[0], cSeconda[0]),
                vista(turniSeconda[1], cSeconda[1].copy(voceId = voceDellaPrima)),
            ),
            a.lettore.trascritto(seconda)?.segmenti,
        )
    }

    private fun assertOgniElencataHaTrascritto(lettore: LettoreTrascritto) {
        lettore.registrazioniConTrascritto().forEach {
            assertNotNull(lettore.trascritto(it), "elencata senza Trascritto: ${it.valore}")
        }
    }

    private fun vista(turno: SemeTurno, coniato: SegmentoConiato) =
        SegmentoVista(coniato.segmentoId, coniato.voceId, turno.intervallo, turno.testo)

    private companion object {
        val SCONOSCIUTA = RegistrazioneId("registrazione-sconosciuta")
        val INCONTRO_SCONOSCIUTO = IncontroId("incontro-sconosciuto")
        val RIUNIONE = SemeRegistrazione("Riunione di progetto", LocalDate.of(2026, 9, 21), 60_000L)
        val INTERVISTA = SemeRegistrazione("Intervista", LocalDate.of(2025, 12, 31), 30_000L)
    }
}
