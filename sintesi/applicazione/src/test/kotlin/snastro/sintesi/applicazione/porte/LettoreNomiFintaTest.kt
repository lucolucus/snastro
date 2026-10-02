package snastro.sintesi.applicazione.porte

import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import java.util.Locale

/** AC-S10 (D1): [LettoreNomiFinta] passes [LettoreNomiContratto] in the gate. */
class LettoreNomiFintaTest : LettoreNomiContratto() {
    override fun ambiente(): AmbienteLettoreNomi = AmbienteFinto()

    /**
     * Plays the supplier: mints ids like it and refuses what the Parlanti rules refuse, so every seed
     * the contract asks for is one the real supplier accepts. The Finta reads the live maps.
     */
    private class AmbienteFinto : AmbienteLettoreNomi {
        private val generatore = GeneratoreIdFinto()
        private val voci = mutableSetOf<VoceRef>()
        private val attribuzioni = mutableMapOf<VoceRef, String>()
        private val nomi = mutableMapOf<String, String>()
        private val eliminati = mutableSetOf<String>()

        override val lettore: LettoreNomi = LettoreNomiFinta(attribuzioni, nomi)

        override fun aggiungiRegistrazione(voci: Int): RegistrazioneSeminata =
            aggiungi(IncontroId(generatore.nuovo()), voci)

        override fun aggiungiParte(incontroId: IncontroId, voci: Int): RegistrazioneSeminata {
            require(this.voci.any { it.incontroId == incontroId }) { "Incontro sconosciuto: $incontroId" }
            return aggiungi(incontroId, voci)
        }

        /** The Voci of a new Parte are numbered after the Incontro's existing ones (INV-I4). */
        private fun aggiungi(incontroId: IncontroId, voci: Int): RegistrazioneSeminata {
            require(voci >= 1)
            val prima = this.voci.count { it.incontroId == incontroId } + 1
            val refs = (prima until prima + voci).map { VoceRef(incontroId, VoceId(it)) }
            this.voci += refs
            return RegistrazioneSeminata(RegistrazioneId(generatore.nuovo()), incontroId, refs)
        }

        override fun attribuisciANuovo(voce: VoceRef, nome: String): ParlanteSeminato {
            richiediNomeLibero(nome, escluso = null)
            val p = ParlanteSeminato(generatore.nuovo())
            nomi[p.chiave] = nome
            attribuisci(voce, p)
            return p
        }

        override fun attribuisci(voce: VoceRef, parlante: ParlanteSeminato) {
            require(voce in voci && parlante.chiave in nomi && parlante.chiave !in eliminati)
            attribuzioni[voce] = parlante.chiave
        }

        override fun rinomina(parlante: ParlanteSeminato, nome: String) {
            require(parlante.chiave in nomi && parlante.chiave !in eliminati)
            richiediNomeLibero(nome, escluso = parlante.chiave)
            nomi[parlante.chiave] = nome
        }

        override fun elimina(parlante: ParlanteSeminato) {
            require(parlante.chiave in nomi && eliminati.add(parlante.chiave))
        }

        private fun richiediNomeLibero(nome: String, escluso: String?) {
            val chiave = nome.trim().lowercase(Locale.ROOT)
            require(
                nomi.none { (p, n) -> p != escluso && p !in eliminati && n.trim().lowercase(Locale.ROOT) == chiave },
            ) { "Nome gia in uso: $nome" }
        }
    }
}
