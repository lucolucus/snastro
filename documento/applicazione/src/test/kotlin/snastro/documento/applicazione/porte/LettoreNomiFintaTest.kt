package snastro.documento.applicazione.porte

import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import java.util.Locale

class LettoreNomiFintaTest : LettoreNomiContratto() {
    override fun ambiente(): AmbienteLettoreNomi = AmbienteFinto()

    /**
     * Plays the supplier: mints ids like it (UUID-like strings, VoceId 1..n) and refuses what the
     * Parlanti rules refuse (INV-13, INV-16, INV-17), so every seed the contract asks for is one the
     * real supplier accepts. The Finta reads the live maps.
     */
    private class AmbienteFinto : AmbienteLettoreNomi {
        private val generatore = GeneratoreIdFinto()
        private val voci = mutableSetOf<VoceRef>()
        private val attribuzioni = mutableMapOf<VoceRef, ParlanteId>()
        private val nomi = mutableMapOf<ParlanteId, String>()
        private val eliminati = mutableSetOf<ParlanteId>()

        override val lettore: LettoreNomi = LettoreNomiFinta(attribuzioni, nomi)

        override fun aggiungiRegistrazione(voci: Int): RegistrazioneConiata {
            require(voci >= 1)
            val id = RegistrazioneId(generatore.nuovo())
            val refs = (1..voci).map { VoceRef(id, VoceId(it)) }
            this.voci += refs
            return RegistrazioneConiata(id, refs)
        }

        override fun confermaNuovoParlante(voce: VoceRef, nome: String): ParlanteId {
            richiediNomeLibero(nome, escluso = null)
            val p = ParlanteId(generatore.nuovo())
            nomi[p] = nome
            conferma(voce, p)
            return p
        }

        override fun conferma(voce: VoceRef, parlante: ParlanteId) {
            require(voce in voci && parlante in nomi && parlante !in eliminati)
            attribuzioni[voce] = parlante
        }

        override fun rinomina(parlante: ParlanteId, nome: String) {
            require(parlante in nomi && parlante !in eliminati)
            richiediNomeLibero(nome, escluso = parlante)
            nomi[parlante] = nome
        }

        override fun elimina(parlante: ParlanteId) {
            require(parlante in nomi && eliminati.add(parlante))
        }

        private fun richiediNomeLibero(nome: String, escluso: ParlanteId?) {
            val chiave = nome.trim().lowercase(Locale.ROOT)
            require(
                nomi.none { (p, n) -> p != escluso && p !in eliminati && n.trim().lowercase(Locale.ROOT) == chiave },
            ) { "Nome gia in uso: $nome" }
        }
    }
}
