package snastro.ml

/** The JVM system properties MotoreSherpa reads/writes — substitutable so the gate never loads natives. */
internal interface ProprietaSistema {
    fun leggi(nome: String): String?

    fun imposta(nome: String, valore: String)

    object DellaJvm : ProprietaSistema {
        override fun leggi(nome: String): String? = System.getProperty(nome)

        override fun imposta(nome: String, valore: String) {
            System.setProperty(nome, valore)
        }
    }
}
