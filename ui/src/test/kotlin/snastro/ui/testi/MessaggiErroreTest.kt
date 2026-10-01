package snastro.ui.testi

import snastro.kernel.ElaborazioneId
import snastro.kernel.ErroreDiProva
import snastro.kernel.ErroreDominio
import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.unIncontroDi
import snastro.parlanti.dominio.ErroreParlanti
import snastro.progetto.applicazione.porte.ErroreApplicazioneProgetto
import snastro.progetto.dominio.ErroreProgetto
import snastro.sintesi.applicazione.porte.ErroreApplicazioneSintesi
import snastro.sintesi.dominio.ErroreSintesi
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.ui.ErroreSessione
import snastro.ui.modelli.ErroreServizioModelli
import snastro.ui.registrazione.ErroreComandoVoce
import java.io.File
import java.util.jar.JarFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * AC-180: every member of every context error hierarchy `:ui` can see has a plain-Italian message,
 * with no `else` branch anywhere (RC-4) — a new member breaks `messaggioPer`'s compilation.
 * [verificaCopertura] additionally reflects on the sealed interface's real JVM `PermittedSubclasses`
 * (this toolchain, JDK 21, compiles Kotlin `sealed` to a genuine JVM sealed type) and checks it against
 * the SET of classes mapped below (L1), so a member added here with a `messaggioPer` branch but no test
 * instance — or a branch that compiles but returns a blank string — goes red too, without relying on
 * anyone remembering to keep a hand-written list in sync.
 */
class MessaggiErroreTest {
    /**
     * [ErroreTrascrizione.TransizioneNonAmmessa]'s `da`/`verso` are `StatoElaborazione` — a
     * `:trascrizione:dominio` VO, not an `Errore<Contesto>`, so CR-1(b) forbids `:ui` importing it
     * (only the sealed error hierarchies are allowed, never an aggregate/VO). The class is found and
     * its enum constants read purely by name at runtime (`Class.forName`, never a static import), so
     * this test exercises the real member without creating a source-level `:ui` → dominio-VO edge.
     */
    private fun transizioneNonAmmessa(id: String, da: String, verso: String): ErroreTrascrizione.TransizioneNonAmmessa {
        val statoClass = Class.forName("snastro.trascrizione.dominio.StatoElaborazione")
        fun valore(nome: String) = statoClass.enumConstants.first { (it as Enum<*>).name == nome }
        val costruttore = ErroreTrascrizione.TransizioneNonAmmessa::class.java
            .getDeclaredConstructor(String::class.java, statoClass, statoClass)
        costruttore.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return costruttore.newInstance(id, valore(da), valore(verso)) as ErroreTrascrizione.TransizioneNonAmmessa
    }

    // L1: compares the SET of classes, not just a count — two instances of the same permitted subclass
    // (leaving another member uncovered) had the same size as the real hierarchy and slipped through.
    private fun <E : Any> verificaCopertura(gerarchia: Class<E>, istanze: List<E>, messaggioPer: (E) -> String) {
        val permesse = gerarchia.permittedSubclasses?.toSet()
            ?: error("${gerarchia.name} non è un'interfaccia sealed JVM (nessun PermittedSubclasses)")
        val coperte = istanze.map { it::class.java }.toSet()
        assertEquals(
            permesse,
            coperte,
            "attesa un'istanza per ognuno dei membri di ${gerarchia.simpleName} " +
                "(${permesse.map { it.simpleName }}), coperti ${coperte.map { it.simpleName }} — un membro è " +
                "stato aggiunto o rimosso, o coperto due volte, senza aggiornare questo test",
        )
        istanze.forEach { errore -> assertTrue(messaggioPer(errore).isNotBlank(), "messaggio vuoto per $errore") }
    }

    @Test
    fun `AC-180 ErroreSessione`() {
        verificaCopertura(
            ErroreSessione::class.java,
            listOf(
                ErroreSessione.NomeProgettoVuoto,
                ErroreSessione.CartellaNonValida,
                ErroreSessione.ProgettoGiaAperto,
                ErroreSessione.DatabasePiuRecente,
            ),
        ) { messaggioPer(it) }
    }

    @Test
    fun `AC-180 ErroreApplicazioneProgetto`() {
        verificaCopertura(
            ErroreApplicazioneProgetto::class.java,
            listOf(
                ErroreApplicazioneProgetto.AudioNonLeggibile("x"),
                ErroreApplicazioneProgetto.FormatoNonSupportato("x"),
                ErroreApplicazioneProgetto.CopiaFallita("x"),
            ),
        ) { messaggioPer(it) }
    }

    @Test
    fun `AC-180 ErroreProgetto`() {
        verificaCopertura(
            ErroreProgetto::class.java,
            listOf(
                ErroreProgetto.NomeProgettoVuoto,
                ErroreProgetto.ProgettoGiaPresente,
                ErroreProgetto.RegistrazioneNonTrovata(RegistrazioneId("id-1")),
                ErroreProgetto.TitoloVuoto,
                ErroreProgetto.TitoloGiaUsato("Seduta"),
                ErroreProgetto.OraDiInizioNonValida,
            ),
        ) { messaggioPer(it) }
    }

    @Test
    fun `AC-180 ErroreTrascrizione`() {
        verificaCopertura(
            ErroreTrascrizione::class.java,
            listOf(
                transizioneNonAmmessa("id-1", "IN_ATTESA", "IN_CORSO"),
                ErroreTrascrizione.ElaborazioneGiaAperta(RegistrazioneId("id-1")),
                ErroreTrascrizione.ElaborazioneGiaAvviata(ElaborazioneId("id-1")),
                ErroreTrascrizione.ElaborazioneNonTrovata(ElaborazioneId("id-1")),
                ErroreTrascrizione.RegistrazioneNonTrovata(RegistrazioneId("id-1")),
                ErroreTrascrizione.TrascrittoNonTrovato(RegistrazioneId("id-1")),
                ErroreTrascrizione.NessunParlatoRilevato,
                ErroreTrascrizione.SegmentoOltreLaDurata(IntervalloMs(0, 1000), 500),
                ErroreTrascrizione.VoceNonTrovata(VoceId(1)),
                ErroreTrascrizione.SegmentoNonTrovato(SegmentoId(1)),
                ErroreTrascrizione.UnioneNonAmmessa(VoceId(1), VoceId(2)),
                ErroreTrascrizione.DivisioneNonAmmessa(VoceId(1), setOf(SegmentoId(1))),
                ErroreTrascrizione.RiassegnazioneNonAmmessa(SegmentoId(1), null),
                ErroreTrascrizione.NumeroPersoneFuoriIntervallo(11),
                ErroreTrascrizione.TrascrittoCambiato(RegistrazioneId("id-1")),
            ),
        ) { messaggioPer(it) }
    }

    @Test
    fun `AC-477 i testi di ElaborazioneGiaAvviata e ElaborazioneNonTrovata`() {
        assertEquals(
            "La trascrizione è già partita: non si può più annullare",
            messaggioPer(ErroreTrascrizione.ElaborazioneGiaAvviata(ElaborazioneId("id-1"))),
        )
        assertEquals(
            "Questa trascrizione non è più in coda",
            messaggioPer(ErroreTrascrizione.ElaborazioneNonTrovata(ElaborazioneId("id-1"))),
        )
    }

    @Test
    fun `AC-515 il testo di TrascrittoCambiato`() {
        assertEquals(
            "La trascrizione è cambiata dopo il confronto: ricalcola l'anteprima",
            messaggioPer(ErroreTrascrizione.TrascrittoCambiato(RegistrazioneId("id-1"))),
        )
    }

    @Test
    fun `M2 TransizioneNonAmmessa usa un messaggio generico che non nomina gli stati`() {
        val primo = transizioneNonAmmessa("id-1", "IN_ATTESA", "IN_CORSO")
        val secondo = transizioneNonAmmessa("id-2", "COMPLETATA", "FALLITA")
        assertEquals("Operazione non ammessa nello stato attuale dell'elaborazione.", messaggioPer(primo))
        // Same message regardless of da/verso: production code no longer reads either field (M2).
        assertEquals(messaggioPer(primo), messaggioPer(secondo))
    }

    @Test
    fun `AC-180 ErroreParlanti`() {
        verificaCopertura(
            ErroreParlanti::class.java,
            listOf(
                ErroreParlanti.ParlanteNonTrovato(ParlanteId("id-1")),
                ErroreParlanti.TrascrittoNonTrovato(RegistrazioneId("id-1")),
                ErroreParlanti.VoceNonTrovata(VoceRef(unIncontroDi(RegistrazioneId("id-1")), VoceId(1))),
                ErroreParlanti.ParlanteEliminatoNonModificabile(ParlanteId("id-1")),
                ErroreParlanti.PromozioneNonAmmessa(ParlanteId("id-1")),
                ErroreParlanti.NomeGiaInUso("Marco"),
                ErroreParlanti.NomeVuoto,
                ErroreParlanti.VoceGiaAttribuita(VoceRef(unIncontroDi(RegistrazioneId("id-1")), VoceId(1))),
                ErroreParlanti.VoceCambiata(VoceRef(unIncontroDi(RegistrazioneId("id-1")), VoceId(1))),
                ErroreParlanti.RiferimentiInsufficienti(RegistrazioneId("id-1")),
            ),
        ) { messaggioPer(it) }
    }

    @Test
    fun `AC-180 ErroreServizioModelli`() {
        verificaCopertura(
            ErroreServizioModelli::class.java,
            listOf(
                ErroreServizioModelli.HashNonValido("asr-parakeet-tdt-0.6b-v3-int8"),
                ErroreServizioModelli.ArchivioNonValido("segmentazione-pyannote-3.0"),
                ErroreServizioModelli.ReteAssente,
                ErroreServizioModelli.ScritturaFallita("disco pieno"),
                ErroreServizioModelli.DownloadFallito("connessione interrotta"),
                ErroreServizioModelli.SpazioInsufficiente(6_169_341_984),
            ),
        ) { messaggioPer(it) }
    }

    @Test
    fun `AC-S34 il testo di SpazioInsufficiente arrotonda in GB decimali con la virgola`() {
        assertEquals(
            "Non c'è abbastanza spazio sul disco (servono 6,2 GB).",
            messaggioPer(ErroreServizioModelli.SpazioInsufficiente(6_169_341_984)),
        )
    }

    @Test
    fun `AC-418 ErroreComandoVoce`() {
        verificaCopertura(ErroreComandoVoce::class.java, listOf(ErroreComandoVoce.NonRiuscito)) { messaggioPer(it) }
    }

    @Test
    fun `AC-S139 ErroreSintesi`() {
        verificaCopertura(
            ErroreSintesi::class.java,
            listOf(
                ErroreSintesi.RiassuntoGiaAperto(IncontroId("id-1")),
                ErroreSintesi.ModelloNonInstallato,
                ErroreSintesi.TrascrittoNonDisponibile(IncontroId("id-1")),
                ErroreSintesi.ElaborazioneGiaAperta(IncontroId("id-1")),
                ErroreSintesi.RegistrazioneTroppoLunga(30_000, 28_000),
                ErroreSintesi.ArgomentoTroppoLungo(210, 200),
                ErroreSintesi.LunghezzaMassimaFuoriIntervallo(299, 300, 2500),
                ErroreSintesi.TransizioneNonAmmessa("in_attesa", "in_corso"),
                ErroreSintesi.RiassuntoNonTrovato("id-1"),
            ),
        ) { messaggioPer(it) }
    }

    @Test
    fun `AC-S139 i testi di ArgomentoTroppoLungo e LunghezzaMassimaFuoriIntervallo usano i limiti dell errore`() {
        assertEquals("Al massimo 200 caratteri.", messaggioPer(ErroreSintesi.ArgomentoTroppoLungo(210, 200)))
        assertEquals(
            "Scegli fra 300 e 2500 parole.",
            messaggioPer(ErroreSintesi.LunghezzaMassimaFuoriIntervallo(299, 300, 2500)),
        )
    }

    @Test
    fun `AC-S139 ErroreApplicazioneSintesi`() {
        verificaCopertura(
            ErroreApplicazioneSintesi::class.java,
            listOf(
                ErroreApplicazioneSintesi.ModelloNonDisponibile,
                ErroreApplicazioneSintesi.IngressoTroppoLungo(31_000),
                ErroreApplicazioneSintesi.ErroreRuntime("metal non disponibile"),
                ErroreApplicazioneSintesi.RispostaNonValida,
                ErroreApplicazioneSintesi.Annullato,
            ),
        ) { messaggioPer(it) }
    }

    /**
     * X6 (ADR 0003 RC-4): the hierarchies are DISCOVERED on this module's own classpath — every sealed direct
     * subtype of [ErroreDominio] `:ui` can load — never listed by hand, so a hierarchy newly made visible to `:ui`
     * (a new `*:applicazione` dependency, a new `ErroreApplicazione<Contesto>`) without a branch in the entry
     * point goes red here instead of crashing a screen at `else -> error(..)`.
     */
    @Test
    fun `il punto di ingresso instrada ogni gerarchia visibile a ui`() {
        val esempi: List<ErroreDominio> = listOf(
            ErroreSessione.CartellaNonValida,
            ErroreApplicazioneProgetto.AudioNonLeggibile("x"),
            ErroreProgetto.ProgettoGiaPresente,
            ErroreTrascrizione.NessunParlatoRilevato,
            ErroreParlanti.NomeVuoto,
            ErroreServizioModelli.ReteAssente,
            ErroreComandoVoce.NonRiuscito,
            ErroreSintesi.ModelloNonInstallato,
            ErroreApplicazioneSintesi.RispostaNonValida,
        )

        assertEquals(
            gerarchieVisibili().map { it.name }.sorted(),
            esempi.map { gerarchiaDi(it).name }.distinct().sorted(),
            "ogni gerarchia di ErroreDominio visibile a :ui ha un esempio (e un ramo in messaggioPer)",
        )
        esempi.forEach { errore -> assertTrue(messaggioPer(errore).isNotBlank(), "messaggio vuoto per $errore") }
    }

    /** The sealed `Errore<X>` of [errore]: the one interface of its class directly extending [ErroreDominio]. */
    private fun gerarchiaDi(errore: ErroreDominio): Class<*> =
        errore.javaClass.interfaces.single { ErroreDominio::class.java in it.interfaces }

    /**
     * Every sealed interface directly extending [ErroreDominio] among the `snastro` classes on this test's classpath
     * (class directories and jars alike), minus [MAI_MOSTRATE]. Loaded without initialisation.
     */
    private fun gerarchieVisibili(): Set<Class<*>> {
        val caricatore = javaClass.classLoader
        return System.getProperty("java.class.path").split(File.pathSeparator)
            .map(::File)
            .flatMap(::classiSnastro)
            .map { nome -> Class.forName(nome, false, caricatore) }
            .filter { it.isInterface && it.isSealed && ErroreDominio::class.java in it.interfaces }
            .filterNot { it in MAI_MOSTRATE }
            .toSet()
    }

    private fun classiSnastro(voce: File): List<String> {
        val percorsi = when {
            voce.isDirectory ->
                voce.walkTopDown().filter { it.isFile }.map { it.relativeTo(voce).invariantSeparatorsPath }.toList()
            voce.isFile && voce.name.endsWith(".jar") ->
                JarFile(voce).use { jar -> jar.entries().toList().map { it.name } }
            else -> emptyList()
        }
        return percorsi.filter { it.startsWith("snastro/") && it.endsWith(".class") }
            .map { it.removeSuffix(".class").replace('/', '.') }
    }

    @Test
    fun `il punto di ingresso rifiuta un ErroreDominio non mappato`() {
        // ErroreDiProva (kernel testFixtures, CR-8 shape) stands in for "a hierarchy not yet wired
        // into the entry-point dispatcher" — RC-4: its only `else` is a programmer error, never a
        // generic user message.
        assertFailsWith<IllegalStateException> { messaggioPer(ErroreDiProva.Fallito("x")) }
    }

    private companion object {
        /** Hierarchies on the classpath that never reach a screen: [ErroreDiProva] is kernel testFixtures only. */
        val MAI_MOSTRATE: Set<Class<*>> = setOf(ErroreDiProva::class.java)
    }
}
