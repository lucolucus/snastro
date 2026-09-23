package snastro.parlanti.applicazione.comandi

import snastro.kernel.CampioniAudio
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.Esito
import snastro.kernel.GeneratoreIdFinto
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
import snastro.parlanti.applicazione.eventi.ParlanteCreato
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.porte.AttribuzioneRepository
import snastro.parlanti.applicazione.porte.AttribuzioneRepositoryFinta
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.parlanti.applicazione.porte.LettoreRegistrazione
import snastro.parlanti.applicazione.porte.LettoreRegistrazioneFinta
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.applicazione.porte.LettoreVociFinta
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
import snastro.parlanti.applicazione.porte.RegistrazioneVista
import snastro.parlanti.applicazione.porte.RigaImpronta
import snastro.parlanti.applicazione.porte.VoceVista
import snastro.parlanti.dominio.Attribuzione
import snastro.parlanti.dominio.ErroreParlanti
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.TipoParlante
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfermaAttribuzioneServizioTest {

    @Test
    fun `AC-84 confermare un Candidato crea l Attribuzione, l ImprontaVocale e pubblica AttribuzioneConfermata`() {
        val p = unParlante(ParlanteId("p-1"), "Marco")
        val ambiente = Ambiente(parlanti = ParlanteRepositoryFinta().apply { salva(p).atteso() })

        ambiente.servizio.esegui(ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.ParlanteEsistente(p.id))).atteso()

        val attribuzione = assertNotNull(ambiente.attribuzioni.trova(VOCE_1))
        assertEquals(p.id, attribuzione.parlanteId)
        val salvato = assertNotNull(ambiente.parlanti.trova(p.id))
        assertEquals(listOf(VOCE_1), salvato.impronte.map { it.voceRef })
        assertEquals(listOf(AttribuzioneConfermata(VOCE_1, p.id, precedente = null)), ambiente.eventi.pubblicati)
    }

    @Test
    fun `AC-85 un nuovo Parlante senza tipo scelto e ricorrente di default`() {
        val ambiente = Ambiente()

        ambiente.servizio.esegui(ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.NuovoParlante("Giulia"))).atteso()

        val creato = assertNotNull(ambiente.parlanti.trova(ParlanteId("id-1")))
        assertEquals("Giulia", creato.nome.valore)
        assertEquals(TipoParlante.RICORRENTE, creato.tipo)
        assertEquals(creato.id, ambiente.attribuzioni.trova(VOCE_1)?.parlanteId)
        assertEquals(
            listOf(
                ParlanteCreato(ParlanteId("id-1"), PROGETTO, "Giulia", TipoParlanteVista.RICORRENTE),
                AttribuzioneConfermata(VOCE_1, ParlanteId("id-1"), precedente = null),
            ),
            ambiente.eventi.pubblicati,
        )
    }

    @Test
    fun `AC-85 un nuovo Parlante puo essere scelto occasionale`() {
        val ambiente = Ambiente()

        val obiettivo = ObiettivoAttribuzione.NuovoParlante("Ospite del 20-09-2026", TipoParlante.OCCASIONALE)
        ambiente.servizio.esegui(ConfermaAttribuzione(VOCE_1, obiettivo)).atteso()

        val creato = assertNotNull(ambiente.parlanti.trova(ParlanteId("id-1")))
        assertTrue(creato.occasionale)
        assertTrue(
            ambiente.eventi.pubblicati.any { it is ParlanteCreato && it.tipo == TipoParlanteVista.OCCASIONALE },
        )
    }

    @Test
    fun `INV-16 un Nome gia usato, anche con spazi o maiuscole diverse, rifiuta con NomeGiaInUso`() {
        val esistente = unParlante(ParlanteId("p-1"), "Marco Rossi")
        val ambiente = Ambiente(parlanti = ParlanteRepositoryFinta().apply { salva(esistente).atteso() })

        val errore = ambiente.servizio.esegui(
            ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.NuovoParlante("  MARCO rossi  ")),
        ).erroreAtteso<ErroreParlanti.NomeGiaInUso>()

        assertEquals(ErroreParlanti.NomeGiaInUso("MARCO rossi"), errore)
        assertEquals(listOf(esistente.id), ambiente.parlanti.delProgetto(PROGETTO).map { it.id })
        assertNull(ambiente.attribuzioni.trova(VOCE_1))
        assertEquals(emptyList(), ambiente.eventi.pubblicati)
    }

    @Test
    fun `INV-16 il Nome di un eliminato e riusabile`() {
        val eliminato = unParlante(ParlanteId("p-1"), "Marco").also { it.elimina().atteso() }
        val ambiente = Ambiente(parlanti = ParlanteRepositoryFinta().apply { salva(eliminato).atteso() })

        ambiente.servizio.esegui(ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.NuovoParlante("Marco"))).atteso()

        val attivi = ambiente.parlanti.delProgetto(PROGETTO).filter { it.attivo }
        assertEquals(listOf("Marco"), attivi.map { it.nome.valore })
    }

    @Test
    fun `INV-16 se l indice segnala la violazione il servizio restituisce lo stesso NomeGiaInUso`() {
        val attribuzioni = AttribuzioneRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(attribuzioni))
        val servizio = ConfermaAttribuzioneServizio(
            eventi.unitaDiLavoro,
            GeneratoreIdFinto(),
            LettoreRegistrazioneFinta(mapOf(REGISTRAZIONE to unaRegistrazioneVista())),
            LettoreVociFinta(mapOf(REGISTRAZIONE to listOf(unaVoceVista(1)))),
            ParlanteRepositoryCheSegnalaLaGara(),
            attribuzioni,
            DecodificatoreAudioFinta(),
            EstrattoreImprontaFinta(),
            eventi,
        )

        val errore = servizio.esegui(ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.NuovoParlante("Marco")))
            .erroreAtteso<ErroreParlanti.NomeGiaInUso>()

        assertEquals(ErroreParlanti.NomeGiaInUso("Marco"), errore)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `INV-16 il servizio pre-verifica il Nome prima di salvare, non si appoggia solo al repository`() {
        val stub = ParlanteRepositoryCheNonSiAutoverifica()
        val attribuzioni = AttribuzioneRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(attribuzioni))
        val servizio = ConfermaAttribuzioneServizio(
            eventi.unitaDiLavoro,
            GeneratoreIdFinto(),
            LettoreRegistrazioneFinta(mapOf(REGISTRAZIONE to unaRegistrazioneVista())),
            LettoreVociFinta(mapOf(REGISTRAZIONE to listOf(unaVoceVista(1)))),
            stub,
            attribuzioni,
            DecodificatoreAudioFinta(),
            EstrattoreImprontaFinta(),
            eventi,
        )

        servizio.esegui(ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.NuovoParlante("Marco")))
            .erroreAtteso<ErroreParlanti.NomeGiaInUso>()

        assertEquals(0, stub.salvataggi, "il servizio deve rifiutare PRIMA di chiamare salva")
    }

    @Test
    fun `INV-15 cambiare l attribuzione da P a Q sposta l impronta di v, P non ne ha piu per quella Voce`() {
        val p = unParlante(ParlanteId("p-1"), "Piero")
        val q = unParlante(ParlanteId("p-2"), "Quinto")
        val parlanti = ParlanteRepositoryFinta().apply {
            salva(p).atteso()
            salva(q).atteso()
        }
        val ambiente = Ambiente(
            parlanti = parlanti,
            lettoreVoci = LettoreVociFinta(mapOf(REGISTRAZIONE to listOf(unaVoceVista(1), unaVoceVista(2)))),
        )
        ambiente.servizio.esegui(ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.ParlanteEsistente(p.id))).atteso()
        ambiente.servizio.esegui(ConfermaAttribuzione(VOCE_2, ObiettivoAttribuzione.ParlanteEsistente(p.id))).atteso()

        ambiente.servizio.esegui(ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.ParlanteEsistente(q.id))).atteso()

        val pOra = assertNotNull(ambiente.parlanti.trova(p.id))
        val qOra = assertNotNull(ambiente.parlanti.trova(q.id))
        assertEquals(listOf(VOCE_2), pOra.impronte.map { it.voceRef }, "P non ha piu l impronta di v")
        assertEquals(listOf(VOCE_1), qOra.impronte.map { it.voceRef })
        assertEquals(q.id, ambiente.attribuzioni.trova(VOCE_1)?.parlanteId)
        assertTrue(pOra.attivo, "P (ricorrente) resta attivo con le altre impronte, INV-25")
        assertEquals(
            AttribuzioneConfermata(VOCE_1, q.id, precedente = p.id),
            ambiente.eventi.pubblicati.last(),
        )
    }

    @Test
    fun `INV-25 un occasionale rimasto senza Attribuzioni cessa di esistere, un ricorrente resta`() {
        val occasionale = unParlante(ParlanteId("p-1"), "Ospite del 20-09-2026", tipo = TipoParlante.OCCASIONALE)
        val q = unParlante(ParlanteId("p-2"), "Quinto")
        val parlanti = ParlanteRepositoryFinta().apply {
            salva(occasionale).atteso()
            salva(q).atteso()
        }
        val ambiente = Ambiente(parlanti = parlanti)
        ambiente.servizio.esegui(
            ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.ParlanteEsistente(occasionale.id)),
        ).atteso()

        ambiente.servizio.esegui(ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.ParlanteEsistente(q.id))).atteso()

        assertNull(ambiente.parlanti.trova(occasionale.id), "l occasionale cessa di esistere")
        assertEquals(emptyList(), ambiente.attribuzioni.diParlante(occasionale.id))
        val qOra = assertNotNull(ambiente.parlanti.trova(q.id))
        assertEquals(listOf(VOCE_1), qOra.impronte.map { it.voceRef })
    }

    @Test
    fun `INV-25 un ricorrente con una sola Attribuzione non cessa di esistere quando la perde`() {
        val p = unParlante(ParlanteId("p-1"), "Piero") // RICORRENTE di default
        val q = unParlante(ParlanteId("p-2"), "Quinto")
        val parlanti = ParlanteRepositoryFinta().apply {
            salva(p).atteso()
            salva(q).atteso()
        }
        val ambiente = Ambiente(parlanti = parlanti)
        ambiente.servizio.esegui(ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.ParlanteEsistente(p.id))).atteso()

        ambiente.servizio.esegui(ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.ParlanteEsistente(q.id))).atteso()

        val pOra = assertNotNull(
            ambiente.parlanti.trova(p.id),
            "il ricorrente NON cessa di esistere, a differenza dell'occasionale",
        )
        assertTrue(pOra.attivo)
        assertEquals(emptyList(), pOra.impronte, "P non ha piu' nessuna impronta")
        assertEquals(emptyList(), ambiente.attribuzioni.diParlante(p.id))
    }

    @Test
    fun `INV-25 un occasionale che perde una Voce ma ne mantiene un altra resta con la sua impronta`() {
        val occasionale = unParlante(ParlanteId("p-1"), "Ospite del 20-09-2026", tipo = TipoParlante.OCCASIONALE)
        val q = unParlante(ParlanteId("p-2"), "Quinto")
        val parlanti = ParlanteRepositoryFinta().apply {
            salva(occasionale).atteso()
            salva(q).atteso()
        }
        val ambiente = Ambiente(
            parlanti = parlanti,
            lettoreVoci = LettoreVociFinta(mapOf(REGISTRAZIONE to listOf(unaVoceVista(1), unaVoceVista(2)))),
        )
        ambiente.servizio.esegui(
            ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.ParlanteEsistente(occasionale.id)),
        ).atteso()
        ambiente.servizio.esegui(
            ConfermaAttribuzione(VOCE_2, ObiettivoAttribuzione.ParlanteEsistente(occasionale.id)),
        ).atteso()

        ambiente.servizio.esegui(ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.ParlanteEsistente(q.id))).atteso()

        val occasionaleOra = assertNotNull(
            ambiente.parlanti.trova(occasionale.id),
            "l occasionale resta: ha ancora un altra Attribuzione (Voce 2)",
        )
        assertTrue(occasionaleOra.attivo)
        assertEquals(listOf(VOCE_2), occasionaleOra.impronte.map { it.voceRef })
        assertEquals(listOf(VOCE_2), ambiente.attribuzioni.diParlante(occasionale.id).map { it.voceRef })
    }

    @Test
    fun `INV-25 un occasionale gia eliminato non viene ri-rimosso quando la sua ultima Attribuzione si sposta`() {
        val eliminato = unParlante(ParlanteId("p-1"), "Ospite del 20-09-2026", tipo = TipoParlante.OCCASIONALE)
        val q = unParlante(ParlanteId("p-2"), "Quinto")
        val parlanti = ParlanteRepositoryFinta().apply {
            salva(eliminato).atteso()
            salva(q).atteso()
        }
        val ambiente = Ambiente(parlanti = parlanti)
        ambiente.servizio.esegui(
            ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.ParlanteEsistente(eliminato.id)),
        ).atteso()
        // Simula EliminaParlante (sibling block, gestione-parlante): purga le impronte, marca
        // ELIMINATO, MA non tocca l'Attribuzione — resta un tombstone referenziato da Attribuzione(v).
        val tombstone = assertNotNull(ambiente.parlanti.trova(eliminato.id))
        tombstone.elimina().atteso()
        ambiente.parlanti.salva(tombstone).atteso()

        ambiente.servizio.esegui(ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.ParlanteEsistente(q.id))).atteso()

        val tombstoneOra = assertNotNull(
            ambiente.parlanti.trova(eliminato.id),
            "il tombstone eliminato NON viene rimosso da INV-25 (quella si applica solo a un attivo)",
        )
        assertTrue(tombstoneOra.eliminato)
        assertEquals(q.id, ambiente.attribuzioni.trova(VOCE_1)?.parlanteId)
    }

    @Test
    fun `INV-17 un Parlante eliminato viene rifiutato`() {
        val eliminato = unParlante(ParlanteId("p-1"), "Marco").also { it.elimina().atteso() }
        val ambiente = Ambiente(parlanti = ParlanteRepositoryFinta().apply { salva(eliminato).atteso() })

        val errore = ambiente.servizio.esegui(
            ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.ParlanteEsistente(eliminato.id)),
        ).erroreAtteso<ErroreParlanti.ParlanteEliminatoNonModificabile>()

        assertEquals(ErroreParlanti.ParlanteEliminatoNonModificabile(eliminato.id), errore)
        assertNull(ambiente.attribuzioni.trova(VOCE_1))
    }

    @Test
    fun `INV-17 un Parlante di un altro Progetto viene rifiutato come ParlanteNonTrovato`() {
        val altroProgetto = unParlante(ParlanteId("p-1"), "Marco", progettoId = ProgettoId("altro-progetto"))
        val ambiente = Ambiente(parlanti = ParlanteRepositoryFinta().apply { salva(altroProgetto).atteso() })

        val errore = ambiente.servizio.esegui(
            ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.ParlanteEsistente(altroProgetto.id)),
        ).erroreAtteso<ErroreParlanti.ParlanteNonTrovato>()

        assertEquals(ErroreParlanti.ParlanteNonTrovato(altroProgetto.id), errore)
    }

    @Test
    fun `INV-17 una Voce inesistente nel Trascritto viene rifiutata`() {
        val ambiente = Ambiente(lettoreVoci = LettoreVociFinta(mapOf(REGISTRAZIONE to listOf(unaVoceVista(1)))))

        val errore = ambiente.servizio.esegui(
            ConfermaAttribuzione(unaVoce(9), ObiettivoAttribuzione.NuovoParlante("Marco")),
        ).erroreAtteso<ErroreParlanti.VoceNonTrovata>()

        assertEquals(ErroreParlanti.VoceNonTrovata(unaVoce(9)), errore)
    }

    @Test
    fun `INV-17 una Registrazione senza Trascritto viene rifiutata`() {
        val ambiente = Ambiente(lettoreVoci = LettoreVociFinta())

        val errore = ambiente.servizio.esegui(
            ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.NuovoParlante("Marco")),
        ).erroreAtteso<ErroreParlanti.TrascrittoNonTrovato>()

        assertEquals(ErroreParlanti.TrascrittoNonTrovato(REGISTRAZIONE), errore)
    }

    @Test
    fun `INV-17 una Registrazione sconosciuta viene rifiutata come TrascrittoNonTrovato`() {
        val ambiente = Ambiente(registrazioni = LettoreRegistrazioneFinta())

        val errore = ambiente.servizio.esegui(
            ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.NuovoParlante("Marco")),
        ).erroreAtteso<ErroreParlanti.TrascrittoNonTrovato>()

        assertEquals(ErroreParlanti.TrascrittoNonTrovato(REGISTRAZIONE), errore)
    }

    @Test
    fun `AC-86 se l estrazione dell impronta fallisce il rollback e completo`() {
        val p = unParlante(ParlanteId("p-1"), "Marco")
        val ambiente = Ambiente(
            parlanti = ParlanteRepositoryFinta().apply { salva(p).atteso() },
            estrattore = EstrattoreImprontaCheFallisce(),
        )

        assertFailsWith<GuastoEstrazioneDiProva> {
            ambiente.servizio.esegui(ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.ParlanteEsistente(p.id)))
        }

        assertNull(ambiente.attribuzioni.trova(VOCE_1))
        val pOra = assertNotNull(ambiente.parlanti.trova(p.id))
        assertEquals(emptyList(), pOra.impronte)
        assertEquals(emptyList(), ambiente.eventi.pubblicati)
    }

    @Test
    fun `AC-86 estrazione fallita in un cambio di attribuzione, P invariato e nulla pubblicato`() {
        val p = unParlante(ParlanteId("p-1"), "Piero")
        val q = unParlante(ParlanteId("p-2"), "Quinto")
        val parlanti = ParlanteRepositoryFinta().apply {
            salva(p).atteso()
            salva(q).atteso()
        }
        val attribuzioni = AttribuzioneRepositoryFinta()
        val primaConferma = Ambiente(parlanti = parlanti, attribuzioni = attribuzioni)
        primaConferma.servizio.esegui(
            ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.ParlanteEsistente(p.id)),
        ).atteso()

        val cambioConGuasto = Ambiente(
            parlanti = parlanti,
            attribuzioni = attribuzioni,
            estrattore = EstrattoreImprontaCheFallisce(),
        )
        assertFailsWith<GuastoEstrazioneDiProva> {
            cambioConGuasto.servizio.esegui(ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.ParlanteEsistente(q.id)))
        }

        assertEquals(p.id, attribuzioni.trova(VOCE_1)?.parlanteId, "l Attribuzione resta a P, invariata")
        val pOra = assertNotNull(parlanti.trova(p.id))
        assertEquals(listOf(VOCE_1), pOra.impronte.map { it.voceRef }, "l impronta di P resta invariata")
        val qOra = assertNotNull(parlanti.trova(q.id))
        assertEquals(emptyList(), qOra.impronte, "Q non riceve nulla")
        assertEquals(emptyList(), cambioConGuasto.eventi.pubblicati, "nulla viene pubblicato")
    }

    @Test
    fun `AC-84 il decodificatore riceve esattamente e solo gli intervalli della Voce confermata, in ordine`() {
        val p = unParlante(ParlanteId("p-1"), "Marco")
        val intervalliVoce2 = listOf(IntervalloMs(5_000, 6_000), IntervalloMs(9_000, 9_500))
        val decodificatore = DecodificatoreAudioCheRegistra()
        val ambiente = Ambiente(
            parlanti = ParlanteRepositoryFinta().apply { salva(p).atteso() },
            lettoreVoci = LettoreVociFinta(
                mapOf(REGISTRAZIONE to listOf(unaVoceVista(1), unaVoceVista(2, intervalliVoce2))),
            ),
            decodificatore = decodificatore,
        )

        ambiente.servizio.esegui(ConfermaAttribuzione(VOCE_2, ObiettivoAttribuzione.ParlanteEsistente(p.id))).atteso()

        assertEquals(
            listOf(REGISTRAZIONE to intervalliVoce2),
            decodificatore.chiamate,
            "solo gli intervalli della Voce 2 confermata, in ordine — mai l intera Registrazione ne quelli di Voce 1",
        )
    }

    @Test
    fun `AC-85 il nuovo Parlante viene salvato prima della sua Attribuzione (vincolo FK sqlite)`() {
        val parlanti = ParlanteRepositoryFinta()
        val attribuzioni = AttribuzioneRepositoryConVincoloFK(parlanti)
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(parlanti, attribuzioni))
        val servizio = ConfermaAttribuzioneServizio(
            eventi.unitaDiLavoro,
            GeneratoreIdFinto(),
            LettoreRegistrazioneFinta(mapOf(REGISTRAZIONE to unaRegistrazioneVista())),
            LettoreVociFinta(mapOf(REGISTRAZIONE to listOf(unaVoceVista(1)))),
            parlanti,
            attribuzioni,
            DecodificatoreAudioFinta(),
            EstrattoreImprontaFinta(),
            eventi,
        )

        servizio.esegui(ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.NuovoParlante("Giulia"))).atteso()

        assertEquals(ParlanteId("id-1"), attribuzioni.trova(VOCE_1)?.parlanteId)
        assertNotNull(parlanti.trova(ParlanteId("id-1")), "il Parlante e' stato salvato")
    }

    @Test
    fun `AC-87 riconfermare lo stesso Parlante non cambia nulla, non pubblica eventi e non ri-estrae l impronta`() {
        val p = unParlante(ParlanteId("p-1"), "Marco")
        val estrattore = EstrattoreImprontaCheConta()
        val ambiente = Ambiente(
            parlanti = ParlanteRepositoryFinta().apply { salva(p).atteso() },
            estrattore = estrattore,
        )
        ambiente.servizio.esegui(ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.ParlanteEsistente(p.id))).atteso()
        val improntaPrima = assertNotNull(ambiente.parlanti.trova(p.id)).impronte
        val pubblicatiPrima = ambiente.eventi.pubblicati

        ambiente.servizio.esegui(ConfermaAttribuzione(VOCE_1, ObiettivoAttribuzione.ParlanteEsistente(p.id))).atteso()

        assertEquals(improntaPrima, assertNotNull(ambiente.parlanti.trova(p.id)).impronte)
        assertEquals(pubblicatiPrima, ambiente.eventi.pubblicati, "nessun nuovo evento")
        assertEquals(1, estrattore.chiamate, "il riconferma non ri-estrae l'impronta")
    }

    /** Pre-check passes (`nomeAttivoInUso` = false) but `salva` refuses, like the ADR 0007 index would. */
    private class ParlanteRepositoryCheSegnalaLaGara : ParlanteRepository {
        override fun trova(id: ParlanteId): Parlante? = null

        override fun delProgetto(id: ProgettoId): List<Parlante> = emptyList()

        override fun nomeAttivoInUso(progettoId: ProgettoId, nome: Nome, escluso: ParlanteId?): Boolean = false

        override fun salva(p: Parlante): Esito<Unit> = Esito.Errore(ErroreParlanti.NomeGiaInUso(p.nome.valore))

        override fun rimuovi(id: ParlanteId) = Unit

        override fun impronteDiRegistrazione(id: RegistrazioneId): List<RigaImpronta> = emptyList()

        override fun impronteDelProgetto(id: ProgettoId): List<RigaImpronta> = emptyList()

        override fun aggiornaImpronta(attesa: RigaImpronta, impronta: Impronta, sorgente: String, modello: String) =
            false
    }

    /**
     * `nomeAttivoInUso` always signals the Nome taken; `salva` never checks it itself (unlike the real
     * index / [ParlanteRepositoryFinta]) — proves the SERVICE performs its own [INV-16] pre-check (ADR 0007).
     */
    private class ParlanteRepositoryCheNonSiAutoverifica : ParlanteRepository {
        var salvataggi: Int = 0
            private set

        override fun trova(id: ParlanteId): Parlante? = null

        override fun delProgetto(id: ProgettoId): List<Parlante> = emptyList()

        override fun nomeAttivoInUso(progettoId: ProgettoId, nome: Nome, escluso: ParlanteId?): Boolean = true

        override fun salva(p: Parlante): Esito<Unit> {
            salvataggi++
            return Esito.Ok(Unit)
        }

        override fun rimuovi(id: ParlanteId) = Unit

        override fun impronteDiRegistrazione(id: RegistrazioneId): List<RigaImpronta> = emptyList()

        override fun impronteDelProgetto(id: ProgettoId): List<RigaImpronta> = emptyList()

        override fun aggiornaImpronta(attesa: RigaImpronta, impronta: Impronta, sorgente: String, modello: String) =
            false
    }

    /** Records every call (Registrazione, intervalli) it is asked to decode, delegating for real samples. */
    private class DecodificatoreAudioCheRegistra(
        private val delegato: DecodificatoreAudio = DecodificatoreAudioFinta(),
    ) : DecodificatoreAudio {
        val chiamate: MutableList<Pair<RegistrazioneId, List<IntervalloMs>>> = mutableListOf()

        override fun campioni(id: RegistrazioneId, intervalli: List<IntervalloMs>): CampioniAudio {
            chiamate += id to intervalli
            return delegato.campioni(id, intervalli)
        }
    }

    /**
     * Simulates persistenza-schema's immediate FK `attribuzione.parlante_id REFERENCES parlante(id)`
     * (SQLite `foreign_keys=ON`): [salva] refuses when [parlanti] doesn't yet have that row — pins the
     * save order the service must follow (Parlante first, then Attribuzione).
     */
    private class AttribuzioneRepositoryConVincoloFK(
        private val parlanti: ParlanteRepository,
        private val delegato: AttribuzioneRepositoryFinta = AttribuzioneRepositoryFinta(),
    ) : AttribuzioneRepository, snastro.kernel.Ripristinabile {
        override fun trova(v: VoceRef) = delegato.trova(v)

        override fun diRegistrazione(id: RegistrazioneId) = delegato.diRegistrazione(id)

        override fun diParlante(id: ParlanteId) = delegato.diParlante(id)

        override fun salva(a: Attribuzione) {
            checkNotNull(parlanti.trova(a.parlanteId)) {
                "vincolo FK violato: parlante ${a.parlanteId} non ancora salvato " +
                    "(attribuzione.parlante_id REFERENCES parlante(id))"
            }
            delegato.salva(a)
        }

        override fun rimuovi(v: VoceRef) = delegato.rimuovi(v)

        override fun istantanea(): () -> Unit = delegato.istantanea()
    }

    /** A dedicated type (not a generic [RuntimeException]) so [assertFailsWith] can target it precisely. */
    private class GuastoEstrazioneDiProva(messaggio: String) : RuntimeException(messaggio)

    private class EstrattoreImprontaCheFallisce : EstrattoreImpronta {
        override val modello: String = "finto"

        override fun estrai(c: CampioniAudio): Impronta = throw GuastoEstrazioneDiProva("estrazione fallita")
    }

    private class EstrattoreImprontaCheConta(private val delegato: EstrattoreImpronta = EstrattoreImprontaFinta()) :
        EstrattoreImpronta {
        var chiamate: Int = 0
            private set

        override val modello: String get() = delegato.modello

        override fun estrai(c: CampioniAudio): Impronta {
            chiamate++
            return delegato.estrai(c)
        }
    }

    /**
     * Wires a [ConfermaAttribuzioneServizio] with the standard fakes, `eventi` correctly wrapping
     * [parlanti]/[attribuzioni] (so rollback and `pubblica` see the same transaction).
     */
    @Suppress("LongParameterList") // one parameter per collaborator, mirrors the servizio's own constructor
    private class Ambiente(
        val parlanti: ParlanteRepositoryFinta = ParlanteRepositoryFinta(),
        val attribuzioni: AttribuzioneRepositoryFinta = AttribuzioneRepositoryFinta(),
        registrazioni: LettoreRegistrazione = LettoreRegistrazioneFinta(
            mapOf(REGISTRAZIONE to unaRegistrazioneVista()),
        ),
        lettoreVoci: LettoreVoci = LettoreVociFinta(mapOf(REGISTRAZIONE to listOf(unaVoceVista(1)))),
        generatoreId: GeneratoreIdFinto = GeneratoreIdFinto(),
        // TODO(option-c follow-up): ML Finte WITHOUT the UnitaDiLavoroFinta (no in-transaction guard, AC-272)
        // because ConfermaAttribuzioneServizio still extracts inside its transaction.
        decodificatore: DecodificatoreAudio = DecodificatoreAudioFinta(),
        estrattore: EstrattoreImpronta = EstrattoreImprontaFinta(),
    ) {
        val eventi: DispatcherEventiFinta = DispatcherEventiFinta(UnitaDiLavoroFinta(parlanti, attribuzioni))
        val servizio: ConfermaAttribuzioneServizio = ConfermaAttribuzioneServizio(
            eventi.unitaDiLavoro,
            generatoreId,
            registrazioni,
            lettoreVoci,
            parlanti,
            attribuzioni,
            decodificatore,
            estrattore,
            eventi,
        )
    }

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        val VOCE_1 = VoceRef(REGISTRAZIONE, VoceId(1))
        val VOCE_2 = VoceRef(REGISTRAZIONE, VoceId(2))

        fun unaVoce(n: Int): VoceRef = VoceRef(REGISTRAZIONE, VoceId(n))

        fun unaRegistrazioneVista(
            progettoId: ProgettoId = PROGETTO,
            id: RegistrazioneId = REGISTRAZIONE,
        ): RegistrazioneVista = RegistrazioneVista(
            registrazioneId = id,
            progettoId = progettoId,
            titolo = "Seduta",
            riferimentoAudio = RiferimentoAudio("audio/${id.valore}.m4a"),
            dataRegistrazione = LocalDate.of(2026, 9, 20),
            durataMs = 3_600_000L,
        )

        fun unaVoceVista(n: Int, intervalli: List<IntervalloMs> = listOf(IntervalloMs(0, 1000))): VoceVista =
            VoceVista(unaVoce(n), intervalli)

        fun unParlante(
            id: ParlanteId,
            nome: String,
            tipo: TipoParlante = TipoParlante.RICORRENTE,
            progettoId: ProgettoId = PROGETTO,
        ): Parlante = Parlante.crea(id, progettoId, Nome.di(nome).atteso(), tipo).aggregato
    }
}
