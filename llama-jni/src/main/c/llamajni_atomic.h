/* llama-jni: the one atomic flag the shim needs (the abort request), portable C11.
 * clang/gcc: <stdatomic.h>. MSVC: Interlocked* (its C11 atomics are experimental). */
#ifndef LLAMAJNI_ATOMIC_H
#define LLAMAJNI_ATOMIC_H

#if defined(_MSC_VER) && !defined(__clang__)
#include <windows.h>
typedef volatile LONG llamajni_flag;
static inline void llamajni_flag_set(llamajni_flag *f, int v) { InterlockedExchange(f, (LONG)v); }
static inline int llamajni_flag_get(llamajni_flag *f) { return (int)InterlockedCompareExchange(f, 0, 0); }
#else
#include <stdatomic.h>
typedef atomic_int llamajni_flag;
static inline void llamajni_flag_set(llamajni_flag *f, int v) { atomic_store(f, v); }
static inline int llamajni_flag_get(llamajni_flag *f) { return atomic_load(f); }
#endif

#endif
