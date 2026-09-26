/*
 * llama-jni: the JNI shim over the C API of llama.cpp (release pinned in build.gradle.kts, headers vendored
 * in include/). Portable C11, no C++ runtime. Each function backs one `external fun` of the Kotlin object
 * io.github.lucolucus.llamajni.JniBridge; the Kotlin side owns validation, the overflow check, the prefill
 * chunking, the cancel watcher and the result mapping.
 *
 * Text (prompts, grammars, paths, generated output, device names, error details) crosses as standard
 * UTF-8 byte arrays in both directions: JNI's modified UTF-8 would corrupt supplementary characters and NUL.
 */
#include <jni.h>
#include <limits.h>
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "llama.h"
#include "llamajni_atomic.h"

#define JNI_FN(name) JNICALL Java_io_github_lucolucus_llamajni_JniBridge_##name

/* Status codes shared with NativeBridge.kt (keep in sync). */
enum { GEN_END_OF_GENERATION = 0, GEN_MAX_TOKENS = 1, GEN_ABORTED = 2, GEN_DECODE_FAILED = 3 };
enum { DECODE_ABORTED = 2 }; /* llama_decode: 2 = aborted by the abort callback */

typedef struct {
    struct llama_context *ctx;
    const struct llama_vocab *vocab;
    int32_t n_vocab;
    llama_token_data *candidates;
    llamajni_flag abort;
} Context;

typedef struct {
    struct llama_sampler *chain;
    struct llama_sampler *grammar; /* NULL when no grammar */
    int lazy;
} Sampler;

/* ---- last error: the most recent ERROR log line of this thread (a detail for Kotlin) ---------------- */

#define ERROR_CAP 512
static _Thread_local char last_error[ERROR_CAP];
static _Thread_local int last_level_error;

static void on_log(enum ggml_log_level level, const char *text, void *user_data) {
    (void)user_data;
    if (level == GGML_LOG_LEVEL_ERROR) {
        last_level_error = 1;
        strncpy(last_error, text, ERROR_CAP - 1);
        last_error[ERROR_CAP - 1] = 0;
    } else if (level == GGML_LOG_LEVEL_CONT && last_level_error) {
        size_t used = strlen(last_error);
        strncat(last_error, text, ERROR_CAP - 1 - used);
    } else if (level != GGML_LOG_LEVEL_CONT) {
        last_level_error = 0;
    }
    if (level == GGML_LOG_LEVEL_ERROR || (level == GGML_LOG_LEVEL_CONT && last_level_error)) {
        fputs(text, stderr);
    }
}

static jbyteArray to_bytes(JNIEnv *env, const char *data, size_t len) {
    jbyteArray out = (*env)->NewByteArray(env, (jsize)len);
    if (out != NULL && len > 0) (*env)->SetByteArrayRegion(env, out, 0, (jsize)len, (const jbyte *)data);
    return out;
}

/* A NUL-terminated copy of a Java byte[] (UTF-8), or NULL for a null array. The caller frees it. */
static char *from_bytes(JNIEnv *env, jbyteArray bytes, int32_t *len) {
    if (bytes == NULL) return NULL;
    jsize n = (*env)->GetArrayLength(env, bytes);
    char *s = malloc((size_t)n + 1);
    if (s == NULL) return NULL;
    (*env)->GetByteArrayRegion(env, bytes, 0, n, (jbyte *)s);
    s[n] = 0;
    if (len != NULL) *len = (int32_t)n;
    return s;
}

static bool abort_requested(void *data) { return llamajni_flag_get(&((Context *)data)->abort) != 0; }

/* ---- backend ------------------------------------------------------------------------------------------- */

JNIEXPORT void JNI_FN(nBackendInit)(JNIEnv *env, jclass cls, jbyteArray dynamic_backend_dir) {
    (void)cls;
    static int backends_loaded = 0;
    llama_log_set(on_log, NULL);
    if (dynamic_backend_dir != NULL && !backends_loaded) {
        char *dir = from_bytes(env, dynamic_backend_dir, NULL);
        if (dir != NULL) {
            ggml_backend_load_all_from_path(dir);
            free(dir);
            backends_loaded = 1;
        }
    }
    llama_backend_init();
}

JNIEXPORT void JNI_FN(nBackendFree)(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    llama_backend_free();
}

JNIEXPORT jint JNI_FN(nDeviceCount)(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    return (jint)ggml_backend_dev_count();
}

JNIEXPORT jbyteArray JNI_FN(nDeviceName)(JNIEnv *env, jclass cls, jint index) {
    (void)cls;
    ggml_backend_dev_t dev = ggml_backend_dev_get((size_t)index);
    const char *desc = ggml_backend_dev_description(dev);
    const char *name = (desc != NULL && desc[0] != 0) ? desc : ggml_backend_dev_name(dev);
    return to_bytes(env, name, strlen(name));
}

JNIEXPORT jint JNI_FN(nDeviceKind)(JNIEnv *env, jclass cls, jint index) {
    (void)env; (void)cls;
    return (jint)ggml_backend_dev_type(ggml_backend_dev_get((size_t)index));
}

JNIEXPORT void JNI_FN(nDeviceMemory)(JNIEnv *env, jclass cls, jint index, jlongArray out) {
    (void)cls;
    size_t free_bytes = 0, total_bytes = 0;
    ggml_backend_dev_memory(ggml_backend_dev_get((size_t)index), &free_bytes, &total_bytes);
    jlong values[2] = { (jlong)free_bytes, (jlong)total_bytes };
    (*env)->SetLongArrayRegion(env, out, 0, 2, values);
}

JNIEXPORT jbyteArray JNI_FN(nLastError)(JNIEnv *env, jclass cls) {
    (void)cls;
    size_t len = strlen(last_error);
    while (len > 0 && (last_error[len - 1] == '\n' || last_error[len - 1] == '\r')) len--;
    return len == 0 ? NULL : to_bytes(env, last_error, len);
}

/* ---- model and context --------------------------------------------------------------------------------- */

JNIEXPORT jlong JNI_FN(nLoadModel)(JNIEnv *env, jclass cls, jbyteArray path, jint n_gpu_layers) {
    (void)cls;
    last_error[0] = 0;
    char *p = from_bytes(env, path, NULL);
    if (p == NULL) return 0;
    struct llama_model_params params = llama_model_default_params();
    params.n_gpu_layers = n_gpu_layers;
    struct llama_model *model = llama_model_load_from_file(p, params);
    free(p);
    return (jlong)(intptr_t)model;
}

JNIEXPORT void JNI_FN(nFreeModel)(JNIEnv *env, jclass cls, jlong model) {
    (void)env; (void)cls;
    llama_model_free((struct llama_model *)(intptr_t)model);
}

JNIEXPORT jlong JNI_FN(nNewContext)(JNIEnv *env, jclass cls, jlong model, jint n_ctx, jint n_ubatch, jint flash_attention) {
    (void)env; (void)cls;
    last_error[0] = 0;
    struct llama_model *m = (struct llama_model *)(intptr_t)model;
    Context *c = calloc(1, sizeof(Context));
    if (c == NULL) return 0;
    struct llama_context_params params = llama_context_default_params();
    params.n_ctx = (uint32_t)n_ctx;
    params.n_batch = (uint32_t)n_ubatch; /* prefill chunks are <= n_ubatch (validated in Kotlin) */
    params.n_ubatch = (uint32_t)n_ubatch;
    params.n_seq_max = 1;
    params.flash_attn_type = (enum llama_flash_attn_type)flash_attention;
    params.no_perf = true;
    params.abort_callback = abort_requested;
    params.abort_callback_data = c;
    c->ctx = llama_init_from_model(m, params);
    c->vocab = llama_model_get_vocab(m);
    c->n_vocab = llama_vocab_n_tokens(c->vocab);
    c->candidates = malloc(sizeof(llama_token_data) * (size_t)c->n_vocab);
    if (c->ctx == NULL || c->candidates == NULL) {
        if (c->ctx != NULL) llama_free(c->ctx);
        free(c->candidates);
        free(c);
        return 0;
    }
    return (jlong)(intptr_t)c;
}

JNIEXPORT jint JNI_FN(nContextSize)(JNIEnv *env, jclass cls, jlong context) {
    (void)env; (void)cls;
    uint32_t n = llama_n_ctx(((Context *)(intptr_t)context)->ctx);
    return n > INT32_MAX ? INT32_MAX : (jint)n;
}

JNIEXPORT void JNI_FN(nFreeContext)(JNIEnv *env, jclass cls, jlong context) {
    (void)env; (void)cls;
    Context *c = (Context *)(intptr_t)context;
    llama_free(c->ctx);
    free(c->candidates);
    free(c);
}

/* Exact token count/ids: llama_tokenize with special-token parsing, no BOS/EOS added. */
JNIEXPORT jintArray JNI_FN(nTokenize)(JNIEnv *env, jclass cls, jlong model, jbyteArray text) {
    (void)cls;
    const struct llama_vocab *vocab = llama_model_get_vocab((struct llama_model *)(intptr_t)model);
    int32_t len = 0;
    char *s = from_bytes(env, text, &len);
    if (s == NULL) return NULL;
    int32_t cap = len + 16;
    llama_token *tokens = malloc(sizeof(llama_token) * (size_t)cap);
    int32_t n = tokens == NULL ? INT32_MIN : llama_tokenize(vocab, s, len, tokens, cap, false, true);
    if (n < 0 && n != INT32_MIN) {
        cap = -n;
        llama_token *bigger = realloc(tokens, sizeof(llama_token) * (size_t)cap);
        if (bigger == NULL) {
            n = INT32_MIN;
        } else {
            tokens = bigger;
            n = llama_tokenize(vocab, s, len, tokens, cap, false, true);
        }
    }
    free(s);
    jintArray out = NULL;
    if (n >= 0) {
        out = (*env)->NewIntArray(env, n);
        if (out != NULL && n > 0) (*env)->SetIntArrayRegion(env, out, 0, n, (const jint *)tokens);
    }
    free(tokens);
    return out;
}

/* ---- sampling ------------------------------------------------------------------------------------------ */

JNIEXPORT jlong JNI_FN(nNewSampler)(JNIEnv *env, jclass cls, jlong model, jbyteArray grammar, jbyteArray grammar_root,
                                    jboolean lazy, jfloat temperature, jint top_k, jfloat top_p, jint seed) {
    (void)cls;
    last_error[0] = 0;
    const struct llama_vocab *vocab = llama_model_get_vocab((struct llama_model *)(intptr_t)model);
    Sampler *s = calloc(1, sizeof(Sampler));
    if (s == NULL) return 0;
    if (grammar != NULL) {
        char *g = from_bytes(env, grammar, NULL);
        char *root = from_bytes(env, grammar_root, NULL);
        s->grammar = (g != NULL && root != NULL) ? llama_sampler_init_grammar(vocab, g, root) : NULL;
        free(g);
        free(root);
        if (s->grammar == NULL) {
            free(s);
            return 0;
        }
    }
    s->lazy = lazy == JNI_TRUE;
    s->chain = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (top_k > 0) llama_sampler_chain_add(s->chain, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(s->chain, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(s->chain, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(s->chain, llama_sampler_init_dist((uint32_t)seed));
    return (jlong)(intptr_t)s;
}

JNIEXPORT void JNI_FN(nFreeSampler)(JNIEnv *env, jclass cls, jlong sampler) {
    (void)env; (void)cls;
    Sampler *s = (Sampler *)(intptr_t)sampler;
    if (s->grammar != NULL) llama_sampler_free(s->grammar);
    llama_sampler_free(s->chain);
    free(s);
}

/* ---- generation ---------------------------------------------------------------------------------------- */

/* A fresh generation: empty memory (KV cache), abort flag cleared. */
JNIEXPORT void JNI_FN(nBeginGeneration)(JNIEnv *env, jclass cls, jlong context) {
    (void)env; (void)cls;
    Context *c = (Context *)(intptr_t)context;
    llamajni_flag_set(&c->abort, 0);
    llama_memory_clear(llama_get_memory(c->ctx), true);
}

JNIEXPORT void JNI_FN(nAbort)(JNIEnv *env, jclass cls, jlong context) {
    (void)env; (void)cls;
    llamajni_flag_set(&((Context *)(intptr_t)context)->abort, 1);
}

/* One prefill chunk: tokens[offset, offset + count). Returns llama_decode's code (2 = aborted). */
JNIEXPORT jint JNI_FN(nDecode)(JNIEnv *env, jclass cls, jlong context, jintArray tokens, jint offset, jint count) {
    (void)cls;
    Context *c = (Context *)(intptr_t)context;
    if (llamajni_flag_get(&c->abort)) return DECODE_ABORTED;
    llama_token *chunk = malloc(sizeof(llama_token) * (size_t)count);
    if (chunk == NULL) return -1;
    (*env)->GetIntArrayRegion(env, tokens, offset, count, (jint *)chunk);
    int32_t r = llama_decode(c->ctx, llama_batch_get_one(chunk, count));
    free(chunk);
    return r;
}

static void fill_candidates(Context *c, llama_token_data_array *arr) {
    const float *logits = llama_get_logits_ith(c->ctx, -1);
    for (int32_t i = 0; i < c->n_vocab; i++) c->candidates[i] = (llama_token_data){ i, logits[i], 0.0f };
    *arr = (llama_token_data_array){ c->candidates, (size_t)c->n_vocab, -1, false };
}

/* Samples one token. Lazy grammar: sample without the grammar, check only the chosen token, and apply the
 * grammar to the full vocabulary (then resample) only when it rejects that token. */
static llama_token sample(Context *c, Sampler *s) {
    llama_token_data_array arr;
    fill_candidates(c, &arr);
    if (s->grammar != NULL && !s->lazy) llama_sampler_apply(s->grammar, &arr);
    llama_sampler_apply(s->chain, &arr);
    llama_token token = arr.data[arr.selected].id;
    if (s->grammar != NULL && s->lazy) {
        llama_token_data single = { token, 1.0f, 0.0f };
        llama_token_data_array one = { &single, 1, -1, false };
        llama_sampler_apply(s->grammar, &one);
        if (isinf(single.logit)) {
            fill_candidates(c, &arr);
            llama_sampler_apply(s->grammar, &arr);
            llama_sampler_apply(s->chain, &arr);
            token = arr.data[arr.selected].id;
        }
    }
    if (s->grammar != NULL) llama_sampler_accept(s->grammar, token);
    llama_sampler_accept(s->chain, token);
    return token;
}

/* Generates after the prefill, up to max_tokens, stopping at end-of-generation. The output bytes are
 * accumulated here and returned whole (a token can split a multi-byte character).
 * out[0] = generated tokens, out[1] = GEN_* status, out[2] = llama_decode code when GEN_DECODE_FAILED. */
JNIEXPORT jbyteArray JNI_FN(nGenerate)(JNIEnv *env, jclass cls, jlong context, jlong sampler, jint max_tokens, jintArray out) {
    (void)cls;
    Context *c = (Context *)(intptr_t)context;
    Sampler *s = (Sampler *)(intptr_t)sampler;
    size_t cap = 4096, len = 0;
    char *text = malloc(cap);
    jint result[3] = { 0, GEN_END_OF_GENERATION, 0 };
    int32_t generated = 0;
    while (text != NULL) {
        if (llamajni_flag_get(&c->abort)) { result[1] = GEN_ABORTED; break; }
        llama_token token = sample(c, s);
        if (llama_vocab_is_eog(c->vocab, token)) break;
        int32_t n = llama_token_to_piece(c->vocab, token, text + len, (int32_t)(cap - len), 0, false);
        if (n < 0) { /* not enough room: grow to fit the piece, then write it again */
            size_t need = len + (size_t)(-n);
            while (cap < need) cap *= 2;
            char *bigger = realloc(text, cap);
            if (bigger == NULL) { free(text); text = NULL; break; }
            text = bigger;
            n = llama_token_to_piece(c->vocab, token, text + len, (int32_t)(cap - len), 0, false);
        }
        if (n > 0) len += (size_t)n;
        generated++;
        if (generated >= max_tokens) { result[1] = GEN_MAX_TOKENS; break; }
        int32_t r = llama_decode(c->ctx, llama_batch_get_one(&token, 1));
        if (r == DECODE_ABORTED) { result[1] = GEN_ABORTED; break; }
        if (r != 0) { result[1] = GEN_DECODE_FAILED; result[2] = r; break; }
    }
    if (text == NULL) { result[1] = GEN_DECODE_FAILED; result[2] = -1; len = 0; }
    result[0] = generated;
    (*env)->SetIntArrayRegion(env, out, 0, 3, result);
    jbyteArray bytes = to_bytes(env, text, len);
    free(text);
    return bytes;
}
