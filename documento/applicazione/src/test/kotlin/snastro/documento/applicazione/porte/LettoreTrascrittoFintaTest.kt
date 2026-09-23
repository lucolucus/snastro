package snastro.documento.applicazione.porte

import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

class LettoreTrascrittoFintaTest : LettoreTrascrittoContratto() {
    override fun ambiente(): AmbienteLettoreTrascritto = AmbienteFinto()

    /**
     * Plays the supplier: mints ids with the pinned keys (VoceId by first appearance, tie on voceIndice;
     * SegmentoId by inizio then Voce; a new Voce takes the next number) and hands the segmenti to the
     * Finta in seeding order, so the Finta's own ordering is what the contract checks.
     */
    private class AmbienteFinto : AmbienteLettoreTrascritto {
        private val generatore = GeneratoreIdFinto()
        private val registrazioni = mutableMapOf<RegistrazioneId, SemeRegistrazione>()
        private val trascritti = mutableMapOf<RegistrazioneId, MutableList<SegmentoVista>>()
        private val prossimaVoce = mutableMapOf<RegistrazioneId, Int>()

        override val lettore: LettoreTrascritto
            get() = LettoreTrascrittoFinta(
                trascritti.mapValues { (id, segmenti) ->
                    val r = registrazioni.getValue(id)
                    TrascrittoTesto(id, r.titolo, r.dataRegistrazione, segmenti.toList())
                },
            )

        override fun aggiungiRegistrazione(seme: SemeRegistrazione): RegistrazioneId =
            RegistrazioneId(generatore.nuovo()).also { registrazioni[it] = seme }

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
                compareBy({ turni[it].intervallo.inizioMs }, { voceDi.getValue(turni[it].voceIndice).numero }),
            )
            val coniati = turni.indices.map { i ->
                SegmentoConiato(SegmentoId(ordine.indexOf(i) + 1), voceDi.getValue(turni[i].voceIndice))
            }
            trascritti[registrazioneId] = turni
                .zip(coniati) { t, c -> SegmentoVista(c.segmentoId, c.voceId, t.intervallo, t.testo) }
                .toMutableList()
            prossimaVoce[registrazioneId] = voceDi.size + 1
            return coniati
        }

        override fun fallisciElaborazione(registrazioneId: RegistrazioneId) {
            require(registrazioneId in registrazioni && registrazioneId !in trascritti)
        }

        override fun riassegna(
            registrazioneId: RegistrazioneId,
            segmento: SegmentoId,
            destinazione: VoceId?,
        ): VoceId {
            val segmenti = trascritti.getValue(registrazioneId)
            val i = segmenti.indexOfFirst { it.segmentoId == segmento }
            val voce = destinazione ?: VoceId(prossimaVoce.getValue(registrazioneId)).also {
                prossimaVoce[registrazioneId] = it.numero + 1
            }
            segmenti[i] = segmenti[i].copy(voceId = voce)
            return voce
        }
    }
}
