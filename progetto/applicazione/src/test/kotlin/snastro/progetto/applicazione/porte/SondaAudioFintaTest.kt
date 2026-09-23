package snastro.progetto.applicazione.porte

import java.time.LocalDate

class SondaAudioFintaTest : SondaAudioContratto() {
    override fun ambiente(): Ambiente =
        object : Ambiente {
            override val fileLeggibile = "/sorgenti/Seduta.m4a"
            override val dataDelFileLeggibile: LocalDate = LocalDate.of(2026, 2, 12)
            override val fileIlleggibile = "/sorgenti/Rovinato.m4a"
            override val fileVuoto = "/sorgenti/Vuoto.m4a"
            override val cartella = "/sorgenti"
            override val fileInesistente = "/sorgenti/Mancante.m4a"
            override val fileFormatoNonSupportato = "/sorgenti/Appunti.txt"
            override val sonda = SondaAudioFinta(
                leggibili = mapOf(fileLeggibile to InfoAudio(durataMs = 3_600_000, dataFile = dataDelFileLeggibile)),
                nonSupportati = setOf(fileFormatoNonSupportato),
            )
        }
}
