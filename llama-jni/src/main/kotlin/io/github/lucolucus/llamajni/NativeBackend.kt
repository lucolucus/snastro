package io.github.lucolucus.llamajni

import java.nio.file.Path

/**
 * The backend over a [NativeBridge]. It counts the open models: the last one to close frees the backend
 * (llama_backend_free), and the next [openModel] initializes it again. Created already initialized.
 */
internal class NativeBackend(
    private val bridge: NativeBridge,
    private val dynamicBackendDir: ByteArray?,
    private val nanoTime: () -> Long = System::nanoTime,
) : LlamaBackend {
    private val lock = Any()
    private var openModels = 0
    private var initialized = true

    override val devices: List<BackendDevice>
        get() = bridge.devices().mapNotNull { device ->
            val kind = when (device.type) {
                NativeDevice.TYPE_CPU -> DeviceKind.CPU
                NativeDevice.TYPE_GPU, NativeDevice.TYPE_IGPU -> DeviceKind.GPU
                else -> null // accelerators (BLAS, ...) only assist the CPU: not a place to put layers
            }
            kind?.let { BackendDevice(device.name, it, device.freeMemoryBytes, device.totalMemoryBytes) }
        }

    override fun openModel(model: Path, params: ModelParams): LlamaResult<LlamaModel> {
        reserve()
        var succeeded = false
        try {
            val opened = open(model, params)
            succeeded = opened is LlamaResult.Ok
            return opened
        } finally {
            // A bridge call in open() throwing (not just an Err) must not leak the reservation: with no
            // release() here that path would leave openModels incremented forever, so the backend is never
            // freed again even once every model has closed.
            if (!succeeded) release()
        }
    }

    private fun open(model: Path, params: ModelParams): LlamaResult<LlamaModel> {
        val start = nanoTime()
        val handle = bridge.loadModel(model.toString().encodeToByteArray(), params.nGpuLayers)
        val context = if (handle == 0L) {
            0L
        } else {
            bridge.newContext(handle, params.nCtx, params.nUbatch, params.flashAttention.nativeCode)
        }
        return when {
            handle == 0L -> LlamaResult.Err(LlamaError.ModelLoadFailed(detail("cannot load the model $model")))
            context == 0L -> {
                bridge.freeModel(handle)
                val what = "cannot create a ${params.nCtx}-token context"
                LlamaResult.Err(LlamaError.ContextCreateFailed(detail(what)))
            }
            else -> {
                val loadMs = (nanoTime() - start) / NANOS_PER_MILLI
                val nCtx = bridge.contextSize(context)
                val handles = NativeModel.Handles(handle, context, nCtx)
                LlamaResult.Ok(NativeModel(bridge, handles, params, loadMs, ::release, nanoTime))
            }
        }
    }

    private fun detail(what: String): String = bridge.lastError()?.let { "$what: $it" } ?: what

    private fun reserve() = synchronized(lock) {
        if (!initialized) {
            bridge.backendInit(dynamicBackendDir)
            initialized = true
        }
        openModels++
    }

    private fun release() = synchronized(lock) {
        openModels--
        if (openModels == 0) {
            bridge.backendFree()
            initialized = false
        }
    }
}

internal const val NANOS_PER_MILLI: Long = 1_000_000L
