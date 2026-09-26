# Rework 1 — llama-jni-libreria (code-review HIGH, head 0802522)

## HIGH — CancelWatcher.close() joins interruptibly (CancelWatcher.kt:34-38, via NativeModel.kt:63)
If the caller thread is interrupted (ADR 0026 §4: interrupt = cancel signal, e.g. executor shutdownNow),
`thread.join()` throws InterruptedException at once:
1. `generate()` leaks a raw InterruptedException instead of returning a `LlamaResult` (even after a completed native generation) — contract drift;
2. the watcher is never joined: if it is inside `cancel()` and that returns true it calls `bridge.abort(handles.context)`
   while the caller's `use {}` runs `close()` → `nFreeContext` → `nAbort` on a freed native Context (use-after-free).
   Breaks the class guarantee "onCancel never after close".

Fix: join uninterruptibly (loop on `join()`, catch InterruptedException, restore the interrupt flag afterwards);
map a caller interrupt to cancellation per ADR 0026 §4 if the contract says so.
Add a gate test: interrupt the caller while a blocking fake bridge call is in progress → a `LlamaResult` is returned,
no bridge call after close, the interrupt flag is restored.

Advisory (fold in only if trivial, else leave): a `cancel()` that throws kills the watcher silently (CancelWatcher.kt:20-31);
`NativeBackend.openModel` without try/finally leaks the refcount if the bridge throws (NativeBackend.kt:28-33).
