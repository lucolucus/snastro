package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.GeneratoreId
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.trascrizione.applicazione.eventi.ElaborazioneAvviata
import snastro.trascrizione.applicazione.letture.ElaborazioniInAttesa
import snastro.trascrizione.applicazione.porte.AllineatoreFinta
import snastro.trascrizione.applicazione.porte.DecodificatoreAudioFinta
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.applicazione.porte.LettoreRegistrazioneFinta
import snastro.trascrizione.applicazione.porte.RegistrazioneVista
import snastro.trascrizione.applicazione.porte.SegnalatoreFaseFinta
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepositoryFinta
import snastro.trascrizione.dominio.Elaborazione
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneGiaAperta
import snastro.trascrizione.dominio.ErroreTrascrizione.IncontroNonTrovato
import snastro.trascrizione.dominio.ErroreTrascrizione.NessunaParteDaTrascrivere
import snastro.trascrizione.dominio.ErroreTrascrizione.NumeroPersoneFuoriIntervallo
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.trascrizione.dominio.unSegmentoIniziale
import snastro.trascrizione.dominio.unaElaborazione
import snastro.trascrizione.dominio.unaRadiceDa
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

/** ADR 0039: 'Trascrivi' on an Incontro queues its untranscribed Parti, in Parte order, in one transaction. */
class AvviaElaborazioniDellIncontroServizioTest {
    private val elaborazioni = ElaborazioneRepositoryFinta()
    private val trascritti = VociDellIncontroRepositoryFinta()
    private val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
    private val lettore = LettoreRegistrazioneFinta(
        registrazioni = listOf(A, B, C).associateWith { vista(it) },
        ordine = mapOf(INCONTRO to listOf(A, B, C)),
    )

    private fun servizio(orologio: Clock = OROLOGIO) = AvviaElaborazioniDellIncontroServizio(
        eventi.unitaDiLavoro,
        GeneratoreIdFinto(),
        orologio,
        lettore,
        elaborazioni,
        trascritti,
        eventi,
    )

    private fun trascrivi(r: RegistrazioneId) {
        val turni = listOf(unSegmentoIniziale(voceIndice = 0, inizioMs = 0))
        trascritti.salva(unaRadiceDa(r, IncontroId("altro-${r.valore}"), 10_000, turni))
    }

    @Test
    fun `A e C non trascritte con B trascritta accodano due Elaborazioni con numeroPersone 4 a t e t+1ms`() {
        trascrivi(B)

        servizio().esegui(AvviaElaborazioniDellIncontro(INCONTRO, 4)).atteso()

        val a = elaborazioni.diRegistrazione(A).single()
        val c = elaborazioni.diRegistrazione(C).single()
        assertEquals(listOf(StatoElaborazione.IN_ATTESA, StatoElaborazione.IN_ATTESA), listOf(a.stato, c.stato))
        assertEquals(listOf(4, 4), listOf(a.numeroPersone?.valore, c.numeroPersone?.valore))
        assertEquals(listOf(T, T.plusMillis(1)), listOf(a.creataAlle, c.creataAlle))
        assertEquals(emptyList(), elaborazioni.diRegistrazione(B))
        val attesi = listOf<EventoPubblicato>(ElaborazioneAvviata(A, T), ElaborazioneAvviata(C, T.plusMillis(1)))
        assertEquals(attesi, eventi.pubblicati)
    }

    @Test
    fun `una Parte con Elaborazione aperta non viene riaccodata mentre una fallita si`() {
        elaborazioni.salva(unaElaborazione(StatoElaborazione.IN_CORSO, ElaborazioneId("x"), A)).atteso()
        elaborazioni.salva(unaElaborazione(StatoElaborazione.FALLITA, ElaborazioneId("y"), B)).atteso()

        servizio().esegui(AvviaElaborazioniDellIncontro(INCONTRO)).atteso()

        assertEquals(1, elaborazioni.diRegistrazione(A).size)
        assertEquals(2, elaborazioni.diRegistrazione(B).size)
        assertEquals(1, elaborazioni.diRegistrazione(C).size)
    }

    @Test
    fun `ogni Parte trascritta o in corso rifiuta con NessunaParteDaTrascrivere e non scrive`() {
        trascrivi(A)
        elaborazioni.salva(unaElaborazione(StatoElaborazione.IN_ATTESA, ElaborazioneId("x"), B)).atteso()
        trascrivi(C)

        val errore = servizio()
            .esegui(AvviaElaborazioniDellIncontro(INCONTRO))
            .erroreAtteso<NessunaParteDaTrascrivere>()

        assertEquals(NessunaParteDaTrascrivere(INCONTRO), errore)
        assertEquals(1, elaborazioni.inAttesa().size)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `un Incontro sconosciuto rifiuta con IncontroNonTrovato`() {
        val ignoto = IncontroId("ignoto")

        val errore = servizio().esegui(AvviaElaborazioniDellIncontro(ignoto)).erroreAtteso<IncontroNonTrovato>()

        assertEquals(IncontroNonTrovato(ignoto), errore)
        assertEquals(emptyList(), elaborazioni.inAttesa())
    }

    @Test
    fun `un numeroPersone fuori intervallo rifiuta prima di scrivere`() {
        val errore = servizio().esegui(AvviaElaborazioniDellIncontro(INCONTRO, 11))
            .erroreAtteso<NumeroPersoneFuoriIntervallo>()

        assertEquals(NumeroPersoneFuoriIntervallo(11), errore)
        assertEquals(emptyList(), elaborazioni.inAttesa())
    }

    @Test
    fun `se una Parte rifiuta il salvataggio nessuna Elaborazione resta e nessun evento esce`() {
        val finto = object : ElaborazioneRepository by elaborazioni {
            override fun salva(e: Elaborazione): Esito<Unit> =
                if (e.registrazioneId == C) Esito.Errore(ElaborazioneGiaAperta(C)) else elaborazioni.salva(e)
        }
        val s = AvviaElaborazioniDellIncontroServizio(
            eventi.unitaDiLavoro,
            GeneratoreIdFinto(),
            OROLOGIO,
            lettore,
            finto,
            trascritti,
            eventi,
        )

        s.esegui(AvviaElaborazioniDellIncontro(INCONTRO)).erroreAtteso<ElaborazioneGiaAperta>()

        assertEquals(emptyList(), elaborazioni.inAttesa())
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-I33 con un orologio fermo la coda condivisa serve A prima di C`() {
        servizio(Clock.fixed(T, ZoneOffset.UTC)).esegui(AvviaElaborazioniDellIncontro(INCONTRO)).atteso()

        val coda = ElaborazioniInAttesa(elaborazioni).elenco()

        assertEquals(listOf(A, B, C), coda.map { it.registrazioneId })
        assertEquals(listOf(T, T.plusMillis(1), T.plusMillis(2)), coda.map { it.creataAlle })
    }

    @Test
    fun `AC-I33 con un orologio fermo e id in ordine inverso la vera presa avvia A, poi B, poi C`() {
        val idInversi = ArrayDeque(listOf("elab-z", "elab-y", "elab-x")) // the id tie-break alone would pick C first
        val accoda = AvviaElaborazioniDellIncontroServizio(
            eventi.unitaDiLavoro,
            object : GeneratoreId {
                override fun nuovo(): String = idInversi.removeFirst()
            },
            Clock.fixed(T, ZoneOffset.UTC),
            lettore,
            elaborazioni,
            trascritti,
            eventi,
        )
        accoda.esegui(AvviaElaborazioniDellIncontro(INCONTRO)).atteso()
        val pipeline = PortePipeline(
            LettoreRegistrazioneFinta(), // no Registrazione: each claimed run then fails, only the claim matters here
            DecodificatoreAudioFinta(emptyMap()),
            DiarizzatoreFinta(),
            AllineatoreFinta(),
            SegnalatoreFaseFinta(),
        )
        val presa = EseguiProssimaElaborazioneServizio(
            eventi.unitaDiLavoro,
            OROLOGIO,
            elaborazioni,
            trascritti,
            pipeline,
            eventi,
        )

        val avviate = List(3) { presa.esegui(EseguiProssimaElaborazione()).atteso() }

        val idDi = listOf(A, B, C).map { RisultatoAvanzamento.Avviata(elaborazioni.diRegistrazione(it).single().id) }
        assertEquals(idDi, avviate)
    }

    private companion object {
        val A = RegistrazioneId("parte-A")
        val B = RegistrazioneId("parte-B")
        val C = RegistrazioneId("parte-C")
        val INCONTRO = IncontroId("incontro-1")
        val T: Instant = Instant.parse("2026-10-02T10:00:00Z")
        val OROLOGIO: Clock = Clock.fixed(T, ZoneOffset.UTC)

        fun vista(id: RegistrazioneId) = RegistrazioneVista(
            registrazioneId = id,
            progettoId = ProgettoId("progetto-1"),
            incontroId = INCONTRO,
            titolo = "Riunione",
            riferimentoAudio = RiferimentoAudio("audio/${id.valore}.m4a"),
            dataRegistrazione = LocalDate.of(2026, 10, 2),
            durataMs = 10_000,
        )
    }
}
