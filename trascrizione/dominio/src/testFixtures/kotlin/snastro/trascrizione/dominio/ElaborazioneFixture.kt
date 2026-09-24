package snastro.trascrizione.dominio

import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import java.time.Instant

/** Fixed instants for deterministic tests (no wall clock, CR-14). */
public val CREATA_ALLE: Instant = Instant.parse("2026-09-23T10:00:00Z")
public val AVVIATA_ALLE: Instant = Instant.parse("2026-09-23T10:05:00Z")

/**
 * An [Elaborazione] brought to [stato] through its own transitions (never via `ricostituisci`,
 * which only persistence adapters may call — CR-15).
 */
public fun unaElaborazione(
    stato: StatoElaborazione = StatoElaborazione.IN_ATTESA,
    id: ElaborazioneId = ElaborazioneId("id-1"),
    registrazioneId: RegistrazioneId = RegistrazioneId("id-2"),
    creataAlle: Instant = CREATA_ALLE,
    avviataAlle: Instant = AVVIATA_ALLE,
    motivo: String = "Il file audio non si puo leggere",
    numeroPersone: NumeroPersone? = null,
): Elaborazione {
    val elaborazione = Elaborazione.accoda(id, registrazioneId, creataAlle, numeroPersone).aggregato
    if (stato == StatoElaborazione.IN_ATTESA) return elaborazione
    check(elaborazione.avvia(avviataAlle) is Esito.Ok)
    when (stato) {
        StatoElaborazione.IN_ATTESA, StatoElaborazione.IN_CORSO -> Unit
        StatoElaborazione.COMPLETATA -> check(elaborazione.completa() is Esito.Ok)
        StatoElaborazione.FALLITA -> check(elaborazione.fallisci(motivo) is Esito.Ok)
    }
    return elaborazione
}
