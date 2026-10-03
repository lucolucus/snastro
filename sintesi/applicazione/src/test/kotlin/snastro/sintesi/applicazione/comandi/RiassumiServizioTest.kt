package snastro.sintesi.applicazione.comandi

import com.lemonappdev.konsist.api.Konsist
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.kernel.unIncontroDi
import snastro.sintesi.applicazione.eventi.RiassuntoRichiesto
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguistico
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguisticoFinta
import snastro.sintesi.applicazione.porte.LettoreIncontro
import snastro.sintesi.applicazione.porte.LettoreIncontroFinta
import snastro.sintesi.applicazione.porte.LettoreTrascritto
import snastro.sintesi.applicazione.porte.LettoreTrascrittoFinta
import snastro.sintesi.applicazione.porte.LunghezzaMassimaRiassuntoRepository
import snastro.sintesi.applicazione.porte.LunghezzaMassimaRiassuntoRepositoryFinta
import snastro.sintesi.applicazione.porte.MotivoDownload
import snastro.sintesi.applicazione.porte.ParteSintesi
import snastro.sintesi.applicazione.porte.RiassuntoRepository
import snastro.sintesi.applicazione.porte.RiassuntoRepositoryFinta
import snastro.sintesi.applicazione.porte.SegmentoSintesi
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico
import snastro.sintesi.applicazione.porte.StatoParteSintesi
import snastro.sintesi.applicazione.porte.conAvvio
import snastro.sintesi.applicazione.porte.conCompletamento
import snastro.sintesi.applicazione.porte.conFallimento
import snastro.sintesi.applicazione.porte.ogniIncontroConUnaParte
import snastro.sintesi.applicazione.porte.statoOsservabile
import snastro.sintesi.applicazione.porte.unRiassunto
import snastro.sintesi.applicazione.porte.unaStruttura
import snastro.sintesi.dominio.Argomento
import snastro.sintesi.dominio.BozzaElemento
import snastro.sintesi.dominio.BozzaRiassunto
import snastro.sintesi.dominio.ErroreSintesi
import snastro.sintesi.dominio.LimiteIngresso
import snastro.sintesi.dominio.LunghezzaMassimaParole
import snastro.sintesi.dominio.LunghezzaMassimaRiassunto
import snastro.sintesi.dominio.Riassunto
import snastro.sintesi.dominio.RiassuntoId
import snastro.sintesi.dominio.StatoRiassunto
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AC-S77..S82; INV-S2 (via `RiassuntoGiaAperto`), INV-S3, INV-S6, INV-S10. Each guard test flips exactly
 * one [Riassumibilita][snastro.sintesi.dominio.Riassumibilita] precondition (or the Argomento) off a base
 * scenario where everything else passes, per the fixed order and the What-to-do's "guards, then Argomento".
 */
class RiassumiServizioTest {
    @Test
    fun `AC-S77 crea un in_attesa con argomento cap e richiestoAlle, pubblica dopo commit`() {
        val a = unAmbiente()
        val cappata = LunghezzaMassimaRiassunto.predefinita(PROGETTO).also { it.modifica(1500).atteso() }
        a.lunghezze.salva(cappata).atteso()

        val id = a.servizio.esegui(Riassumi(unIncontroDi(REGISTRAZIONE), argomento = "budget")).atteso()

        val salvato = checkNotNull(a.riassunti.trova(id))
        assertEquals(RiassuntoId("id-1"), id)
        assertEquals(Argomento.di("budget").atteso(), salvato.argomento)
        assertEquals(LunghezzaMassimaParole.di(1500).atteso(), salvato.lunghezzaMassima)
        assertEquals(CLOCK.instant(), salvato.richiestoAlle)
        assertEquals(StatoRiassunto.IN_ATTESA, salvato.stato)
        assertEquals(listOf(id), a.riassunti.trova(unIncontroDi(REGISTRAZIONE)).map { it.id })
        assertEquals(listOf(RiassuntoRichiesto(unIncontroDi(REGISTRAZIONE))), a.eventi.pubblicati)
    }

    @Test
    fun `AC-S78 modello non installato, in download o con download fallito rifiuta con ModelloNonInstallato`() {
        listOf(
            StatoModelloLinguistico.NonInstallato(1),
            StatoModelloLinguistico.InDownload(1, 2),
            StatoModelloLinguistico.DownloadFallito(MotivoDownload.ConnessioneInterrotta),
        ).forEach { stato ->
            val a = unAmbiente(disponibilita = DisponibilitaModelloLinguisticoFinta(stato))

            a.servizio.esegui(Riassumi(unIncontroDi(REGISTRAZIONE))).erroreAtteso<ErroreSintesi.ModelloNonInstallato>()

            assertEquals(emptyList(), a.riassunti.trova(unIncontroDi(REGISTRAZIONE)), "$stato")
            assertEquals(emptyList(), a.eventi.pubblicati, "$stato")
        }
    }

    @Test
    fun `AC-S78 senza Trascritto rifiuta con PartiNonTrascritte e non scrive`() {
        val a = unAmbiente(trascritti = LettoreTrascrittoFinta())

        a.servizio.esegui(Riassumi(unIncontroDi(REGISTRAZIONE))).erroreAtteso<ErroreSintesi.PartiNonTrascritte>()

        assertEquals(emptyList(), a.riassunti.trova(unIncontroDi(REGISTRAZIONE)))
        assertEquals(emptyList(), a.eventi.pubblicati)
    }

    @Test
    fun `AC-S78 una Elaborazione aperta rifiuta con ElaborazioneGiaAperta e non scrive`() {
        val trascritti = LettoreTrascrittoFinta(
            mapOf(REGISTRAZIONE to listOf(unSegmentoSintesi())),
            aperte = setOf(REGISTRAZIONE),
        )
        val a = unAmbiente(trascritti = trascritti)

        a.servizio.esegui(Riassumi(unIncontroDi(REGISTRAZIONE))).erroreAtteso<ErroreSintesi.ElaborazioneGiaAperta>()

        assertEquals(emptyList(), a.riassunti.trova(unIncontroDi(REGISTRAZIONE)))
        assertEquals(emptyList(), a.eventi.pubblicati)
    }

    @Test
    fun `AC-S78 un Riassunto gia aperto rifiuta con RiassuntoGiaAperto e non scrive altro`() {
        listOf(
            unRiassunto("aperto-1", REGISTRAZIONE),
            unRiassunto("aperto-2", REGISTRAZIONE).conAvvio(),
        ).forEach { aperto ->
            val a = unAmbiente()
            a.riassunti.salva(aperto).atteso()

            a.servizio.esegui(Riassumi(unIncontroDi(REGISTRAZIONE))).erroreAtteso<ErroreSintesi.RiassuntoGiaAperto>()

            val righe = a.riassunti.trova(unIncontroDi(REGISTRAZIONE)).map { it.id }
            assertEquals(listOf(aperto.id), righe, "${aperto.stato}")
            assertEquals(emptyList(), a.eventi.pubblicati, "${aperto.stato}")
        }
    }

    @Test
    fun `AC-S78 un ingresso stimato oltre il limite rifiuta con IngressoTroppoLungo`() {
        val testoLungo = "a".repeat(LimiteIngresso.LIMITE_TOKEN * 3)
        val trascritti = LettoreTrascrittoFinta(
            mapOf(REGISTRAZIONE to listOf(unSegmentoSintesi(testo = testoLungo))),
        )
        val a = unAmbiente(trascritti = trascritti)

        a.servizio.esegui(Riassumi(unIncontroDi(REGISTRAZIONE))).erroreAtteso<ErroreSintesi.IngressoTroppoLungo>()

        assertEquals(emptyList(), a.riassunti.trova(unIncontroDi(REGISTRAZIONE)))
        assertEquals(emptyList(), a.eventi.pubblicati)
    }

    @Test
    fun `AC-S78 un argomento di 201 caratteri rifiuta con ArgomentoTroppoLungo dopo le guardie`() {
        val a = unAmbiente()

        val comando = Riassumi(unIncontroDi(REGISTRAZIONE), argomento = "x".repeat(201))
        val errore = a.servizio.esegui(comando).erroreAtteso<ErroreSintesi.ArgomentoTroppoLungo>()

        assertEquals(201, errore.lunghezza)
        assertEquals(emptyList(), a.riassunti.trova(unIncontroDi(REGISTRAZIONE)))
        assertEquals(emptyList(), a.eventi.pubblicati)
    }

    @Test
    fun `INV-S3 con un pronto e un fallito, Riassumi rimuove il fallito e lascia il pronto identico`() {
        val a = unAmbiente()
        val pronto = unRiassunto("pronto-1", REGISTRAZIONE).conAvvio().conCompletamento(
            BozzaRiassunto(
                sommario = null,
                decisioni = listOf(BozzaElemento("Decisione gia pronta.", listOf(1), null)),
                questioniAperte = emptyList(),
                azioni = emptyList(),
                puntiChiave = emptyList(),
            ),
            unaStruttura(1 to 1),
        )
        a.riassunti.salva(pronto).atteso()
        val fallito = unRiassunto("fallito-1", REGISTRAZIONE).conAvvio().conFallimento()
        a.riassunti.salva(fallito).atteso()
        val prontoPrima = pronto.statoOsservabile()

        a.servizio.esegui(Riassumi(unIncontroDi(REGISTRAZIONE))).atteso()

        val righe = a.riassunti.trova(unIncontroDi(REGISTRAZIONE))
        assertEquals(setOf(StatoRiassunto.PRONTO, StatoRiassunto.IN_ATTESA), righe.map { it.stato }.toSet())
        assertEquals(prontoPrima, checkNotNull(righe.single { it.pronto }).statoOsservabile())
        assertNull(a.riassunti.trova(RiassuntoId("fallito-1")))
    }

    @Test
    fun `AC-S79 un argomento vuoto e assente, non eredita quello del Riassunto precedente`() {
        val a = unAmbiente()
        val precedente = unRiassunto("precedente", REGISTRAZIONE, argomento = "vecchio argomento")
            .conAvvio()
            .conFallimento()
        a.riassunti.salva(precedente).atteso()

        val id = a.servizio.esegui(Riassumi(unIncontroDi(REGISTRAZIONE), argomento = "   ")).atteso()

        assertNull(checkNotNull(a.riassunti.trova(id)).argomento)
    }

    @Test
    fun `AC-S80 le guardie sono lette con la transazione della UnitaDiLavoro aperta`() {
        val riassuntiReali = RiassuntoRepositoryFinta()
        val lunghezzeReali = LunghezzaMassimaRiassuntoRepositoryFinta()
        val transazione = UnitaDiLavoroFinta(riassuntiReali, lunghezzeReali)
        val eventi = DispatcherEventiFinta(transazione)
        val trascrittiSpia = LettoreTrascrittoSpia(
            LettoreTrascrittoFinta(mapOf(REGISTRAZIONE to listOf(unSegmentoSintesi()))),
            transazione,
        )
        val riassuntiSpia = RiassuntoRepositorySpia(riassuntiReali, transazione)
        val lunghezzeSpia = LunghezzaMassimaRiassuntoRepositorySpia(lunghezzeReali, transazione)
        val servizio = RiassumiServizio(
            eventi.unitaDiLavoro,
            GeneratoreIdFinto(),
            CLOCK,
            PROGETTO,
            riassuntiSpia,
            lunghezzeSpia,
            trascrittiSpia,
            ogniIncontroConUnaParte(),
            DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.Installato),
            eventi,
        )

        servizio.esegui(Riassumi(unIncontroDi(REGISTRAZIONE))).atteso()

        assertTrue(trascrittiSpia.letture.isNotEmpty())
        assertTrue(trascrittiSpia.letture.all { it }, "${trascrittiSpia.letture}")
        assertTrue(riassuntiSpia.letture.isNotEmpty())
        assertTrue(riassuntiSpia.letture.all { it }, "${riassuntiSpia.letture}")
        assertTrue(lunghezzeSpia.letture.isNotEmpty())
        assertTrue(lunghezzeSpia.letture.all { it }, "${lunghezzeSpia.letture}")
    }

    @Test
    fun `AC-S81 backstop, salva risponde RiassuntoGiaAperto, rollback e niente pubblicato, il fallito rimosso torna`() {
        val riassuntiReali = RiassuntoRepositoryFinta()
        val lunghezze = LunghezzaMassimaRiassuntoRepositoryFinta()
        val fallito = unRiassunto("fallito-1", REGISTRAZIONE).conAvvio().conFallimento()
        riassuntiReali.salva(fallito).atteso()
        val fallitoPrima = fallito.statoOsservabile()
        val riassuntiGuasti = object : RiassuntoRepository by riassuntiReali {
            override fun salva(r: Riassunto): Esito<Unit> =
                Esito.Errore(ErroreSintesi.RiassuntoGiaAperto(unIncontroDi(REGISTRAZIONE)))
        }
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(riassuntiReali, lunghezze))
        val servizio = RiassumiServizio(
            eventi.unitaDiLavoro,
            GeneratoreIdFinto(),
            CLOCK,
            PROGETTO,
            riassuntiGuasti,
            lunghezze,
            LettoreTrascrittoFinta(mapOf(REGISTRAZIONE to listOf(unSegmentoSintesi()))),
            ogniIncontroConUnaParte(),
            DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.Installato),
            eventi,
        )

        servizio.esegui(Riassumi(unIncontroDi(REGISTRAZIONE))).erroreAtteso<ErroreSintesi.RiassuntoGiaAperto>()

        // Il fallito era gia' stato rimosso da INV-S3 (crea()) prima che salva() fallisse: il rollback della
        // transazione (non solo di rimuovi, gia' provato da INV-S3 sotto) deve restituirlo intatto.
        val righe = riassuntiReali.trova(unIncontroDi(REGISTRAZIONE))
        assertEquals(listOf(fallito.id), righe.map { it.id })
        assertEquals(fallitoPrima, righe.single().statoOsservabile())
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `INV-S3 un Errore di rimuovi sul fallito precedente si propaga, rollback e niente pubblicato`() {
        val riassuntiReali = RiassuntoRepositoryFinta()
        val lunghezze = LunghezzaMassimaRiassuntoRepositoryFinta()
        val fallito = unRiassunto("fallito-1", REGISTRAZIONE).conAvvio().conFallimento()
        riassuntiReali.salva(fallito).atteso()
        val riassuntiGuasti = object : RiassuntoRepository by riassuntiReali {
            override fun rimuovi(id: RiassuntoId): Esito<Unit> = Esito.Errore(ErroreDiProva.Fallito("rimuovi"))
        }
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(riassuntiReali, lunghezze))
        val servizio = RiassumiServizio(
            eventi.unitaDiLavoro,
            GeneratoreIdFinto(),
            CLOCK,
            PROGETTO,
            riassuntiGuasti,
            lunghezze,
            LettoreTrascrittoFinta(mapOf(REGISTRAZIONE to listOf(unSegmentoSintesi()))),
            ogniIncontroConUnaParte(),
            DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.Installato),
            eventi,
        )

        val esito = servizio.esegui(Riassumi(unIncontroDi(REGISTRAZIONE)))

        assertEquals(ErroreDiProva.Fallito("rimuovi"), esito.erroreAtteso<ErroreDiProva.Fallito>())
        assertEquals(listOf(fallito.id), riassuntiReali.trova(unIncontroDi(REGISTRAZIONE)).map { it.id })
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-S82 il costruttore non ha un collaboratore verso i modelli`() {
        // DisponibilitaModelloLinguistico ha per unico membro stato(): non c'e' altra chiamata che il
        // servizio potrebbe fare verso :modelli attraverso questa porta (nessuna Finta puo' registrare altro).
        val scope = Konsist.scopeFromPackage(PACCHETTO, "sintesi/applicazione", "main")
        val costruttore = scope.classes().single { it.name == "RiassumiServizio" }.primaryConstructor

        val tipi = checkNotNull(costruttore) { "RiassumiServizio" }.parameters.map { it.type.text }

        assertEquals(
            listOf(
                "UnitaDiLavoro", "GeneratoreId", "Clock", "ProgettoId", "RiassuntoRepository",
                "LunghezzaMassimaRiassuntoRepository", "LettoreTrascritto", "LettoreIncontro",
                "DisponibilitaModelloLinguistico",
                "DispatcherEventi",
            ),
            tipi,
        )

        // e la porta e' effettivamente usata: la guardia INV-S6 la legge davvero.
        val a = unAmbiente()
        a.servizio.esegui(Riassumi(unIncontroDi(REGISTRAZIONE))).atteso()
    }

    @Test
    fun `Incontro di 2 Parti TRASCRITTE e modello installato crea un in_attesa, pubblicato dopo commit`() {
        val a = unAmbienteMultiParte(trascritte = setOf(REG1, REG2))

        val id = a.servizio.esegui(Riassumi(INCONTRO)).atteso()

        assertEquals(listOf(id), a.riassunti.trova(INCONTRO).map { it.id })
        assertEquals(StatoRiassunto.IN_ATTESA, checkNotNull(a.riassunti.trova(id)).stato)
        assertEquals(listOf(RiassuntoRichiesto(INCONTRO)), a.eventi.pubblicati)
    }

    @Test
    fun `Incontro di 2 Parti, Parte 2 DA_TRASCRIVERE rifiuta con PartiNonTrascritte(2) e non scrive`() {
        val a = unAmbienteMultiParte(trascritte = setOf(REG1))

        val errore = a.servizio.esegui(Riassumi(INCONTRO)).erroreAtteso<ErroreSintesi.PartiNonTrascritte>()

        assertEquals(2, errore.parte)
        assertEquals(emptyList(), a.riassunti.trova(INCONTRO))
        assertEquals(emptyList(), a.eventi.pubblicati)
    }

    @Test
    fun `Incontro di 2 Parti, Parte 1 IN_TRASCRIZIONE rifiuta con ElaborazioneGiaAperta(1) prima della 2`() {
        val a = unAmbienteMultiParte(trascritte = setOf(REG1), aperte = setOf(REG1))

        val errore = a.servizio.esegui(Riassumi(INCONTRO)).erroreAtteso<ErroreSintesi.ElaborazioneGiaAperta>()

        assertEquals(1, errore.parte)
        assertEquals(emptyList(), a.riassunti.trova(INCONTRO))
        assertEquals(emptyList(), a.eventi.pubblicati)
    }

    @Test
    fun `L248 Parte 2 TRASCRITTA ma senza Segmenti alla lettura rifiuta con ElaborazioneGiaAperta(2) e non scrive`() {
        // the race: the state read says TRASCRITTA, the Segmenti read right after finds no Trascritto
        val finta = LettoreTrascrittoFinta(listOf(REG1, REG2).associateWith { listOf(unSegmentoSintesi()) })
        val inCorsa = object : LettoreTrascritto by finta {
            override fun segmenti(r: RegistrazioneId): List<SegmentoSintesi>? =
                if (r == REG2) null else finta.segmenti(r)
        }
        val a = unAmbiente(trascritti = inCorsa, incontri = LettoreIncontroFinta(mapOf(INCONTRO to listOf(REG1, REG2))))

        val errore = a.servizio.esegui(Riassumi(INCONTRO)).erroreAtteso<ErroreSintesi.ElaborazioneGiaAperta>()

        assertEquals(2, errore.parte)
        assertEquals(emptyList(), a.riassunti.trova(INCONTRO))
        assertEquals(emptyList(), a.eventi.pubblicati)
    }

    @Test
    fun `Incontro di 2 Parti, un Riassunto gia aperto rifiuta con RiassuntoGiaAperto dell Incontro`() {
        val a = unAmbienteMultiParte(trascritte = setOf(REG1, REG2))
        val aperto = unRiassunto("aperto-1", INCONTRO)
        a.riassunti.salva(aperto).atteso()

        val errore = a.servizio.esegui(Riassumi(INCONTRO)).erroreAtteso<ErroreSintesi.RiassuntoGiaAperto>()

        // the guard's value equals the repository backstop's (RiassuntoRepositoryContratto AC-S66)
        assertEquals(ErroreSintesi.RiassuntoGiaAperto(INCONTRO), errore)

        assertEquals(listOf(aperto.id), a.riassunti.trova(INCONTRO).map { it.id })
        assertEquals(emptyList(), a.eventi.pubblicati)
    }

    @Test
    fun `Incontro di 2 Parti, l'ingresso concatenato oltre il limite rifiuta con IngressoTroppoLungo`() {
        // each Parte alone fits LimiteIngresso, together they do not (the scope is the whole Incontro).
        val meta = "a".repeat(LimiteIngresso.LIMITE_TOKEN * 2)
        val a = unAmbienteMultiParte(
            trascritte = setOf(REG1, REG2),
            segmenti = mapOf(
                REG1 to listOf(unSegmentoSintesi(testo = meta)),
                REG2 to listOf(unSegmentoSintesi(testo = meta)),
            ),
        )

        a.servizio.esegui(Riassumi(INCONTRO)).erroreAtteso<ErroreSintesi.IngressoTroppoLungo>()

        assertEquals(emptyList(), a.riassunti.trova(INCONTRO))
        assertEquals(emptyList(), a.eventi.pubblicati)
    }

    @Test
    fun `Incontro di 2 Parti, Parte 2 NON_RIUSCITA rifiuta con PartiFallite(2) e non scrive`() {
        val a = unAmbienteMultiParte(trascritte = setOf(REG1), fallite = setOf(REG2))

        val errore = a.servizio.esegui(Riassumi(INCONTRO)).erroreAtteso<ErroreSintesi.PartiFallite>()

        assertEquals(2, errore.parte)
        assertEquals(emptyList(), a.riassunti.trova(INCONTRO))
        assertEquals(emptyList(), a.eventi.pubblicati)
    }

    @Test
    fun `Incontro sconosciuto rifiuta con IncontroNonTrovato prima di ModelloNonInstallato e non scrive`() {
        val a = unAmbienteMultiParte(
            trascritte = setOf(REG1, REG2),
            disponibilita = DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.NonInstallato(1)),
        )
        val ignoto = IncontroId("ignoto")

        val errore = a.servizio.esegui(Riassumi(ignoto)).erroreAtteso<ErroreSintesi.IncontroNonTrovato>()

        assertEquals(ignoto, errore.incontroId)
        assertEquals(emptyList(), a.riassunti.trova(ignoto))
        assertEquals(emptyList(), a.riassunti.trova(INCONTRO))
        assertEquals(emptyList(), a.eventi.pubblicati)
    }

    @Test
    fun `Incontro letto senza Parti rifiuta con IncontroNonTrovato, mai un crash`() {
        val senzaParti = object : LettoreIncontro {
            override fun parti(incontroId: IncontroId): List<ParteSintesi> = emptyList()
        }
        val a = unAmbiente(incontri = senzaParti)

        a.servizio.esegui(Riassumi(INCONTRO)).erroreAtteso<ErroreSintesi.IncontroNonTrovato>()

        assertEquals(emptyList(), a.riassunti.trova(INCONTRO))
        assertEquals(emptyList(), a.eventi.pubblicati)
    }

    @Test
    fun `l ingresso per la stima non e costruito quando un altra guardia ha gia deciso`() {
        val segmenti = mapOf(REG1 to listOf(unSegmentoSintesi()), REG2 to listOf(unSegmentoSintesi()))
        val casi = listOf(
            "modello non installato" to unAmbienteMultiParte(
                trascritte = setOf(REG1, REG2),
                disponibilita = DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.NonInstallato(1)),
                letture = LettureContate(),
            ),
            "Parte 2 in trascrizione" to unAmbienteMultiParte(
                trascritte = setOf(REG1, REG2),
                aperte = setOf(REG2),
                segmenti = segmenti,
                letture = LettureContate(),
            ),
        )
        casi.forEach { (nome, a) ->
            assertTrue(a.servizio.esegui(Riassumi(INCONTRO)) is Esito.Errore, nome)
            assertEquals(0, checkNotNull(a.letture).segmenti, nome)
        }

        val aperto = unAmbienteMultiParte(trascritte = setOf(REG1, REG2), letture = LettureContate())
        aperto.servizio.esegui(Riassumi(INCONTRO)).atteso()
        val letturePrima = checkNotNull(aperto.letture).segmenti
        aperto.servizio.esegui(Riassumi(INCONTRO)).erroreAtteso<ErroreSintesi.RiassuntoGiaAperto>()
        assertEquals(letturePrima, checkNotNull(aperto.letture).segmenti, "Riassunto aperto: l ingresso non e letto")

        val ok = unAmbienteMultiParte(trascritte = setOf(REG1, REG2), letture = LettureContate())
        ok.servizio.esegui(Riassumi(INCONTRO)).atteso()
        assertEquals(2, checkNotNull(ok.letture).segmenti, "con le guardie superate la stima legge ogni Parte")
    }

    @Test
    fun `Incontro sconosciuto rifiuta con IncontroNonTrovato e non scrive`() {
        val a = unAmbienteMultiParte(trascritte = setOf(REG1, REG2))
        val ignoto = IncontroId("ignoto")

        val errore = a.servizio.esegui(Riassumi(ignoto)).erroreAtteso<ErroreSintesi.IncontroNonTrovato>()

        assertEquals(ignoto, errore.incontroId)
        assertEquals(emptyList(), a.riassunti.trova(ignoto))
        assertEquals(emptyList(), a.eventi.pubblicati)
    }

    @Suppress("LongParameterList") // one knob per port state of the scenario
    private fun unAmbienteMultiParte(
        trascritte: Set<RegistrazioneId>,
        aperte: Set<RegistrazioneId> = emptySet(),
        fallite: Set<RegistrazioneId> = emptySet(),
        segmenti: Map<RegistrazioneId, List<SegmentoSintesi>> =
            trascritte.associateWith { listOf(unSegmentoSintesi()) },
        disponibilita: DisponibilitaModelloLinguistico =
            DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.Installato),
        letture: LettureContate? = null,
    ): Ambiente {
        val riassunti = RiassuntoRepositoryFinta()
        val lunghezze = LunghezzaMassimaRiassuntoRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(riassunti, lunghezze))
        val finta = LettoreTrascrittoFinta(segmenti.filterKeys { it in trascritte }, aperte, fallite)
        val servizio = RiassumiServizio(
            eventi.unitaDiLavoro, GeneratoreIdFinto(), CLOCK, PROGETTO,
            riassunti, lunghezze,
            letture?.su(finta) ?: finta,
            LettoreIncontroFinta(mapOf(INCONTRO to listOf(REG1, REG2))),
            disponibilita,
            eventi,
        )
        return Ambiente(servizio, riassunti, lunghezze, eventi, letture)
    }

    /** Counts the full Segmenti reads (the input the estimate builds), L137. */
    private class LettureContate {
        var segmenti = 0

        fun su(delega: LettoreTrascritto): LettoreTrascritto = object : LettoreTrascritto by delega {
            override fun segmenti(r: RegistrazioneId): List<SegmentoSintesi>? {
                segmenti++
                return delega.segmenti(r)
            }
        }
    }

    private class LettoreTrascrittoSpia(
        private val delega: LettoreTrascritto,
        private val transazione: UnitaDiLavoroFinta,
    ) : LettoreTrascritto {
        val letture = mutableListOf<Boolean>()

        override fun segmenti(r: RegistrazioneId): List<SegmentoSintesi>? {
            letture += transazione.transazioneAperta
            return delega.segmenti(r)
        }

        override fun statoParte(r: RegistrazioneId): StatoParteSintesi {
            letture += transazione.transazioneAperta
            return delega.statoParte(r)
        }
    }

    private class RiassuntoRepositorySpia(
        private val delega: RiassuntoRepository,
        private val transazione: UnitaDiLavoroFinta,
    ) : RiassuntoRepository by delega {
        val letture = mutableListOf<Boolean>()

        override fun trova(incontroId: IncontroId): List<Riassunto> {
            letture += transazione.transazioneAperta
            return delega.trova(incontroId)
        }
    }

    /** AC-S80: proves the cap guard's own read ([crea]'s [LunghezzaMassimaRiassuntoRepository.trova]) also runs
     * with the transaction open — the two Spia above only cover [LettoreTrascritto] and [RiassuntoRepository]. */
    private class LunghezzaMassimaRiassuntoRepositorySpia(
        private val delega: LunghezzaMassimaRiassuntoRepository,
        private val transazione: UnitaDiLavoroFinta,
    ) : LunghezzaMassimaRiassuntoRepository by delega {
        val letture = mutableListOf<Boolean>()

        override fun trova(p: ProgettoId): LunghezzaMassimaRiassunto {
            letture += transazione.transazioneAperta
            return delega.trova(p)
        }
    }

    private data class Ambiente(
        val servizio: RiassumiServizio,
        val riassunti: RiassuntoRepositoryFinta,
        val lunghezze: LunghezzaMassimaRiassuntoRepositoryFinta,
        val eventi: DispatcherEventiFinta,
        val letture: LettureContate? = null,
    )

    /** A fresh set of fakes wired into a [RiassumiServizio]; every guard passes unless overridden here. */
    private fun unAmbiente(
        trascritti: LettoreTrascritto = LettoreTrascrittoFinta(mapOf(REGISTRAZIONE to listOf(unSegmentoSintesi()))),
        disponibilita: DisponibilitaModelloLinguistico =
            DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.Installato),
        incontri: LettoreIncontro = ogniIncontroConUnaParte(),
    ): Ambiente {
        val riassunti = RiassuntoRepositoryFinta()
        val lunghezze = LunghezzaMassimaRiassuntoRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(riassunti, lunghezze))
        val servizio = RiassumiServizio(
            eventi.unitaDiLavoro, GeneratoreIdFinto(), CLOCK, PROGETTO,
            riassunti, lunghezze, trascritti, incontri, disponibilita, eventi,
        )
        return Ambiente(servizio, riassunti, lunghezze, eventi)
    }

    private fun unSegmentoSintesi(
        segmentoId: Int = 1,
        voceId: Int = 1,
        inizioMs: Long = 0,
        fineMs: Long = 1_000,
        testo: String = "Testo di prova.",
    ): SegmentoSintesi {
        val intervallo = IntervalloMs(inizioMs, fineMs)
        return SegmentoSintesi(SegmentoId(segmentoId), VoceId(voceId), intervallo, testo)
    }

    private companion object {
        const val PACCHETTO = "snastro.sintesi.applicazione.comandi"
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        val REG1 = RegistrazioneId("r1")
        val REG2 = RegistrazioneId("r2")
        val INCONTRO = IncontroId("incontro-multi")
        val PROGETTO = ProgettoId("progetto-1")
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC)
    }
}
