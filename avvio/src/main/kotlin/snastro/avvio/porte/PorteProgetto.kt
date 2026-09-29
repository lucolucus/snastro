package snastro.avvio.porte

import snastro.kernel.LetturaCoerente
import snastro.persistenza.SnastroDatabase
import snastro.progetto.adattatori.persistenza.EliminazioniInSospesoSql
import snastro.progetto.adattatori.persistenza.ProgettoRepositorySql
import snastro.progetto.adattatori.persistenza.RegistrazioneRepositorySql
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.trascrizione.adattatori.persistenza.ElaborazioneRepositorySql
import snastro.trascrizione.adattatori.persistenza.TrascrittoRepositorySql
import snastro.trascrizione.applicazione.letture.FasiInCorso
import snastro.trascrizione.applicazione.letture.StatiElaborazione
import java.time.Clock

/**
 * The ports of ONE open project (ADR 0030 §1, AC-C60/AC-C61): built EXACTLY ONCE per `crea`/`apri` by
 * `SessioneProgettoImpl` and handed to R1/R2/R3 through `ContestoEstensione.porte`. Nothing else in `:avvio` builds
 * an SQL repository: every consumer of R0/R1/R2/R3 receives the instance held here.
 *
 * - Progetto and Trascrizione (what every level, R1 included, needs): the fields below.
 * - Parlanti and Sintesi: the per-context parts `PorteProgettoParlanti` (`snastro.avvio.r2`) and
 *   `PorteProgettoSintesi` (`snastro.avvio.r3`), which this object OWNS through [parte]: built on first use, at
 *   most once per open project, then the same instance for every later caller (R1's Documento names, R2 and R3
 *   alike). They are declared in their own composition package because an R1-only project must never construct a
 *   Parlanti class, nor may this package name one (`CablaggioR1Test` AC-356, `GrafoR0Test` AC-350).
 *
 * [statiElaborazione] (over [fasiInCorso]) is the ONE `StatiElaborazione` of the open project (AC-C63): R1's
 * pipeline writes into [fasiInCorso], S2 reads it, and Sintesi's `LettoreTrascrittoDaTrascrizione` reads the SAME
 * instance, never a second, unwritten `FasiInCorso()`.
 */
internal class PorteProgetto(
    val database: SnastroDatabase,
    /** ADR 0029 §3: the SAME `UnitaDiLavoroSql` the project's dispatcher delegates to. */
    val lettura: LetturaCoerente,
    clock: Clock,
    costruisciRegistrazioni: (SnastroDatabase) -> RegistrazioneRepository = registrazioniSql,
) {
    val registrazioni: RegistrazioneRepository = costruisciRegistrazioni(database)
    val progetti: ProgettoRepositorySql = ProgettoRepositorySql(database)
    val eliminazioniInSospeso: EliminazioniInSospesoSql = EliminazioniInSospesoSql(database, clock)
    val catalogo: CatalogoRegistrazioni = CatalogoRegistrazioni(registrazioni)
    val trascritti: TrascrittoRepositorySql = TrascrittoRepositorySql(database, lettura)
    val elaborazioni: ElaborazioneRepositorySql = ElaborazioneRepositorySql(database)

    /** AC-C63: written by R1's pipeline, read by [statiElaborazione]; shared with Sintesi, never rebuilt. */
    val fasiInCorso: FasiInCorso = FasiInCorso()

    /** AC-C63: the project's ONE `StatiElaborazione`: S2 (R1) and Sintesi (R3) read this SAME instance. */
    val statiElaborazione: StatiElaborazione = StatiElaborazione(elaborazioni, trascritti, fasiInCorso)

    private val parti = HashMap<Class<*>, Any>()

    /**
     * The per-context part of type [tipo] of this project: [costruisci] runs on the FIRST request only; every
     * later request returns that same instance (AC-C60/AC-C61).
     */
    @Synchronized
    fun <P : Any> parte(tipo: Class<P>, costruisci: (PorteProgetto) -> P): P =
        tipo.cast(parti.getOrPut(tipo) { costruisci(this) })

    companion object {
        /** `SessioneProgettoSeams`' default: the production Registrazione repository, built through here only. */
        val registrazioniSql: (SnastroDatabase) -> RegistrazioneRepository = ::RegistrazioneRepositorySql
    }
}
