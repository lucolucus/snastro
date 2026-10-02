package snastro.trascrizione.applicazione.letture

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione
import snastro.trascrizione.applicazione.porte.ParteDiIncontro
import snastro.trascrizione.applicazione.porte.RegistrazioneVista
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepository
import snastro.trascrizione.dominio.Trascritto
import snastro.trascrizione.dominio.VociDellIncontro

/**
 * Read-model `vista-parte`: builds [TrascrittoView] (the Trascrizione slice of S3, one Parte of an Incontro) and
 * [VociIncontro]. Read-only, no rule: order and labels come straight from the root (INV-7/INV-8), the Parte
 * numbers from [LettoreRegistrazione.parti], the open runs from [ElaborazioneRepository]. The root is read in ONE
 * `trova` snapshot (ADR 0029).
 */
public class TrascrittoQuery(
    private val trascritti: VociDellIncontroRepository,
    private val registrazioni: LettoreRegistrazione,
    private val elaborazioni: ElaborazioneRepository,
    private val numeroPersone: NumeroPersoneDellIncontro = NumeroPersoneDellIncontro(registrazioni, elaborazioni),
) {
    /** AC-167: the view of [registrazioneId]'s Parte; AC-168: `null` without a Trascritto (AC-I43, AC-I44). */
    public fun vista(registrazioneId: RegistrazioneId): TrascrittoView? =
        registrazioni.registrazione(registrazioneId)?.let { registrazione ->
            val parti = registrazioni.parti(registrazione.incontroId)
            val numero = parti?.firstOrNull { it.registrazioneId == registrazioneId }?.numero
            val radice = trascritti.trova(registrazione.incontroId)
            val trascritto = radice?.trascritto(registrazioneId)
            if (parti == null || numero == null || trascritto == null) {
                null
            } else {
                costruisci(registrazione, numero, parti, radice, trascritto)
            }
        }

    private fun costruisci(
        registrazione: RegistrazioneVista,
        numero: Int,
        parti: List<ParteDiIncontro>,
        radice: VociDellIncontro,
        trascritto: Trascritto,
    ): TrascrittoView {
        val numeri = parti.associate { it.registrazioneId to it.numero }
        val inCorso = parti.firstOrNull { p ->
            radice.haParte(p.registrazioneId) && elaborazioni.diRegistrazione(p.registrazioneId).any { it.aperta }
        }
        return TrascrittoView(
            registrazioneId = registrazione.registrazioneId,
            incontroId = registrazione.incontroId,
            titolo = registrazione.titolo,
            dataRegistrazione = registrazione.dataRegistrazione,
            durataMs = registrazione.durataMs,
            segmenti = trascritto.segmenti.map { s ->
                SegmentoTrascrittoView(
                    s.id,
                    s.voceId,
                    s.intervallo.inizioMs,
                    s.intervallo.fineMs,
                    s.testo,
                    s.confermato,
                )
            },
            voci = trascritto.voci.map { v ->
                val altre = radice.partiDi(v.id).filter { it != registrazione.registrazioneId }
                VoceTrascrittoView(v.id, "Voce ${v.id.numero}", altre.mapNotNull { numeri[it] }.sorted())
            },
            numeroParte = numero,
            parti = parti.map { ParteRef(it.registrazioneId, it.numero) },
            solaLettura = inCorso?.let { RitrascrizioneInCorso(it.numero.takeIf { parti.size > 1 }) },
        )
    }

    /** AC-I45: every Voce of [incontroId] with the Parti it speaks in, ascending by voceId; `null` without a root. */
    public fun vociIncontro(incontroId: IncontroId): VociIncontro? {
        val numeri = registrazioni.parti(incontroId)?.associate { it.registrazioneId to it.numero }
        val radice = trascritti.trova(incontroId)
        return if (numeri == null || radice == null) {
            null
        } else {
            val righe = radice.voci.sortedBy { it.numero }.map { voce ->
                VoceIncontroRiga(voce, "Voce ${voce.numero}", radice.partiDi(voce).mapNotNull { numeri[it] }.sorted())
            }
            VociIncontro(righe, righe.size)
        }
    }

    /** Pinned `numeroPersonePrecompilato(incontroId)`, delegated to [NumeroPersoneDellIncontro] (no second copy). */
    public fun numeroPersonePrecompilato(incontroId: IncontroId): Int? =
        numeroPersone.numeroPersonePrecompilato(incontroId)
}
