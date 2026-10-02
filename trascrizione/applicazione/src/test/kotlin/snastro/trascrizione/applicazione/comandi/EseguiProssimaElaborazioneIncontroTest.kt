package snastro.trascrizione.applicazione.comandi

import snastro.kernel.CampioniAudio
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.ElaborazioneId
import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.trascrizione.applicazione.eventi.ElaborazioneAvviata
import snastro.trascrizione.applicazione.eventi.ElaborazioneCompletata
import snastro.trascrizione.applicazione.eventi.ElaborazioneFallita
import snastro.trascrizione.applicazione.eventi.TrascrittoSostituito
import snastro.trascrizione.applicazione.porte.Allineatore
import snastro.trascrizione.applicazione.porte.AllineatoreFinta
import snastro.trascrizione.applicazione.porte.DecodificatoreAudioFinta
import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione
import snastro.trascrizione.applicazione.porte.RegistrazioneVista
import snastro.trascrizione.applicazione.porte.SegnalatoreFaseFinta
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepositoryFinta
import snastro.trascrizione.dominio.Elaborazione
import snastro.trascrizione.dominio.NumeroPersone
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ADR 0035 §3 at Incontro scope (INV-5, INV-I5, INV-I16): the completion of one Parte re-reads the root of the
 * Parte's Incontro, so a first run of B adds Voci after A's numbers and a re-run of A keeps what it shares with B.
 * Split from [EseguiProssimaElaborazioneRitrascriviTest], which covers the one-Parte Incontro.
 */
class EseguiProssimaElaborazioneIncontroTest {
    @Test
    fun `la prima trascrizione di B aggiunge Voci nuove dopo quelle di A senza TrascrittoSostituito`() {
        val s = Scenario()
        s.esegui(A)
        val gia = s.eventi.pubblicati.size

        s.esegui(B)

        val radice = checkNotNull(s.trascritti.trova(INCONTRO))
        assertEquals(listOf(A, B), radice.trascritti.map { it.registrazioneId })
        assertEquals(listOf(1, 2), radice.trascritto(A)?.segmenti?.map { it.voceId.numero }, "A invariata")
        assertEquals(listOf(3, 4), radice.trascritto(B)?.segmenti?.map { it.voceId.numero }, "Voci dopo i numeri di A")
        assertEquals(listOf(1, 2), radice.trascritto(B)?.segmenti?.map { it.id.numero }, "Segmenti numerati per Parte")
        assertEquals(
            listOf(ElaborazioneAvviata(B, OROLOGIO.instant()), ElaborazioneCompletata(B, INCONTRO)),
            s.eventi.pubblicati.drop(gia),
        )
    }

    @Test
    fun `la ritrascrizione di A tiene le Voci condivise con B pubblica vociRimosse prima di Completata`() {
        val s = Scenario()
        s.esegui(A)
        s.esegui(B)
        // Voce 3 of B is joined to Voce 1 of A: Voce 1 now speaks in both Parti, Voce 3 ceased, Voce 2 stays in A only.
        val radice = checkNotNull(s.trascritti.trova(INCONTRO))
        radice.unisci(VoceId(1), VoceId(3)).atteso()
        s.trascritti.salva(radice)
        val gia = s.eventi.pubblicati.size

        s.ritrascrivi(A)

        val dopo = checkNotNull(s.trascritti.trova(INCONTRO))
        assertEquals(listOf(1, 4, 5, 6), dopo.voci.map { it.numero }, "Voce 1 (condivisa con B) resta, 2 cessa")
        assertEquals(listOf(5, 6), dopo.trascritto(A)?.segmenti?.map { it.voceId.numero }, "nuove Voci dal contatore")
        assertEquals(listOf(3, 4), dopo.trascritto(A)?.segmenti?.map { it.id.numero }, "Segmenti dopo gli id di A")
        assertEquals(
            listOf(
                ElaborazioneAvviata(A, OROLOGIO.instant()),
                TrascrittoSostituito(A, INCONTRO, vociRimosse = setOf(VoceId(2))),
                ElaborazioneCompletata(A, INCONTRO),
            ),
            s.eventi.pubblicati.drop(gia),
        )
    }

    @Test
    fun `un fallimento di B non cambia la radice ne pubblica eventi di completamento`() {
        val s = Scenario()
        s.esegui(A)
        val prima = checkNotNull(s.trascritti.trova(INCONTRO)).trascritti.map { it.registrazioneId to it.segmenti }
        val gia = s.eventi.pubblicati.size

        s.esegui(B, allineatore = AllineatoreMuto())

        val dopo = checkNotNull(s.trascritti.trova(INCONTRO)).trascritti.map { it.registrazioneId to it.segmenti }
        assertEquals(prima, dopo)
        assertTrue(s.eventi.pubblicati.drop(gia).none { it is ElaborazioneCompletata || it is TrascrittoSostituito })
    }

    /** The Registrazione vanishing between the pipeline and the completion transaction refuses the completion. */
    @Test
    fun `INV-I16 se la Registrazione sparisce prima del commit la completata diventa fallita e la radice non cambia`() {
        val s = Scenario()
        s.esegui(A)
        val primaA = s.trascritti.trascritto(A)?.segmenti
        val gia = s.eventi.pubblicati.size

        s.eseguiB(mentreGira = { s.registrazioni.remove(B) }) // deleted while the pipeline runs

        assertEquals(null, s.trascritti.trascritto(B))
        assertEquals(primaA, s.trascritti.trascritto(A)?.segmenti, "A invariata")
        assertTrue(s.elaborazioni.trova(ElaborazioneId("e-B"))?.fallita == true)
        assertEquals(
            listOf(
                ElaborazioneAvviata(B, OROLOGIO.instant()),
                ElaborazioneFallita(B, "registrazione non più disponibile"),
            ),
            s.eventi.pubblicati.drop(gia),
        )
    }

    @Test
    fun `INV-I16 un guasto nel rileggere la Registrazione al commit e fallita col motivo della lettura`() {
        val s = Scenario()
        s.esegui(A)
        val primaA = s.trascritti.trascritto(A)?.segmenti
        val gia = s.eventi.pubblicati.size

        s.eseguiB(mentreGira = { s.guasta = true })

        assertEquals(null, s.trascritti.trascritto(B))
        assertEquals(primaA, s.trascritti.trascritto(A)?.segmenti, "A invariata")
        assertEquals(
            listOf(
                ElaborazioneAvviata(B, OROLOGIO.instant()),
                ElaborazioneFallita(B, "impossibile leggere i dati della registrazione"),
            ),
            s.eventi.pubblicati.drop(gia),
        )
    }

    @Test
    fun `ADR 0035 la radice riletta al commit e quella dell Incontro della nuova lettura della Registrazione`() {
        val s = Scenario()
        val altro = IncontroId("incontro-2")

        s.eseguiB(mentreGira = { s.registrazioni[B] = vista(B).copy(incontroId = altro) })

        assertEquals(null, s.trascritti.trova(INCONTRO))
        assertEquals(listOf(B), s.trascritti.trova(altro)?.trascritti?.map { it.registrazioneId })
        assertEquals(ElaborazioneCompletata(B, altro), s.eventi.pubblicati.last())
    }

    private class Scenario {
        val elaborazioni = ElaborazioneRepositoryFinta()
        val trascritti = VociDellIncontroRepositoryFinta()
        val uow = UnitaDiLavoroFinta(elaborazioni, trascritti)
        val eventi = DispatcherEventiFinta(uow)
        val segnalatore = SegnalatoreFaseFinta()
        val registrazioni = mutableMapOf(A to vista(A), B to vista(B))
        var guasta = false
        private var n = 0

        private val lettore = object : LettoreRegistrazione {
            override fun registrazione(id: RegistrazioneId) = registrazioni[id].also { check(!guasta) { "guasto" } }
            override fun parti(incontroId: IncontroId) = null
        }

        fun servizio(
            diarizzatore: Diarizzatore = DiarizzatoreFinta(TURNI),
            allineatore: Allineatore = AllineatoreFinta(),
        ) = EseguiProssimaElaborazioneServizio(
            eventi.unitaDiLavoro,
            OROLOGIO,
            elaborazioni,
            trascritti,
            PortePipeline(
                lettore,
                DecodificatoreAudioFinta(mapOf(riferimento(A) to DURATA, riferimento(B) to DURATA)),
                diarizzatore,
                allineatore,
                segnalatore,
            ),
            eventi,
        )

        fun esegui(
            parte: RegistrazioneId,
            allineatore: Allineatore = AllineatoreFinta(),
        ) {
            accoda(parte)
            servizio(allineatore = allineatore).esegui(EseguiProssimaElaborazione()).atteso()
        }

        fun ritrascrivi(parte: RegistrazioneId) = esegui(parte)

        /** Runs the Elaborazione `e-B` of [B], calling [mentreGira] while its pipeline runs (after the lookup). */
        fun eseguiB(mentreGira: () -> Unit) {
            elaborazioni.salva(Elaborazione.accoda(ElaborazioneId("e-B"), B, DOPO, null).aggregato).atteso()
            val diarizzatore = object : Diarizzatore {
                override fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno> {
                    mentreGira()
                    return DiarizzatoreFinta(TURNI).diarizza(c, numeroPersone)
                }
            }
            servizio(diarizzatore = diarizzatore).esegui(EseguiProssimaElaborazione()).atteso()
        }

        private fun accoda(parte: RegistrazioneId) {
            val id = ElaborazioneId("e-${parte.valore}-${n++}")
            elaborazioni.salva(Elaborazione.accoda(id, parte, DOPO.plusSeconds(n.toLong()), null).aggregato).atteso()
        }
    }

    private companion object {
        val A = RegistrazioneId("parte-A")
        val B = RegistrazioneId("parte-B")
        val INCONTRO = IncontroId("incontro-1")
        const val DURATA = 2_000L
        val OROLOGIO: Clock = Clock.fixed(Instant.parse("2026-09-24T10:10:00Z"), ZoneOffset.UTC)
        val DOPO: Instant = Instant.parse("2026-09-24T09:00:00Z")
        val TURNI = listOf(
            Turno(IntervalloMs(0, 800), voceIndice = 1),
            Turno(IntervalloMs(1_000, 1_800), voceIndice = 0),
        )

        fun riferimento(id: RegistrazioneId) = RiferimentoAudio("audio/${id.valore}.m4a")

        fun vista(id: RegistrazioneId) = RegistrazioneVista(
            registrazioneId = id,
            incontroId = INCONTRO,
            progettoId = ProgettoId("progetto-1"),
            titolo = "Riunione",
            riferimentoAudio = riferimento(id),
            dataRegistrazione = LocalDate.of(2026, 9, 20),
            durataMs = DURATA,
        )
    }
}
