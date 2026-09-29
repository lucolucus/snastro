package snastro.avvio.r3

import snastro.avvio.CodaCondivisa
import snastro.avvio.ContestoEstensione
import snastro.avvio.EstensioneSessione
import snastro.avvio.ProgettoEsteso
import snastro.avvio.TipoElementoCoda
import snastro.avvio.r2.CollaboratoriR2
import snastro.avvio.r2.PorteProgettoParlanti.Companion.parlanti
import snastro.avvio.r3.PorteProgettoSintesi.Companion.sintesi
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
import snastro.kernel.mappa
import snastro.parlanti.applicazione.letture.NomiDelleVoci
import snastro.sintesi.adattatori.eventi.AbbonatoProgettoSintesi
import snastro.sintesi.adattatori.eventi.AbbonatoTrascrizioneSintesi
import snastro.sintesi.adattatori.porte.LettoreNomiDaParlanti
import snastro.sintesi.adattatori.porte.LettoreTrascrittoDaTrascrizione
import snastro.sintesi.applicazione.comandi.EseguiProssimoRiassuntoServizio
import snastro.sintesi.applicazione.comandi.ModificaLunghezzaMassimaRiassunto
import snastro.sintesi.applicazione.comandi.ModificaLunghezzaMassimaRiassuntoServizio
import snastro.sintesi.applicazione.comandi.RecuperaRiassuntiInterrotti
import snastro.sintesi.applicazione.comandi.RecuperaRiassuntiInterrottiServizio
import snastro.sintesi.applicazione.comandi.Riassumi
import snastro.sintesi.applicazione.comandi.RiassumiServizio
import snastro.sintesi.applicazione.letture.ImpostazioniSintesiLettura
import snastro.sintesi.applicazione.letture.RiassuntiInAttesa
import snastro.sintesi.applicazione.letture.RiassuntoVisteLettura
import snastro.sintesi.applicazione.politiche.ApplicaEliminazioneRegistrazioneSintesiPolitica
import snastro.sintesi.applicazione.politiche.ApplicaSostituzioneTrascrittoSintesiPolitica
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguistico
import snastro.sintesi.applicazione.porte.ModelloLinguistico
import snastro.trascrizione.applicazione.letture.VociDelTrascritto
import java.time.Clock
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger

/**
 * The R3 (Sintesi) extension of the per-project graph (ADR 0021 §10): R2's ([r2], itself R1's extended) EXTENDED —
 * never re-created — over the SAME database, dispatcher and session scope.
 *
 * Order in [apri]:
 * 1. The Sintesi SQL repositories and the two cross-context read ports (Trascrizione, Parlanti), all over
 *    `contesto.porte`'s ONE instance of each repository and its ONE `StatiElaborazione` (ADR 0030 §1,
 *    AC-C60/AC-C61/AC-C63): [PorteProgettoSintesi] and `PorteProgettoParlanti`, the parts `PorteProgetto` owns.
 * 2. The Sintesi SYNCHRONOUS subscribers — `AbbonatoTrascrizioneSintesi` (TrascrittoSostituito, ADR 0021 §6) and
 *    `AbbonatoProgettoSintesi` (RegistrazioneEliminata, ADR 0024 §1) — then the after-commit
 *    [AggiornamentiVistaSintesi]; all BEFORE [r2] runs, which registers its own two synchronous purges and only then
 *    lets R1 build the shared queue: all five (event, subscriber) pairs exist before any command (AC-S143).
 * 3. [r2] over this context plus the Riassunto queue source ([fonteCodaRiassunto], ADR 0023 §1): the queue runs
 *    `RecuperaElaborazioniInterrotte` AND `RecuperaRiassuntiInterrotti` before its first claim (AC-S145).
 *
 * Every command gets `dispatcher.unitaDiLavoro`, never the raw one (the AC-359 pattern). The LLM ([modello]) runs on
 * the queue's worker, outside any transaction, and takes no sherpa Mutex (ADR 0023 §5, AC-S150).
 */
@Suppress("LongParameterList") // one parameter per app-wide collaborator of the per-project graph
internal class EstensioneR3(
    private val r2: EstensioneSessione,
    private val clock: Clock,
    private val generatoreId: GeneratoreId,
    internal val modello: ModelloLinguistico,
    private val disponibilita: DisponibilitaModelloLinguistico,
) : EstensioneSessione {
    @Suppress("LongMethod") // linear wiring, one statement per collaborator
    override fun apri(contesto: ContestoEstensione): ProgettoEsteso {
        val dispatcher = contesto.dispatcher
        val uow = dispatcher.unitaDiLavoro
        val porte = contesto.porte
        val progettoId = contesto.progettoId
        val riassunti = porte.sintesi.riassunti
        val lunghezze = porte.sintesi.lunghezze
        val trascritti = porte.trascritti
        // ADR 0030 §1/AC-C63: porte.statiElaborazione, the SAME instance R1's pipeline writes into and S2 reads,
        // never a second StatiElaborazione over a fresh, unwritten FasiInCorso().
        val lettoreTrascritto = LettoreTrascrittoDaTrascrizione(VociDelTrascritto(trascritti), porte.statiElaborazione)
        // AC-C61: the project's ONE Parlanti repositories (PorteProgettoParlanti, owned by porte), built on first use:
        // here, before r2 runs; R1's Documento names and R2 then receive these same two instances.
        val nomi = LettoreNomiDaParlanti(NomiDelleVoci(porte.parlanti.attribuzioni, porte.parlanti.parlanti))

        AbbonatoTrascrizioneSintesi(
            dispatcher,
            ApplicaSostituzioneTrascrittoSintesiPolitica(
                generatoreId,
                clock,
                progettoId,
                riassunti,
                lunghezze,
                lettoreTrascritto,
                disponibilita,
                dispatcher,
            ),
        )
        AbbonatoProgettoSintesi(dispatcher, ApplicaEliminazioneRegistrazioneSintesiPolitica(riassunti, dispatcher))

        val coda = AtomicReference<CodaCondivisa?>(null)
        val aggiornamenti = AggiornamentiVistaSintesi(
            dispatcher,
            avanza = { coda.get()?.avanza() },
            annullaInCorso = { r -> coda.get()?.annullaInCorso(TipoElementoCoda.RIASSUNTO, r.valore) },
        )

        val esecuzioni = EsecuzioniRiassunto(riassunti)
        val esegui = EseguiProssimoRiassuntoServizio(
            uow,
            clock,
            RiassuntoRepositoryConReclamo(riassunti, esecuzioni),
            lettoreTrascritto,
            nomi,
            modello,
            disponibilita,
            dispatcher,
            esecuzioni.annullato,
        )
        val recupera = RecuperaRiassuntiInterrottiServizio(uow, riassunti, dispatcher)
        val fonte = fonteCodaRiassunto(
            elenco = RiassuntiInAttesa(riassunti),
            esegui = esegui::esegui,
            recupera = {
                val esito = recupera.esegui(RecuperaRiassuntiInterrotti)
                if (esito is Esito.Errore) log.warning("recupero dei riassunti interrotti fallito: $esito")
            },
            esecuzioni = esecuzioni,
        )

        val collaboratoriR2 = r2.apri(contesto.conFontiCoda(listOf(fonte))) as CollaboratoriR2
        coda.set(collaboratoriR2.r1.coda)

        val riassumi = RiassumiServizio(
            uow,
            generatoreId,
            clock,
            progettoId,
            riassunti,
            lunghezze,
            lettoreTrascritto,
            disponibilita,
            dispatcher,
        )
        val modifica = ModificaLunghezzaMassimaRiassuntoServizio(uow, lunghezze, dispatcher)
        val impostazioni = ImpostazioniSintesiLettura(lunghezze)
        // The read-model itself owns the ONE snapshot (RiassuntoVisteLettura, ADR 0029 §5/AC-C32): the row and
        // its children from the same snapshot, never a row read before a concurrent completion commits and its
        // elements after (INV-S1 reconstitution would throw) — no composition wrap needed anymore.
        val vista = RiassuntoVisteLettura(contesto.lettura, riassunti, lettoreTrascritto, nomi, disponibilita)
        return CollaboratoriR3(
            r2 = collaboratoriR2,
            vista = vista::di,
            impostazioni = { impostazioni.di(progettoId) },
            riassumi = { id, argomento -> riassumi.esegui(Riassumi(id, argomento)).mappa { } },
            modificaLunghezzaMassima = { parole ->
                modifica.esegui(ModificaLunghezzaMassimaRiassunto(progettoId, parole))
            },
            aggiornamentiSintesi = aggiornamenti,
        )
    }

    private companion object {
        val log: Logger = Logger.getLogger(EstensioneR3::class.java.name)
    }
}
