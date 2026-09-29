package snastro.avvio.progetto

import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.LetturaCoerente
import snastro.kernel.UnitaDiLavoro
import snastro.parlanti.adattatori.persistenza.AttribuzioneRepositorySql
import snastro.parlanti.adattatori.persistenza.ParlanteRepositorySql
import snastro.parlanti.adattatori.porte.LettoreVociDaTrascrizione
import snastro.parlanti.applicazione.letture.NomiDelleVoci
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.progetto.adattatori.persistenza.EliminazioniInSospesoSql
import snastro.progetto.adattatori.persistenza.ProgettoRepositorySql
import snastro.progetto.adattatori.persistenza.RegistrazioneRepositorySql
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.sintesi.adattatori.persistenza.LunghezzaMassimaRiassuntoRepositorySql
import snastro.sintesi.adattatori.persistenza.RiassuntoRepositorySql
import snastro.trascrizione.adattatori.persistenza.ElaborazioneRepositorySql
import snastro.trascrizione.adattatori.persistenza.TrascrittoRepositorySql
import snastro.trascrizione.applicazione.letture.FasiInCorso
import snastro.trascrizione.applicazione.letture.StatiElaborazione
import snastro.trascrizione.applicazione.letture.VociDelTrascritto
import java.time.Clock
import snastro.documento.adattatori.porte.LettoreNomiDaParlanti as LettoreNomiDocumento
import snastro.documento.adattatori.porte.LettoreTrascrittoDaTrascrizione as LettoreTrascrittoDocumento
import snastro.parlanti.adattatori.porte.LettoreRegistrazioneDaProgetto as LettoreRegistrazioneParlanti
import snastro.sintesi.adattatori.porte.LettoreNomiDaParlanti as LettoreNomiSintesi
import snastro.sintesi.adattatori.porte.LettoreTrascrittoDaTrascrizione as LettoreTrascrittoSintesi
import snastro.trascrizione.adattatori.porte.LettoreRegistrazioneDaProgetto as LettoreRegistrazioneTrascrizione

/**
 * The ports of ONE open project (ADR 0030 §1, AC-C60..AC-C63): built EXACTLY ONCE per `crea`/`apri` by
 * [SessioneProgettoImpl] and handed to [apriProgetto]. It holds the database, the ONE [UnitaDiLavoroSql] (passed as
 * the project's [LetturaCoerente] AND as the [dispatcher]'s delegate, ADR 0029 §3), the [dispatcher] itself, every SQL
 * repository (one instance each), [catalogo], the ONE [statiElaborazione] (AC-C63) and the cross-context readers.
 * Nothing else in `:avvio` builds a repository: every module (`Modulo<Contesto>`) receives the instances held here.
 */
internal class PorteProgetto(
    val database: SnastroDatabase,
    clock: Clock,
    costruisciRegistrazioni: (SnastroDatabase) -> RegistrazioneRepository = registrazioniSql,
) {
    private val unitaDiLavoroSql = UnitaDiLavoroSql(database)

    /** ADR 0029 §3, AC-C62: the SAME `UnitaDiLavoroSql` the [dispatcher] delegates to — never a second one. */
    val lettura: LetturaCoerente = unitaDiLavoroSql

    /** The project's ONE event dispatcher (ADR 0012), over the same [UnitaDiLavoroSql] as [lettura]. */
    val dispatcher: DispatcherEventiInMemoria = DispatcherEventiInMemoria(unitaDiLavoroSql)

    /** AC-355/AC-359: what every command service receives — never the raw `UnitaDiLavoroSql`. */
    val unitaDiLavoro: UnitaDiLavoro = dispatcher.unitaDiLavoro

    val registrazioni: RegistrazioneRepository = costruisciRegistrazioni(database)
    val progetti: ProgettoRepositorySql = ProgettoRepositorySql(database)
    val eliminazioniInSospeso: EliminazioniInSospesoSql = EliminazioniInSospesoSql(database, clock)
    val catalogo: CatalogoRegistrazioni = CatalogoRegistrazioni(registrazioni)
    val trascritti: TrascrittoRepositorySql = TrascrittoRepositorySql(database, lettura)
    val elaborazioni: ElaborazioneRepositorySql = ElaborazioneRepositorySql(database)
    val parlanti: ParlanteRepositorySql = ParlanteRepositorySql(database, lettura)
    val attribuzioni: AttribuzioneRepositorySql = AttribuzioneRepositorySql(database)
    val riassunti: RiassuntoRepositorySql = RiassuntoRepositorySql(database, lettura)
    val lunghezze: LunghezzaMassimaRiassuntoRepositorySql = LunghezzaMassimaRiassuntoRepositorySql(database)

    /** AC-C63: written by the pipeline, read by [statiElaborazione]; shared with Sintesi, never rebuilt. */
    val fasiInCorso: FasiInCorso = FasiInCorso()

    /** AC-C63: the project's ONE `StatiElaborazione`: S2 and Sintesi read this SAME instance. */
    val statiElaborazione: StatiElaborazione = StatiElaborazione(elaborazioni, trascritti, fasiInCorso)

    /** Trascrizione's public read API over [trascritti], shared by every cross-context reader below. */
    val vociDelTrascritto: VociDelTrascritto = VociDelTrascritto(trascritti)

    /** Parlanti's public names query over [attribuzioni]/[parlanti], shared by Documento's and Sintesi's readers. */
    val nomiDelleVoci: NomiDelleVoci = NomiDelleVoci(attribuzioni, parlanti, lettura)

    // --- the cross-context readers (ADR 0030 §1): each consumer context's own port, built once here -------------

    val registrazionePerTrascrizione: LettoreRegistrazioneTrascrizione = LettoreRegistrazioneTrascrizione(catalogo)
    val registrazionePerParlanti: LettoreRegistrazioneParlanti = LettoreRegistrazioneParlanti(catalogo)
    val vociPerParlanti: LettoreVociDaTrascrizione = LettoreVociDaTrascrizione(vociDelTrascritto)
    val trascrittoPerDocumento: LettoreTrascrittoDocumento = LettoreTrascrittoDocumento(vociDelTrascritto, catalogo)
    val nomiPerDocumento: LettoreNomiDocumento = LettoreNomiDocumento(nomiDelleVoci)
    val trascrittoPerSintesi: LettoreTrascrittoSintesi = LettoreTrascrittoSintesi(vociDelTrascritto, statiElaborazione)
    val nomiPerSintesi: LettoreNomiSintesi = LettoreNomiSintesi(nomiDelleVoci)

    companion object {
        /** `SessioneProgettoSeams`' default: the production Registrazione repository, built through here only. */
        val registrazioniSql: (SnastroDatabase) -> RegistrazioneRepository = ::RegistrazioneRepositorySql
    }
}
