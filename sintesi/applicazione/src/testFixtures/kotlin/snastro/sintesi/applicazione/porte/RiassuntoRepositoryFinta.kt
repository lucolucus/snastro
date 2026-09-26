package snastro.sintesi.applicazione.porte

import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.Ripristinabile
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.sintesi.dominio.BozzaElemento
import snastro.sintesi.dominio.BozzaRiassunto
import snastro.sintesi.dominio.ErroreSintesi
import snastro.sintesi.dominio.Riassunto
import snastro.sintesi.dominio.RiassuntoId
import snastro.sintesi.dominio.StrutturaTrascritto

/**
 * In-memory [RiassuntoRepository], a stand-in for the `riassunto*` tables of ADR 0022:
 * - refuses like the partial unique indexes `riassunto_non_pronto_unico` (→ `RiassuntoGiaAperto`) and
 *   `riassunto_pronto_unico` (→ the same error: the only Sintesi error for a per-Registrazione collision);
 * - [concludi] is the compare-and-set of ADR 0022 §4 (a `pronto` replaces the previous `pronto`);
 * - stores immutable rows and rebuilds a fresh [Riassunto] through its own transitions on every read
 *   (`ricostituisci` is reserved to persistence adapters, CR-15), so no caller aliases the stored state.
 *
 * [Ripristinabile]: pass it to `UnitaDiLavoroFinta`. Reads see the last committed map from any thread (`@Volatile`,
 * copy-on-write); writes are serialised.
 */
public class RiassuntoRepositoryFinta : RiassuntoRepository, Ripristinabile {
    @Volatile
    private var righe: Map<RiassuntoId, Riga> = emptyMap()

    override fun trova(id: RiassuntoId): Riassunto? = righe[id]?.inDominio()

    override fun diRegistrazione(r: RegistrazioneId): List<Riassunto> =
        righe.values.filter { it.registrazioneId == r }.map { it.inDominio() }

    override fun inAttesa(): List<Riassunto> =
        righe.values.filter { it.inAttesa }.sortedWith(compareBy({ it.richiestoAlle }, { it.id.valore }))
            .map { it.inDominio() }

    override fun inCorso(): List<Riassunto> = righe.values.filter { it.inCorso }.map { it.inDominio() }

    @Synchronized
    override fun salva(r: Riassunto): Esito<Unit> {
        val altre = righe.values.filter { it.registrazioneId == r.registrazioneId && it.id != r.id }
        if (altre.any { it.pronto == r.pronto }) return Esito.Errore(ErroreSintesi.RiassuntoGiaAperto(r.registrazioneId))
        righe = righe + (r.id to Riga(r))
        return Esito.Ok(Unit)
    }

    @Synchronized
    override fun concludi(r: Riassunto): Esito<Boolean> {
        require(r.pronto || r.fallito) { "concludi di un Riassunto non concluso: ${r.id}" }
        if (righe[r.id]?.inCorso != true) return Esito.Ok(false)
        val senzaPrecedente =
            if (r.pronto) righe.filterValues { !(it.pronto && it.registrazioneId == r.registrazioneId) } else righe
        righe = senzaPrecedente + (r.id to Riga(r))
        return Esito.Ok(true)
    }

    @Synchronized
    override fun rimuovi(id: RiassuntoId): Esito<Unit> {
        righe = righe - id
        return Esito.Ok(Unit)
    }

    @Synchronized
    override fun rimuoviDiRegistrazione(r: RegistrazioneId): Esito<Int> {
        val prima = righe.size
        righe = righe.filterValues { it.registrazioneId != r }
        return Esito.Ok(prima - righe.size)
    }

    override fun istantanea(): () -> Unit {
        val salvate = righe
        return { righe = salvate }
    }

    /** The persisted fields of one `riassunto` row and its `riassunto_elemento` / `riassunto_fonte` children. */
    private class Riga(r: Riassunto) {
        val id = r.id
        val registrazioneId = r.registrazioneId
        val argomento = r.argomento?.valore
        val parole = r.lunghezzaMassima.valore
        val richiestoAlle = r.richiestoAlle
        val avviatoAlle = r.avviatoAlle
        val motivo = r.motivoFallimento
        val inAttesa = r.inAttesa
        val inCorso = r.inCorso
        val pronto = r.pronto
        val sommario = r.sommario
        val decisioni = r.decisioni.toList()
        val questioniAperte = r.questioniAperte.toList()
        val azioni = r.azioni.toList()
        val puntiChiave = r.puntiChiave.toList()
        val omessi = r.omessi
        val struttura = r.struttura

        fun inDominio(): Riassunto {
            val riassunto = unRiassunto(id.valore, registrazioneId, argomento, parole, richiestoAlle)
            if (inAttesa) return riassunto
            riassunto.conAvvio(checkNotNull(avviatoAlle))
            return when {
                motivo != null -> riassunto.conFallimento(motivo)
                struttura != null -> riassunto.conCompletamento(bozza(), strutturaDa(struttura))
                    .also { check(it.omessi == omessi) { "omessi non riprodotti per $id" } }
                else -> riassunto
            }
        }

        /** The already-verified content as a draft the Verifica keeps whole, padded to reproduce [omessi]. */
        private fun bozza(): BozzaRiassunto = BozzaRiassunto(
            sommario = sommario?.testo?.codifica(),
            decisioni = decisioni.map { elemento(it.testo.codifica(), it.fonti, null) } +
                List(checkNotNull(omessi)) { BozzaElemento("omesso", emptyList(), null) },
            questioniAperte = questioniAperte.map { elemento(it.testo.codifica(), it.fonti, null) },
            azioni = azioni.map { elemento(it.testo.codifica(), it.fonti, it.responsabile) },
            puntiChiave = puntiChiave.map { elemento(it.testo.codifica(), it.fonti, it.parlante) },
        )

        private fun elemento(testo: String, fonti: Set<SegmentoId>, voce: VoceId?): BozzaElemento =
            BozzaElemento(testo, fonti.map { it.numero }, voce?.numero)

        private fun strutturaDa(chiave: String): StrutturaTrascritto = StrutturaTrascritto.di(
            chiave.split(',').filter { it.isNotEmpty() }.map { coppia ->
                val (segmento, voce) = coppia.split(':')
                SegmentoId(segmento.toInt()) to VoceId(voce.toInt())
            },
        )
    }
}
