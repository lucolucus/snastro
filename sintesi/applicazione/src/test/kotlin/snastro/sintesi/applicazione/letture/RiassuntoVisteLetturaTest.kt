package snastro.sintesi.applicazione.letture

import com.lemonappdev.konsist.api.Konsist
import snastro.kernel.Esito
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguistico
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguisticoFinta
import snastro.sintesi.applicazione.porte.LettoreNomi
import snastro.sintesi.applicazione.porte.LettoreNomiFinta
import snastro.sintesi.applicazione.porte.LettoreTrascritto
import snastro.sintesi.applicazione.porte.LettoreTrascrittoFinta
import snastro.sintesi.applicazione.porte.MotivoDownload
import snastro.sintesi.applicazione.porte.RiassuntoRepository
import snastro.sintesi.applicazione.porte.RiassuntoRepositoryFinta
import snastro.sintesi.applicazione.porte.SegmentoSintesi
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico
import snastro.sintesi.applicazione.porte.conAvvio
import snastro.sintesi.applicazione.porte.conCompletamento
import snastro.sintesi.applicazione.porte.conFallimento
import snastro.sintesi.applicazione.porte.unRiassunto
import snastro.sintesi.applicazione.porte.unaStruttura
import snastro.sintesi.dominio.BozzaElemento
import snastro.sintesi.dominio.BozzaRiassunto
import snastro.sintesi.dominio.IngressoRiassunto
import snastro.sintesi.dominio.LimiteIngresso
import snastro.sintesi.dominio.MotivoFallimento
import snastro.sintesi.dominio.Riassunto
import snastro.sintesi.dominio.RiassuntoId
import snastro.sintesi.dominio.SegmentoIngresso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AC-S102..S108: the `vista-riassunto` read-model. Every scenario reads through the repository fakes
 * (never `ricostituisci` directly, CR-15) so a pronto Riassunto is reconstructed exactly as the real
 * adapter would return it.
 */
class RiassuntoVisteLetturaTest {
    @Test
    fun `AC-S102 senza Trascritto restituisce null`() {
        val a = unAmbiente(trascritti = LettoreTrascrittoFinta())

        assertNull(a.lettura.di(REGISTRAZIONE))
    }

    @Test
    fun `AC-S102 con Trascritto e senza Riassunto, mostrato e richiestaAperta sono null e disponibilita Disponibile`() {
        val a = unAmbiente()

        val vista = checkNotNull(a.lettura.di(REGISTRAZIONE))

        assertNull(vista.mostrato)
        assertNull(vista.richiestaAperta)
        assertNull(vista.ultimoFallimento)
        assertNull(vista.argomentoPrecompilato)
        assertEquals(DisponibilitaVista.Disponibile, vista.disponibilita)
    }

    @Test
    fun `AC-S103 il sommario risolve V1 al nome corrente e V3 a Voce 3 non attribuita`() {
        val segmenti = listOf(
            unSegmentoSintesi(segmentoId = 1, voceId = 1, inizioMs = 0, testo = "Apertura."),
            unSegmentoSintesi(segmentoId = 2, voceId = 2, inizioMs = 1_000, testo = "Intervento."),
            unSegmentoSintesi(segmentoId = 3, voceId = 3, inizioMs = 2_000, testo = "Chiusura."),
        )
        val bozza = BozzaRiassunto(
            sommario = "{V1} apre, {V3} chiude",
            decisioni = listOf(BozzaElemento("Una decisione.", listOf(1), null)),
            questioniAperte = emptyList(),
            azioni = emptyList(),
            puntiChiave = emptyList(),
        )
        val struttura = unaStruttura(1 to 1, 2 to 2, 3 to 3)
        val pronto = unRiassunto("r-1", REGISTRAZIONE).conAvvio().conCompletamento(bozza, struttura)
        val a = unAmbiente(
            trascritti = LettoreTrascrittoFinta(mapOf(REGISTRAZIONE to segmenti)),
            nomi = LettoreNomiFinta(
                attribuzioni = mapOf(VoceRef(REGISTRAZIONE, VoceId(1)) to "parlante-1"),
                nomiParlanti = mapOf("parlante-1" to "Marco"),
            ),
        )
        a.riassunti.salva(pronto).atteso()

        val sommario = checkNotNull(checkNotNull(a.lettura.di(REGISTRAZIONE)).mostrato).sommario

        assertEquals(
            listOf(
                ParteTestoVista.Voce(VoceVista(1, "Voce 1", "Marco")),
                ParteTestoVista.Testo(" apre, "),
                ParteTestoVista.Voce(VoceVista(3, "Voce 3", null)),
                ParteTestoVista.Testo(" chiude"),
            ),
            sommario,
        )
    }

    @Test
    fun `AC-S103 dopo una RinominaParlante la lettura successiva mostra il nuovo nome, la riga non cambia`() {
        val segmenti = listOf(unSegmentoSintesi(segmentoId = 1, voceId = 1))
        val bozza = BozzaRiassunto(
            sommario = "{V1} apre.",
            decisioni = listOf(BozzaElemento("Una decisione.", listOf(1), null)),
            questioniAperte = emptyList(),
            azioni = emptyList(),
            puntiChiave = emptyList(),
        )
        val pronto = unRiassunto("r-1", REGISTRAZIONE).conAvvio().conCompletamento(bozza, unaStruttura(1 to 1))
        val riassunti = RiassuntoRepositoryFinta()
        riassunti.salva(pronto).atteso()
        val trascritti = LettoreTrascrittoFinta(mapOf(REGISTRAZIONE to segmenti))
        val nomi = LettoreNomiFinta(
            attribuzioni = mapOf(VoceRef(REGISTRAZIONE, VoceId(1)) to "parlante-1"),
            nomiParlanti = mapOf("parlante-1" to "Marco"),
        )
        val lettura = RiassuntoVisteLettura(UnitaDiLavoroFinta(), riassunti, trascritti, nomi, unModelloInstallato())
        val primaDellaRinomina = riassunti.trova(RiassuntoId("r-1"))

        val primaVista = checkNotNull(checkNotNull(lettura.di(REGISTRAZIONE)).mostrato).sommario
        val nomiRinominati = LettoreNomiFinta(
            attribuzioni = mapOf(VoceRef(REGISTRAZIONE, VoceId(1)) to "parlante-1"),
            nomiParlanti = mapOf("parlante-1" to "Marchetto"),
        )
        val letturaRinominata =
            RiassuntoVisteLettura(UnitaDiLavoroFinta(), riassunti, trascritti, nomiRinominati, unModelloInstallato())
        val dopoVista = checkNotNull(checkNotNull(letturaRinominata.di(REGISTRAZIONE)).mostrato).sommario

        assertEquals(
            listOf(ParteTestoVista.Voce(VoceVista(1, "Voce 1", "Marco")), ParteTestoVista.Testo(" apre.")),
            primaVista,
        )
        assertEquals(
            listOf(ParteTestoVista.Voce(VoceVista(1, "Voce 1", "Marchetto")), ParteTestoVista.Testo(" apre.")),
            dopoVista,
        )
        assertEquals(primaDellaRinomina?.statoRigaPerTest(), riassunti.trova(RiassuntoId("r-1"))?.statoRigaPerTest())
    }

    @Test
    fun `AC-S104 le Fonti sono ordinate per inizioMs, ognuna con il voceId corrente, non tutte sulla stessa Voce`() {
        val segmenti = listOf(
            unSegmentoSintesi(segmentoId = 1, voceId = 3, inizioMs = 5_000),
            unSegmentoSintesi(segmentoId = 2, voceId = 1, inizioMs = 1_000),
            unSegmentoSintesi(segmentoId = 3, voceId = 2, inizioMs = 3_000),
        )
        val bozza = BozzaRiassunto(
            sommario = null,
            decisioni = listOf(
                BozzaElemento("Prima decisione.", listOf(1, 2, 3), null),
                BozzaElemento("Seconda decisione.", listOf(2), null),
            ),
            questioniAperte = emptyList(),
            azioni = listOf(BozzaElemento("Un'azione fuori struttura.", listOf(99), null)), // fonte invalida: omessa
            puntiChiave = emptyList(),
        )
        val struttura = unaStruttura(1 to 3, 2 to 1, 3 to 2)
        val pronto = unRiassunto("r-1", REGISTRAZIONE, argomento = "budget", parole = 1_500).conAvvio()
            .conCompletamento(bozza, struttura)
        val a = unAmbiente(trascritti = LettoreTrascrittoFinta(mapOf(REGISTRAZIONE to segmenti)))
        a.riassunti.salva(pronto).atteso()

        val mostrato = checkNotNull(checkNotNull(a.lettura.di(REGISTRAZIONE)).mostrato)

        assertEquals(listOf(2, 3, 1), mostrato.decisioni[0].fonti.map { it.segmentoId }, "ordine per inizioMs")
        assertEquals(
            listOf(1, 2, 3),
            mostrato.decisioni[0].fonti.map { it.voce.voceId },
            "il voceId di ogni Fonte segue il Segmento corrente, non tutte la stessa Voce",
        )
        assertEquals(listOf("Prima decisione.", "Seconda decisione."), mostrato.decisioni.map { it.testo.testoPiano() })
        assertEquals(1, mostrato.omessi)
        assertEquals("budget", mostrato.argomento)
        assertEquals(1_500, mostrato.lunghezzaMassimaParole)
    }

    @Test
    fun `AC-S105 superato segue le riassegnazioni della struttura corrente, anche non citate, non un rename`() {
        val strutturaIniziale = listOf(
            unSegmentoSintesi(segmentoId = 1, voceId = 1),
            unSegmentoSintesi(segmentoId = 2, voceId = 2),
        )
        val bozza = BozzaRiassunto(
            sommario = null,
            decisioni = listOf(BozzaElemento("Decisione.", listOf(1), null)),
            questioniAperte = emptyList(),
            azioni = emptyList(),
            puntiChiave = emptyList(),
        )
        val struttura = unaStruttura(1 to 1, 2 to 2)
        val pronto = unRiassunto("r-1", REGISTRAZIONE).conAvvio().conCompletamento(bozza, struttura)
        val riassunti = RiassuntoRepositoryFinta()
        riassunti.salva(pronto).atteso()
        fun lettura(segmenti: List<SegmentoSintesi>, nomi: LettoreNomi = LettoreNomiFinta()): RiassuntoVisteLettura {
            val trascritti = LettoreTrascrittoFinta(mapOf(REGISTRAZIONE to segmenti))
            return RiassuntoVisteLettura(UnitaDiLavoroFinta(), riassunti, trascritti, nomi, unModelloInstallato())
        }

        val subitoDopo = lettura(strutturaIniziale)
        assertEquals(false, checkNotNull(subitoDopo.di(REGISTRAZIONE)).mostrato?.superato, "subito dopo il run")

        // Revisione: il Segmento 2, NON citato, passa alla Voce 3.
        val riassegnato = listOf(
            unSegmentoSintesi(segmentoId = 1, voceId = 1),
            unSegmentoSintesi(segmentoId = 2, voceId = 3),
        )
        val dopoRevisione = lettura(riassegnato)
        assertEquals(true, checkNotNull(dopoRevisione.di(REGISTRAZIONE)).mostrato?.superato, "dopo la riassegnazione")

        // Torna come prima: superato ridiventa false.
        val ripristinato = lettura(strutturaIniziale)
        assertEquals(false, checkNotNull(ripristinato.di(REGISTRAZIONE)).mostrato?.superato, "dopo il ripristino")

        // Revisione: il Segmento 1, CITATO dalla Decisione, passa alla Voce 3 (non solo un non citato lo fa scattare).
        val citatoRiassegnato = listOf(
            unSegmentoSintesi(segmentoId = 1, voceId = 3),
            unSegmentoSintesi(segmentoId = 2, voceId = 2),
        )
        val dopoRiassegnazioneCitata = lettura(citatoRiassegnato)
        assertEquals(
            true,
            checkNotNull(dopoRiassegnazioneCitata.di(REGISTRAZIONE)).mostrato?.superato,
            "riassegnazione di un Segmento citato dalla Decisione",
        )

        // Un rename (nomi diversi) non tocca la struttura: superato resta false.
        val nomiConRename = LettoreNomiFinta(
            attribuzioni = mapOf(VoceRef(REGISTRAZIONE, VoceId(1)) to "p-1"),
            nomiParlanti = mapOf("p-1" to "Marco"),
        )
        val conNomi = lettura(strutturaIniziale, nomiConRename)
        val messaggio = "un rename/Attribuzione non tocca superato"
        assertEquals(false, checkNotNull(conNomi.di(REGISTRAZIONE)).mostrato?.superato, messaggio)
    }

    @Test
    fun `AC-S106 richiestaAperta rispecchia in_attesa e in_corso, un fallito la lascia null`() {
        val trascritti = LettoreTrascrittoFinta(mapOf(REGISTRAZIONE to listOf(unSegmentoSintesi())))
        fun vistaDi(riassunti: RiassuntoRepository): RiassuntoVista {
            val lettura = RiassuntoVisteLettura(
                UnitaDiLavoroFinta(),
                riassunti,
                trascritti,
                LettoreNomiFinta(),
                unModelloInstallato(),
            )
            return checkNotNull(lettura.di(REGISTRAZIONE))
        }

        val inAttesa = RiassuntoRepositoryFinta()
        inAttesa.salva(unRiassunto("r-attesa", REGISTRAZIONE)).atteso()
        val vistaAttesa = vistaDi(inAttesa)
        assertEquals(RichiestaApertaVista.InAttesa(RICHIESTO_ALLE), vistaAttesa.richiestaAperta)
        assertNull(vistaAttesa.ultimoFallimento)

        val inCorso = RiassuntoRepositoryFinta()
        inCorso.salva(unRiassunto("r-corso", REGISTRAZIONE).conAvvio(AVVIATO_ALLE)).atteso()
        val vistaCorso = vistaDi(inCorso)
        assertEquals(RichiestaApertaVista.InCorso(AVVIATO_ALLE), vistaCorso.richiestaAperta)
        assertNull(vistaCorso.ultimoFallimento)

        val fallito = RiassuntoRepositoryFinta()
        val rigaFallita = unRiassunto("r-fallito", REGISTRAZIONE).conAvvio()
            .conFallimento(MotivoFallimento.ERRORE_MODELLO)
        fallito.salva(rigaFallita).atteso()
        val vistaFallita = vistaDi(fallito)
        assertNull(vistaFallita.richiestaAperta)
        assertEquals(FallimentoVista(MotivoFallimento.ERRORE_MODELLO), vistaFallita.ultimoFallimento)
    }

    @Test
    fun `AC-S107 Elaborazione aperta o ingresso troppo lungo rendono NonDisponibile, nessuna scrittura o modello`() {
        val aperta = unAmbiente(
            trascritti = LettoreTrascrittoFinta(
                mapOf(REGISTRAZIONE to listOf(unSegmentoSintesi())),
                aperte = setOf(REGISTRAZIONE),
            ),
        )
        assertEquals(
            DisponibilitaVista.NonDisponibile(MotivoNonDisponibile.ElaborazioneAperta),
            checkNotNull(aperta.lettura.di(REGISTRAZIONE)).disponibilita,
        )

        val testoLungo = "a".repeat(70_000)
        val troppoLunga = unAmbiente(
            trascritti = LettoreTrascrittoFinta(mapOf(REGISTRAZIONE to listOf(unSegmentoSintesi(testo = testoLungo)))),
        )
        assertEquals(
            DisponibilitaVista.NonDisponibile(MotivoNonDisponibile.TroppoLunga),
            checkNotNull(troppoLunga.lettura.di(REGISTRAZIONE)).disponibilita,
        )

        val spia = RiassuntoRepositorySpia(RiassuntoRepositoryFinta())
        val trascrittiSpia = LettoreTrascrittoFinta(mapOf(REGISTRAZIONE to listOf(unSegmentoSintesi())))
        val lettura = RiassuntoVisteLettura(
            UnitaDiLavoroFinta(),
            spia,
            trascrittiSpia,
            LettoreNomiFinta(),
            unModelloInstallato(),
        )
        repeat(3) { lettura.di(REGISTRAZIONE) }
        assertEquals(0, spia.scritture)

        val scope = Konsist.scopeFromPackage(PACCHETTO, "sintesi/applicazione", "main")
        val costruttore = checkNotNull(scope.classes().single { it.name == "RiassuntoVisteLettura" }.primaryConstructor)
        assertEquals(
            listOf(
                "LetturaCoerente",
                "RiassuntoRepository",
                "LettoreTrascritto",
                "LettoreNomi",
                "DisponibilitaModelloLinguistico",
            ),
            costruttore.parameters.map { it.type.text },
            "nessun collaboratore verso ModelloLinguistico e' anche possibile solo cosi'",
        )
    }

    @Test
    fun `AC-S107 la stima senza nomi e' fissata al limite, un Nome lungo non la fa passare a NonDisponibile`() {
        val voce = VoceId(1)
        fun ingressoSenzaNomi(testo: String) = IngressoRiassunto.costruisci(
            listOf(SegmentoIngresso(SegmentoId(1), voce, 0, testo)),
            nomi = emptyMap(),
        )
        // Il piu' lungo testo il cui ingresso NAME-FREE resta esattamente a LIMITE_TOKEN (ricerca sulla formula
        // pura, cosi' il confine resta esatto anche se il testo di contorno di IngressoRiassunto cambiasse).
        var basso = 0
        var alto = LimiteIngresso.LIMITE_TOKEN * 3
        while (basso < alto) {
            val meta = (basso + alto + 1) / 2
            if (LimiteIngresso.stimaToken(ingressoSenzaNomi("a".repeat(meta))) <= LimiteIngresso.LIMITE_TOKEN) {
                basso = meta
            } else {
                alto = meta - 1
            }
        }
        val testoAlLimite = "a".repeat(basso)
        check(LimiteIngresso.stimaToken(ingressoSenzaNomi(testoAlLimite)) == LimiteIngresso.LIMITE_TOKEN)

        // Un Nome molto piu' lungo di "Voce 1": se la stima leggesse davvero i nomi (bug), sforerebbe il limite.
        val nomeLungo = "Nome ".repeat(50).trim()
        val segmentoAlLimite = unSegmentoSintesi(testo = testoAlLimite)
        val a = unAmbiente(
            trascritti = LettoreTrascrittoFinta(mapOf(REGISTRAZIONE to listOf(segmentoAlLimite))),
            nomi = LettoreNomiFinta(
                attribuzioni = mapOf(VoceRef(REGISTRAZIONE, voce) to "parlante-1"),
                nomiParlanti = mapOf("parlante-1" to nomeLungo),
            ),
        )

        assertEquals(DisponibilitaVista.Disponibile, checkNotNull(a.lettura.di(REGISTRAZIONE)).disponibilita)
    }

    @Test
    fun `AC-S108 modello rispecchia i 4 stati, argomentoPrecompilato segue il piu' recente tra fallito e pronto`() {
        listOf(
            StatoModelloLinguistico.NonInstallato(6_600_000_000L) to StatoModelloVista.NonInstallato(6_600_000_000L),
            StatoModelloLinguistico.InDownload(100, 200) to StatoModelloVista.InDownload(100, 200),
            StatoModelloLinguistico.DownloadFallito(MotivoDownload.ConnessioneInterrotta) to
                StatoModelloVista.DownloadFallito(MotivoDownload.ConnessioneInterrotta),
            StatoModelloLinguistico.Installato to StatoModelloVista.Installato,
        ).forEach { (statoPorta, statoAtteso) ->
            val a = unAmbiente(disponibilita = DisponibilitaModelloLinguisticoFinta(statoPorta))
            assertEquals(statoAtteso, checkNotNull(a.lettura.di(REGISTRAZIONE)).modello, "$statoPorta")
        }

        val trascritti = LettoreTrascrittoFinta(mapOf(REGISTRAZIONE to listOf(unSegmentoSintesi())))
        fun argomentoPrecompilatoDi(riassunti: RiassuntoRepositoryFinta): String? {
            val lettura = RiassuntoVisteLettura(
                UnitaDiLavoroFinta(),
                riassunti,
                trascritti,
                LettoreNomiFinta(),
                unModelloInstallato(),
            )
            return checkNotNull(lettura.di(REGISTRAZIONE)).argomentoPrecompilato
        }

        // Solo un pronto.
        val soloPronto = RiassuntoRepositoryFinta()
        val pronto = unRiassunto("r-pronto", REGISTRAZIONE, argomento = "argomento pronto").conAvvio()
            .conCompletamento(unaBozzaMinima(), unaStruttura(1 to 1))
        soloPronto.salva(pronto).atteso()
        assertEquals("argomento pronto", argomentoPrecompilatoDi(soloPronto))

        // Solo un fallito.
        val soloFallito = RiassuntoRepositoryFinta()
        val rigaFallita = unRiassunto("r-fallito", REGISTRAZIONE, argomento = "argomento fallito")
            .conAvvio().conFallimento()
        soloFallito.salva(rigaFallita).atteso()
        assertEquals("argomento fallito", argomentoPrecompilatoDi(soloFallito))

        // Un fallito piu' recente del pronto: vince il fallito.
        val falliroPiuRecente = RiassuntoRepositoryFinta()
        val prontoVecchio = unRiassunto("r-pronto-2", REGISTRAZIONE, argomento = "vecchio", richiestoAlle = T0)
            .conAvvio(T0.plusSeconds(1)).conCompletamento(unaBozzaMinima(), unaStruttura(1 to 1))
        falliroPiuRecente.salva(prontoVecchio).atteso()
        val fallitoNuovo = unRiassunto(
            "r-fallito-2",
            REGISTRAZIONE,
            argomento = "nuovo",
            richiestoAlle = T0.plusSeconds(60),
        ).conAvvio(T0.plusSeconds(61)).conFallimento()
        falliroPiuRecente.salva(fallitoNuovo).atteso()
        assertEquals("nuovo", argomentoPrecompilatoDi(falliroPiuRecente))

        // Un fallito piu' vecchio del pronto: vince il pronto (il caso inverso, non solo "nessun fallito").
        val prontoPiuRecente = RiassuntoRepositoryFinta()
        val fallitoVecchio = unRiassunto(
            "r-fallito-3",
            REGISTRAZIONE,
            argomento = "vecchio fallito",
            richiestoAlle = T0,
        ).conAvvio(T0.plusSeconds(1)).conFallimento()
        prontoPiuRecente.salva(fallitoVecchio).atteso()
        val prontoNuovo = unRiassunto(
            "r-pronto-3",
            REGISTRAZIONE,
            argomento = "nuovo pronto",
            richiestoAlle = T0.plusSeconds(60),
        ).conAvvio(T0.plusSeconds(61)).conCompletamento(unaBozzaMinima(), unaStruttura(1 to 1))
        prontoPiuRecente.salva(prontoNuovo).atteso()
        assertEquals("nuovo pronto", argomentoPrecompilatoDi(prontoPiuRecente))
    }

    @Test
    fun `AC-C32 le letture interne di di girano tutte dentro inLettura`() {
        val uow = UnitaDiLavoroFinta()
        val viste = mutableListOf<Boolean>()
        val trascrittiDelega = LettoreTrascrittoFinta(mapOf(REGISTRAZIONE to listOf(unSegmentoSintesi())))
        val trascritti = object : LettoreTrascritto {
            override fun segmenti(r: RegistrazioneId) = trascrittiDelega.segmenti(r).also { viste += uow.letturaAperta }
            override fun elaborazioneAperta(r: RegistrazioneId) =
                trascrittiDelega.elaborazioneAperta(r).also { viste += uow.letturaAperta }
        }
        val riassuntiDelega = RiassuntoRepositoryFinta()
        val riassunti = object : RiassuntoRepository by riassuntiDelega {
            override fun diRegistrazione(r: RegistrazioneId) =
                riassuntiDelega.diRegistrazione(r).also { viste += uow.letturaAperta }
        }
        val nomiDelega = LettoreNomiFinta()
        val nomi = object : LettoreNomi {
            override fun nomi(r: RegistrazioneId) = nomiDelega.nomi(r).also { viste += uow.letturaAperta }
        }
        val lettura = RiassuntoVisteLettura(uow, riassunti, trascritti, nomi, unModelloInstallato())

        lettura.di(REGISTRAZIONE)

        assertTrue(viste.isNotEmpty(), "nessuna lettura interna osservata")
        assertTrue(viste.all { it }, "una lettura interna e' girata fuori da inLettura: $viste")
    }

    private fun unaBozzaMinima(): BozzaRiassunto = BozzaRiassunto(
        sommario = null,
        decisioni = listOf(BozzaElemento("Decisione.", listOf(1), null)),
        questioniAperte = emptyList(),
        azioni = emptyList(),
        puntiChiave = emptyList(),
    )

    private fun unModelloInstallato(): DisponibilitaModelloLinguistico =
        DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.Installato)

    private fun TestoConVociVista.testoPiano(): String = joinToString("") {
        when (it) {
            is ParteTestoVista.Testo -> it.testo
            is ParteTestoVista.Voce -> "{V${it.voce.voceId}}"
        }
    }

    /** Every observable field but the id, to compare a row before/after an unrelated read (no write happened). */
    private fun Riassunto.statoRigaPerTest(): List<Any?> = listOf(
        registrazioneId, argomento, lunghezzaMassima, richiestoAlle, stato, avviatoAlle, motivoFallimento, sommario,
        decisioni.toList(), questioniAperte.toList(), azioni.toList(), puntiChiave.toList(), omessi, struttura,
    )

    private class RiassuntoRepositorySpia(private val delega: RiassuntoRepository) : RiassuntoRepository by delega {
        var scritture: Int = 0
            private set

        override fun salva(r: Riassunto): Esito<Unit> {
            scritture++
            return delega.salva(r)
        }

        override fun concludi(r: Riassunto): Esito<Boolean> {
            scritture++
            return delega.concludi(r)
        }

        override fun rimuovi(id: RiassuntoId): Esito<Unit> {
            scritture++
            return delega.rimuovi(id)
        }

        override fun rimuoviDiRegistrazione(r: RegistrazioneId): Esito<Int> {
            scritture++
            return delega.rimuoviDiRegistrazione(r)
        }
    }

    private data class Ambiente(val lettura: RiassuntoVisteLettura, val riassunti: RiassuntoRepositoryFinta)

    /** Fresh fakes wired into a [RiassuntoVisteLettura]; a Trascritto is present unless overridden. */
    private fun unAmbiente(
        trascritti: LettoreTrascritto = LettoreTrascrittoFinta(mapOf(REGISTRAZIONE to listOf(unSegmentoSintesi()))),
        nomi: LettoreNomi = LettoreNomiFinta(),
        disponibilita: DisponibilitaModelloLinguistico = unModelloInstallato(),
    ): Ambiente {
        val riassunti = RiassuntoRepositoryFinta()
        val lettura = RiassuntoVisteLettura(UnitaDiLavoroFinta(), riassunti, trascritti, nomi, disponibilita)
        return Ambiente(lettura, riassunti)
    }

    private fun unSegmentoSintesi(
        segmentoId: Int = 1,
        voceId: Int = 1,
        inizioMs: Long = 0,
        fineMs: Long = inizioMs + 1_000,
        testo: String = "Testo di prova.",
    ): SegmentoSintesi = SegmentoSintesi(SegmentoId(segmentoId), VoceId(voceId), IntervalloMs(inizioMs, fineMs), testo)

    private companion object {
        const val PACCHETTO = "snastro.sintesi.applicazione.letture"
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        val RICHIESTO_ALLE: java.time.Instant = java.time.Instant.parse("2026-09-26T10:00:00.123Z")
        val AVVIATO_ALLE: java.time.Instant = java.time.Instant.parse("2026-09-26T10:01:00.456Z")
        val T0: java.time.Instant = java.time.Instant.parse("2026-09-26T09:00:00Z")
    }
}
