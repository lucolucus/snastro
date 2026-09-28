package snastro.avvio.porte

import snastro.kernel.LetturaCoerente
import snastro.persistenza.SnastroDatabase
import snastro.progetto.adattatori.persistenza.ProgettoRepositorySql
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.trascrizione.adattatori.persistenza.ElaborazioneRepositorySql
import snastro.trascrizione.adattatori.persistenza.TrascrittoRepositorySql
import snastro.trascrizione.applicazione.letture.FasiInCorso
import snastro.trascrizione.applicazione.letture.StatiElaborazione

/**
 * Every Progetto/Trascrizione SQL repository of ONE open project — the two contexts EVERY R-level needs,
 * R1 included (ADR 0030 §1, AC-C60/AC-C61): built EXACTLY ONCE by `SessioneProgettoImpl`
 * (`avvio-composizione`) and handed to R1/R2/R3 through `ContestoEstensione.porte`, which stop building
 * their own copy (`TrascrittoRepositorySql` alone was built 5 times per open project before this: once
 * each in R1, R2 (twice: its own `trascritti` and its `PorteParlanti`), R2's `puliziaDerivati`, and R3).
 *
 * Deliberately narrower than ADR 0030 §1's final target shape: it does NOT hold Parlanti's or Sintesi's
 * repositories. Those stay owned by their first consumer (`EstensioneR2`/`EstensioneR3` respectively) and
 * reused (===) by whichever wraps it (R3 reuses R2's) — putting them here would make `snastro.parlanti`/
 * `snastro.sintesi` appear in a file OUTSIDE `r1`/`r2`/`r3`, breaking `CablaggioR1Test`'s AC-356 (no
 * Parlanti class referenced by the R1 composition: an R1-only open project must never construct one) and
 * the equivalent Sintesi-purity expectation for R1/R2 (`ComposizioneR3Test`'s AC-S143). c3
 * (`composizione-piatta`) retires this asymmetry when the R-structure itself is flattened.
 *
 * [statiElaborazione] (over [fasiInCorso]) is the ONE `StatiElaborazione` of the open project (AC-C63):
 * R1's pipeline ([snastro.avvio.r1.SegnalatoreFaseConCambiamenti]) writes into [fasiInCorso], `S2`
 * (`CollaboratoriR1.statiElaborazione`) reads it, and Sintesi's cross-context
 * `LettoreTrascrittoDaTrascrizione` reads the SAME instance — never a second, unwritten `FasiInCorso()`
 * whose `IN_CORSO` phase Sintesi could never actually see.
 *
 * Its own package sits outside `r1`/`r2`/`r3` (it is common to all three, built before any of them runs) —
 * `GrafoR0Test`'s import guard names it explicitly alongside them.
 */
internal class PorteProgetto(
    database: SnastroDatabase,
    lettura: LetturaCoerente,
    registrazioni: RegistrazioneRepository,
) {
    val progetti: ProgettoRepositorySql = ProgettoRepositorySql(database)
    val catalogo: CatalogoRegistrazioni = CatalogoRegistrazioni(registrazioni)
    val trascritti: TrascrittoRepositorySql = TrascrittoRepositorySql(database, lettura)
    val elaborazioni: ElaborazioneRepositorySql = ElaborazioneRepositorySql(database)

    /** AC-C63: written by R1's pipeline, read by [statiElaborazione] — shared with Sintesi, never rebuilt. */
    val fasiInCorso: FasiInCorso = FasiInCorso()

    /** AC-C63: the project's ONE `StatiElaborazione` — S2 (R1) and Sintesi (R3) read this SAME instance. */
    val statiElaborazione: StatiElaborazione = StatiElaborazione(elaborazioni, trascritti, fasiInCorso)
}
