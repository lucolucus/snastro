package io.github.lucolucus.llamajni

import java.nio.file.Files
import java.nio.file.Path

/** Entry point: loads the native libraries and initializes the llama.cpp backend. */
public object LlamaJni {
    private val loader = NativeLoader(loadLibrary = { System.load(it.toString()) }, bridge = JniBridge)

    /**
     * Loads the natives from [nativeDir] (the caller chooses it: no system property, no default location)
     * and initializes the backend. Idempotent per JVM: after the first success, a LATER call — even with a
     * DIFFERENT [nativeDir] — loads nothing and silently returns the SAME backend the first call returned.
     */
    public fun load(nativeDir: Path): LlamaResult<LlamaBackend> = loader.load(nativeDir)
}

/** Which libraries to load, in order, and whether backends are loaded dynamically from the directory. */
internal data class LoadPlan(val libraries: List<String>, val dynamicBackends: Boolean) {
    companion object {
        /** macOS arm64: the shim only — its rpath (@loader_path) resolves libllama and libggml*, which link
         * the Metal/CPU/BLAS backends directly. */
        val MACOS_ARM64: LoadPlan = LoadPlan(listOf("libllamajni.dylib"), dynamicBackends = false)

        /** The plan whose shim is in [dir]; `null` when none is (only macOS arm64 is wired). Reads no property. */
        fun forDirectory(dir: Path): LoadPlan? =
            MACOS_ARM64.takeIf { Files.isRegularFile(dir.resolve(it.libraries.last())) }
    }
}

internal class NativeLoader(private val loadLibrary: (Path) -> Unit, private val bridge: NativeBridge) {
    private var backend: LlamaBackend? = null

    @Synchronized
    fun load(nativeDir: Path): LlamaResult<LlamaBackend> = backend?.let { LlamaResult.Ok(it) }
        ?: when (val loaded = loadLibraries(nativeDir)) {
            is LlamaResult.Err -> loaded
            is LlamaResult.Ok -> {
                val dynamicDir = if (loaded.value.dynamicBackends) nativeDir.toString().encodeToByteArray() else null
                bridge.backendInit(dynamicDir)
                LlamaResult.Ok(NativeBackend(bridge, dynamicDir).also { backend = it })
            }
        }

    private fun loadLibraries(nativeDir: Path): LlamaResult<LoadPlan> {
        val plan = LoadPlan.forDirectory(nativeDir)
            ?: return LlamaResult.Err(
                LlamaError.NativeLoadFailed(
                    "no llama-jni shim in $nativeDir (expected ${LoadPlan.MACOS_ARM64.libraries.last()}; " +
                        "only macOS arm64 is supported for now)",
                ),
            )
        return try {
            plan.libraries.forEach { loadLibrary(nativeDir.resolve(it)) }
            LlamaResult.Ok(plan)
        } catch (e: UnsatisfiedLinkError) {
            LlamaResult.Err(LlamaError.NativeLoadFailed("cannot load the natives from $nativeDir: ${e.message}"))
        }
    }
}
