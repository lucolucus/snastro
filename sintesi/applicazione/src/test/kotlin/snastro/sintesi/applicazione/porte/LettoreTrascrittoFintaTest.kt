package snastro.sintesi.applicazione.porte

import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/** AC-S7 (D1): [LettoreTrascrittoFinta] passes [LettoreTrascrittoContratto] in the gate. */
class LettoreTrascrittoFintaTest : LettoreTrascrittoContratto() {
    override fun ambiente(): AmbienteLettoreTrascritto = AmbienteFinto()

    /**
     * Plays the supplier: keeps each Registrazione's Elaborazioni as a stack of states (the top is the
     * latest), mints ids like the Trascritto (VoceId by first appearance, tie on voceIndice; SegmentoId by
     * inizio then Voce; a replacement renumbers from 1; a new Voce takes the next number) and hands the
     * segmenti to the Finta in SEEDING order, so the Finta's own ordering is what the contract checks.
     */
    private class AmbienteFinto : AmbienteLettoreTrascritto {
        private val generatore = GeneratoreIdFinto()
        private val elaborazioni = mutableMapOf<RegistrazioneId, MutableList<Stato>>()
        private val trascritti = mutableMapOf<RegistrazioneId, MutableList<SegmentoSintesi>>()
        private val prossimaVoce = mutableMapOf<RegistrazioneId, Int>()

        override val lettore: LettoreTrascritto
            get() = LettoreTrascrittoFinta(
                trascritti.mapValues { it.value.toList() },
                elaborazioni.filterValues { it.lastOrNull() in APERTI }.keys,
            )

        override fun aggiungiRegistrazione(): RegistrazioneId =
            RegistrazioneId(generatore.nuovo()).also { elaborazioni[it] = mutableListOf() }

        override fun accodaElaborazione(r: RegistrazioneId) {
            val stati = elaborazioni.getValue(r)
            require(stati.lastOrNull() !in APERTI)
            stati += Stato.IN_ATTESA
        }

        override fun avviaElaborazione(r: RegistrazioneId) = passa(r, setOf(Stato.IN_ATTESA), Stato.IN_CORSO)

        override fun completaElaborazione(r: RegistrazioneId, turni: List<SemeTurno>): List<SegmentoConiato> {
            require(turni.isNotEmpty())
            passa(r, APERTI, Stato.COMPLETATA)
            val voceDi = turni.groupBy { it.voceIndice }
                .mapValues { (_, suoi) -> suoi.minOf { it.intervallo.inizioMs } }
                .entries.sortedWith(compareBy({ it.value }, { it.key }))
                .mapIndexed { i, e -> e.key to VoceId(i + 1) }
                .toMap()
            val ordine = turni.indices.sortedWith(
                compareBy({ turni[it].intervallo.inizioMs }, { voceDi.getValue(turni[it].voceIndice).numero }),
            )
            val coniati = turni.indices.map { i ->
                SegmentoConiato(SegmentoId(ordine.indexOf(i) + 1), voceDi.getValue(turni[i].voceIndice))
            }
            trascritti[r] = turni
                .zip(coniati) { t, c -> SegmentoSintesi(c.segmentoId, c.voceId, t.intervallo, t.testo) }
                .toMutableList()
            prossimaVoce[r] = voceDi.size + 1
            return coniati
        }

        override fun fallisciElaborazione(r: RegistrazioneId) = passa(r, APERTI, Stato.FALLITA)

        override fun annullaElaborazione(r: RegistrazioneId) {
            val stati = elaborazioni.getValue(r)
            require(stati.lastOrNull() == Stato.IN_ATTESA)
            stati.removeAt(stati.lastIndex)
        }

        override fun riassegna(r: RegistrazioneId, segmento: SegmentoId, destinazione: VoceId?): VoceId {
            val segmenti = trascritti.getValue(r)
            val i = segmenti.indexOfFirst { it.segmentoId == segmento }
            val voce = destinazione ?: VoceId(prossimaVoce.getValue(r)).also { prossimaVoce[r] = it.numero + 1 }
            segmenti[i] = segmenti[i].copy(voceId = voce)
            return voce
        }

        private fun passa(r: RegistrazioneId, da: Set<Stato>, a: Stato) {
            val stati = elaborazioni.getValue(r)
            require(stati.lastOrNull() in da) { "ultima Elaborazione ${stati.lastOrNull()}, attesa una di $da" }
            stati[stati.lastIndex] = a
        }
    }

    private enum class Stato { IN_ATTESA, IN_CORSO, COMPLETATA, FALLITA }

    private companion object {
        val APERTI = setOf(Stato.IN_ATTESA, Stato.IN_CORSO)
    }
}
