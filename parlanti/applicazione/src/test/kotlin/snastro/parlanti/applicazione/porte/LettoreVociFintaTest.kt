package snastro.parlanti.applicazione.porte

import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef

class LettoreVociFintaTest : LettoreVociContratto() {
    override fun ambiente(): AmbienteLettoreVoci = AmbienteFinto()

    /**
     * Plays the supplier: mints ids with the pinned keys (VoceId 1..n by first appearance, tie on
     * voceIndice; SegmentoId by inizio, Voce, fine; a new Voce takes the next number, never a removed
     * one) and hands the Finta a live view with the Voci in REVERSE voceId order and the intervalli by
     * DESCENDING inizio (ties by segmentoId), so the Finta's own ordering is what the contract checks —
     * both for [LettoreVoci.voci] ([viste]) and [LettoreVoci.segmenti] ([dati]).
     */
    private class AmbienteFinto : AmbienteLettoreVoci {
        private class Seg(
            val id: SegmentoId,
            var voce: VoceId,
            val intervallo: IntervalloMs,
            var confermato: Boolean = false,
        )

        private val generatore = GeneratoreIdFinto()
        private val registrazioni = mutableSetOf<RegistrazioneId>()
        private val trascritti = mutableMapOf<RegistrazioneId, List<Seg>>()
        private val prossimaVoce = mutableMapOf<RegistrazioneId, Int>()
        private val viste = mutableMapOf<RegistrazioneId, List<VoceVista>>()
        private val dati = mutableMapOf<RegistrazioneId, List<SegmentoDiVoce>>()

        override val lettore: LettoreVoci = LettoreVociFinta(viste, dati)

        override fun aggiungiRegistrazione(): RegistrazioneId =
            RegistrazioneId(generatore.nuovo()).also { registrazioni += it }

        override fun completaElaborazione(
            registrazioneId: RegistrazioneId,
            turni: List<SemeTurno>,
        ): List<SegmentoConiato> {
            require(registrazioneId in registrazioni && registrazioneId !in trascritti && turni.isNotEmpty())
            val voceDi = turni.groupBy { it.voceIndice }
                .mapValues { (_, suoi) -> suoi.minOf { it.intervallo.inizioMs } }
                .entries.sortedWith(compareBy({ it.value }, { it.key }))
                .mapIndexed { i, e -> e.key to VoceId(i + 1) }
                .toMap()
            val ordine = turni.indices.sortedWith(
                compareBy(
                    { turni[it].intervallo.inizioMs },
                    { voceDi.getValue(turni[it].voceIndice).numero },
                    { turni[it].intervallo.fineMs },
                ),
            )
            val coniati = turni.indices.map { i ->
                SegmentoConiato(SegmentoId(ordine.indexOf(i) + 1), voceDi.getValue(turni[i].voceIndice))
            }
            trascritti[registrazioneId] = turni.zip(coniati) { t, c -> Seg(c.segmentoId, c.voceId, t.intervallo) }
            prossimaVoce[registrazioneId] = voceDi.size + 1
            pubblica(registrazioneId)
            return coniati
        }

        override fun fallisciElaborazione(registrazioneId: RegistrazioneId) {
            require(registrazioneId in registrazioni && registrazioneId !in trascritti)
        }

        override fun unisci(registrazioneId: RegistrazioneId, sopravvive: VoceId, rimossa: VoceId) {
            val segmenti = trascritti.getValue(registrazioneId)
            require(sopravvive != rimossa)
            require(segmenti.any { it.voce == sopravvive } && segmenti.any { it.voce == rimossa })
            segmenti.filter { it.voce == rimossa }.forEach { it.voce = sopravvive }
            pubblica(registrazioneId)
        }

        override fun dividi(registrazioneId: RegistrazioneId, origine: VoceId, segmenti: Set<SegmentoId>): VoceId {
            val diOrigine = trascritti.getValue(registrazioneId).filter { it.voce == origine }
            require(segmenti.isNotEmpty() && segmenti.size < diOrigine.size)
            require(diOrigine.map { it.id }.containsAll(segmenti))
            val nuova = nuovaVoce(registrazioneId)
            // [INV-26]: il sottoinsieme spostato diventa confermato; l'origine resta com'era.
            diOrigine.filter { it.id in segmenti }.forEach {
                it.voce = nuova
                it.confermato = true
            }
            pubblica(registrazioneId)
            return nuova
        }

        override fun riassegna(registrazioneId: RegistrazioneId, segmento: SegmentoId, destinazione: VoceId?): VoceId {
            val segmenti = trascritti.getValue(registrazioneId)
            val s = segmenti.single { it.id == segmento }
            require(destinazione != s.voce)
            val ammessa = if (destinazione != null) {
                segmenti.any { it.voce == destinazione }
            } else {
                segmenti.count { it.voce == s.voce } > 1
            }
            require(ammessa)
            s.voce = destinazione ?: nuovaVoce(registrazioneId)
            s.confermato = true // [INV-26]: una RiassegnaSegmento manuale conferma il Segmento spostato.
            pubblica(registrazioneId)
            return s.voce
        }

        override fun conferma(registrazioneId: RegistrazioneId, segmento: SegmentoId) {
            trascritti.getValue(registrazioneId).single { it.id == segmento }.confermato = true
            pubblica(registrazioneId)
        }

        private fun nuovaVoce(registrazioneId: RegistrazioneId): VoceId =
            VoceId(prossimaVoce.getValue(registrazioneId)).also { prossimaVoce[registrazioneId] = it.numero + 1 }

        private fun pubblica(registrazioneId: RegistrazioneId) {
            val segmenti = trascritti.getValue(registrazioneId)
            viste[registrazioneId] = segmenti
                .groupBy { it.voce }
                .toSortedMap(compareByDescending { it.numero })
                .map { (voce, suoi) ->
                    VoceVista(
                        VoceRef(registrazioneId, voce),
                        suoi.sortedWith(compareByDescending<Seg> { it.intervallo.inizioMs }.thenBy { it.id.numero })
                            .map { it.intervallo },
                    )
                }
            dati[registrazioneId] = segmenti
                .sortedWith(compareByDescending<Seg> { it.intervallo.inizioMs }.thenByDescending { it.id.numero })
                .map { SegmentoDiVoce(it.id, it.voce, it.intervallo, it.confermato) }
        }
    }
}
