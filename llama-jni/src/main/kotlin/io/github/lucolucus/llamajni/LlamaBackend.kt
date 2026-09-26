package io.github.lucolucus.llamajni

import java.nio.file.Path

/** The initialized llama.cpp backend, obtained from [LlamaJni.load]. */
public interface LlamaBackend {
    /** The CPU and GPU devices, with their current free and total memory. */
    public val devices: List<BackendDevice>

    /**
     * Loads [model] (a GGUF file) and creates its context. The returned model is confined to the calling
     * thread. Blocking.
     */
    public fun openModel(model: Path, params: ModelParams): LlamaResult<LlamaModel>
}
