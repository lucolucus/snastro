package snastro.parlanti.applicazione.porte

import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId
import snastro.kernel.VoceRef

class LettoreVociFintaTest : LettoreVociContratto() {
    override fun ambiente(): AmbienteLettoreVoci = AmbienteFinto()

    /**
     * Plays the supplier: mints ids with the pinned keys (in a Parte, new Voci by first appearance, tie on
     * voceIndice, numbered from the Incontro's counter, never a removed one — [INV-I4]; SegmentoId per Parte by
     * inizio, Voce, fine) and hands the Finta a live view with the Voci in REVERSE voceId order, the intervalli by
     * DESCENDING inizio (ties by segmentoId) and the Segmenti of the Parti interleaved, so the Finta's own ordering is
     * what the contract checks — both for [LettoreVoci.voci] ([viste]) and [LettoreVoci.segmenti] ([dati]).
     */
    private class AmbienteFinto : AmbienteLettoreVoci {
        private class Seg(
            val ref: SegmentoRef,
            var voce: VoceId,
            val intervallo: IntervalloMs,
            var confermato: Boolean = false,
        )

        private val generatore = GeneratoreIdFinto()
        private val incontroDi = mutableMapOf<RegistrazioneId, IncontroId>()
        private val segmenti = mutableMapOf<IncontroId, MutableList<Seg>>()
        private val prossimaVoce = mutableMapOf<IncontroId, Int>()
        private val viste = mutableMapOf<IncontroId, List<VoceVista>>()
        private val dati = mutableMapOf<IncontroId, List<SegmentoDiVoce>>()

        override val lettore: LettoreVoci = LettoreVociFinta(viste, dati)

        override val piuPartiPerIncontro: Boolean = true

        override fun aggiungiRegistrazione(): RegistrazioneId = aggiungiParte(IncontroId(generatore.nuovo()))

        override fun aggiungiParte(incontroId: IncontroId): RegistrazioneId =
            RegistrazioneId(generatore.nuovo()).also { incontroDi[it] = incontroId }

        override fun completaElaborazione(
            registrazioneId: RegistrazioneId,
            turni: List<SemeTurno>,
        ): List<SegmentoConiato> {
            val incontro = incontroDi.getValue(registrazioneId)
            val dellIncontro = segmenti.getOrPut(incontro) { mutableListOf() }
            require(dellIncontro.none { it.ref.registrazioneId == registrazioneId } && turni.isNotEmpty())
            val base = prossimaVoce[incontro] ?: 1
            val voceDi = turni.groupBy { it.voceIndice }
                .mapValues { (_, suoi) -> suoi.minOf { it.intervallo.inizioMs } }
                .entries.sortedWith(compareBy({ it.value }, { it.key }))
                .mapIndexed { i, e -> e.key to VoceId(base + i) }
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
            dellIncontro += turni.zip(coniati) { t, c ->
                Seg(SegmentoRef(registrazioneId, c.segmentoId), c.voceId, t.intervallo)
            }
            prossimaVoce[incontro] = base + voceDi.size
            pubblica(incontro)
            return coniati
        }

        override fun incontroDi(registrazioneId: RegistrazioneId): IncontroId = incontroDi.getValue(registrazioneId)

        override fun fallisciElaborazione(registrazioneId: RegistrazioneId) {
            val incontro = incontroDi.getValue(registrazioneId)
            require(segmenti[incontro].orEmpty().none { it.ref.registrazioneId == registrazioneId })
        }

        override fun unisci(incontroId: IncontroId, sopravvive: VoceId, rimossa: VoceId) {
            val tutti = segmenti.getValue(incontroId)
            require(sopravvive != rimossa)
            require(tutti.any { it.voce == sopravvive } && tutti.any { it.voce == rimossa })
            tutti.filter { it.voce == rimossa }.forEach { it.voce = sopravvive }
            pubblica(incontroId)
        }

        override fun dividi(incontroId: IncontroId, origine: VoceId, segmenti: Set<SegmentoRef>): VoceId {
            val diOrigine = this.segmenti.getValue(incontroId).filter { it.voce == origine }
            require(segmenti.isNotEmpty() && segmenti.size < diOrigine.size)
            require(diOrigine.map { it.ref }.containsAll(segmenti))
            val nuova = nuovaVoce(incontroId)
            // [INV-26]: il sottoinsieme spostato diventa confermato; l'origine resta com'era.
            diOrigine.filter { it.ref in segmenti }.forEach {
                it.voce = nuova
                it.confermato = true
            }
            pubblica(incontroId)
            return nuova
        }

        override fun riassegna(segmento: SegmentoRef, destinazione: VoceId?): VoceId {
            val incontro = incontroDi.getValue(segmento.registrazioneId)
            val tutti = segmenti.getValue(incontro)
            val s = tutti.single { it.ref == segmento }
            require(destinazione != s.voce)
            val ammessa = if (destinazione != null) {
                tutti.any { it.voce == destinazione }
            } else {
                tutti.count { it.voce == s.voce } > 1
            }
            require(ammessa)
            s.voce = destinazione ?: nuovaVoce(incontro)
            s.confermato = true // [INV-26]: una RiassegnaSegmento manuale conferma il Segmento spostato.
            pubblica(incontro)
            return s.voce
        }

        override fun conferma(segmento: SegmentoRef) {
            val incontro = incontroDi.getValue(segmento.registrazioneId)
            segmenti.getValue(incontro).single { it.ref == segmento }.confermato = true
            pubblica(incontro)
        }

        private fun nuovaVoce(incontroId: IncontroId): VoceId =
            VoceId(prossimaVoce.getValue(incontroId)).also { prossimaVoce[incontroId] = it.numero + 1 }

        private val perInizioDecrescente = compareByDescending<Seg> { it.intervallo.inizioMs }

        private fun pubblica(incontroId: IncontroId) {
            val tutti = segmenti.getValue(incontroId)
            viste[incontroId] = tutti
                .groupBy { it.voce }
                .toSortedMap(compareByDescending { it.numero })
                .map { (voce, suoi) ->
                    VoceVista(
                        VoceRef(incontroId, voce),
                        suoi.groupBy { it.ref.registrazioneId }.mapValues { (_, diParte) ->
                            diParte.sortedWith(perInizioDecrescente.thenBy { it.ref.segmentoId.numero })
                                .map { it.intervallo }
                        },
                    )
                }
            dati[incontroId] = tutti
                .sortedWith(perInizioDecrescente.thenByDescending { it.ref.segmentoId.numero })
                .map { SegmentoDiVoce(it.ref, it.voce, it.intervallo, it.confermato) }
        }
    }
}
