package snastro.trascrizione.dominio

import snastro.kernel.Creato
import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.trascrizione.dominio.ErroreTrascrizione.TransizioneNonAmmessa
import snastro.trascrizione.dominio.StatoElaborazione.COMPLETATA
import snastro.trascrizione.dominio.StatoElaborazione.FALLITA
import snastro.trascrizione.dominio.StatoElaborazione.IN_ATTESA
import snastro.trascrizione.dominio.StatoElaborazione.IN_CORSO
import java.time.Instant

/**
 * One run of the local pipeline on a `Registrazione`. Owns INV-3: [stato] moves only
 * `in_attesa → in_corso → completata | fallita`; every other move is [TransizioneNonAmmessa] and
 * leaves the state unchanged. The per-Registrazione set rule (INV-4) is not checked here (ADR 0007).
 */
public class Elaborazione private constructor(
    public val id: ElaborazioneId,
    public val registrazioneId: RegistrazioneId,
    public val creataAlle: Instant,
    stato: StatoElaborazione,
    avviataAlle: Instant?,
    motivoFallimento: String?,
) {
    // Backing fields, not `public var … private set`: CR-4's Konsist rule bans any public var in dominio.
    private var _stato = stato
    private var _avviataAlle = avviataAlle
    private var _motivoFallimento = motivoFallimento

    public val stato: StatoElaborazione get() = _stato
    public val avviataAlle: Instant? get() = _avviataAlle
    public val motivoFallimento: String? get() = _motivoFallimento

    /** `in_attesa` or `in_corso`. */
    public val aperta: Boolean get() = stato == IN_ATTESA || stato == IN_CORSO
    public val completata: Boolean get() = stato == COMPLETATA
    public val fallita: Boolean get() = stato == FALLITA

    /** `completata` or `fallita`: no further transition. */
    public val terminale: Boolean get() = !aperta

    public fun avvia(alle: Instant): Esito<ElaborazioneAvviata> =
        transizione(da = IN_ATTESA, verso = IN_CORSO) {
            _avviataAlle = alle
            ElaborazioneAvviata(id, registrazioneId, alle)
        }

    public fun completa(): Esito<ElaborazioneCompletata> =
        transizione(da = IN_CORSO, verso = COMPLETATA) { ElaborazioneCompletata(id, registrazioneId) }

    public fun fallisci(motivo: String): Esito<ElaborazioneFallita> =
        transizione(da = IN_CORSO, verso = FALLITA) {
            _motivoFallimento = motivo
            ElaborazioneFallita(id, registrazioneId, motivo)
        }

    private inline fun <E> transizione(
        da: StatoElaborazione,
        verso: StatoElaborazione,
        effetto: () -> E,
    ): Esito<E> {
        if (stato != da) return Esito.Errore(TransizioneNonAmmessa(id, stato, verso))
        val evento = effetto()
        _stato = verso
        return Esito.Ok(evento)
    }

    public companion object {
        public fun accoda(
            id: ElaborazioneId,
            registrazioneId: RegistrazioneId,
            creataAlle: Instant,
        ): Creato<Elaborazione, ElaborazioneAccodata> = Creato(
            Elaborazione(id, registrazioneId, creataAlle, IN_ATTESA, avviataAlle = null, motivoFallimento = null),
            ElaborazioneAccodata(id, registrazioneId, creataAlle),
        )

        /** Rebuilds from persisted state; re-validates nothing (the DB is trusted). */
        @RicostituzioneDaPersistenza
        public fun ricostituisci(
            id: ElaborazioneId,
            registrazioneId: RegistrazioneId,
            creataAlle: Instant,
            stato: StatoElaborazione,
            avviataAlle: Instant?,
            motivoFallimento: String?,
        ): Elaborazione = Elaborazione(id, registrazioneId, creataAlle, stato, avviataAlle, motivoFallimento)
    }
}
