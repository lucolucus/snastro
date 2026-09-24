package snastro.ml

import com.k2fsa.sherpa.onnx.LibraryLoader
import com.k2fsa.sherpa.onnx.LibraryUtils
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * The sherpa-onnx engine (boundary tec-ml-sherpa, ADR 0004/0016): the ONLY place that loads the
 * natives, and the native Mutex that serializes every sherpa use (architecture R12). The composition
 * root builds ONE instance and hands it to every sherpa adapter.
 */
class MotoreSherpa internal constructor(
    private val proprieta: ProprietaSistema,
    private val caricaLibrerie: () -> Unit,
) {
    constructor() : this(ProprietaSistema.DellaJvm, ::caricaConLibraryUtils)

    private val bloccoCaricamento = Any()

    @Volatile
    private var caricati = false

    /**
     * Loads onnxruntime + the sherpa JNI lib, once (idempotent, AC-398). Directory, in this order
     * (ADR 0016 §4, AC-397): `sherpa_onnx.native.path` if already set (modelliTest), otherwise
     * `compose.application.resources.dir` (`:avvio:run`, packaged app). `java.library.path` is never
     * used. Fails naming both properties, before any load, when neither holds both libs.
     */
    fun caricaNativi() {
        if (caricati) return
        synchronized(bloccoCaricamento) {
            if (caricati) return
            val cartella = cartellaNativi()
            proprieta.imposta(PERCORSO_NATIVI, cartella.absolutePath)
            caricaLibrerie()
            caricati = true
        }
    }

    /**
     * Runs [uso] inside one native session, holding the native Mutex: two sessions never overlap, the
     * second waits (AC-401). The natives are loaded lazily first (AC-399). The session and the Mutex
     * are released when [uso] returns or throws (AC-244). Not reentrant: a nested call fails.
     */
    fun <T> conSessione(config: ConfigSessione, uso: (SessioneSherpa) -> T): T {
        check(!mutexNativo.isHeldByCurrentThread) { "conSessione is not reentrant: one native session at a time" }
        return mutexNativo.withLock {
            caricaNativi()
            SessioneSherpa(config).use(uso)
        }
    }

    private fun cartellaNativi(): File {
        val scelta = proprieta.leggi(PERCORSO_NATIVI) ?: proprieta.leggi(RISORSE_COMPOSE)
        val cartella = scelta?.let(::File)
        val mancanti = LIBRERIE.filterNot { cartella?.resolve(it)?.isFile == true }
        check(cartella != null && mancanti.isEmpty()) {
            "sherpa-onnx natives not found: set '$PERCORSO_NATIVI' or '$RISORSE_COMPOSE' to a directory " +
                "holding ${LIBRERIE.joinToString(" and ")} (ADR 0016 §4) — " +
                if (cartella == null) "neither is set" else "missing ${mancanti.joinToString()} in $cartella"
        }
        return cartella
    }

    private companion object {
        const val PERCORSO_NATIVI = "sherpa_onnx.native.path"
        const val RISORSE_COMPOSE = "compose.application.resources.dir"
        val LIBRERIE: List<String> = listOf("onnxruntime", "sherpa-onnx-jni").map(System::mapLibraryName)

        /** Process-wide: the natives are process-global, so is the lock that serializes them. */
        val mutexNativo = ReentrantLock(true)

        fun caricaConLibraryUtils() {
            // sherpa constructors auto-load through LibraryLoader, whose fallbacks (in-jar resource,
            // java.library.path) we never want: the explicit load below is the only one (ADR 0016 §4).
            LibraryLoader.setAutoLoadEnabled(false)
            LibraryUtils.load()
        }
    }
}
