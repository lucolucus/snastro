package snastro.supporto

/** A recording [Segnalazione] for the tests (thread-safe). */
internal class SegnalazioniRegistrate : Segnalazione {
    data class Riga(val messaggio: String, val causa: Throwable?)

    private val righe = mutableListOf<Riga>()

    val tutte: List<Riga> get() = synchronized(righe) { righe.toList() }

    override fun segnala(messaggio: String, causa: Throwable?) {
        synchronized(righe) { righe += Riga(messaggio, causa) }
    }
}
