package snastro.ui.stile

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * L723: the real (non-mocked) system read. On a working machine `defaults read
 * com.apple.universalaccess reduceMotion` completes well inside [riduciMovimentoSistema]'s budget —
 * a DEFINITIVE outcome — so it never throws out of [riduciMovimentoSistema] (the catch now covers
 * more than [java.io.IOException]) and the result gets cached. The pure decision table itself
 * ([interpretaRiduciMovimento]) is covered by [RiduciMovimentoTest]; a fake/slow process to force
 * the timeout-not-cached branch would need injecting the process launcher, which nothing else in
 * this small OS shim needs (frugality: YAGNI).
 */
class RiduciMovimentoSistemaTest {
    @Test
    fun `L723 la lettura reale non lancia e una risposta definitiva finisce in cache`() {
        // Block body on purpose: `runBlocking` is generic over its block's return type, and
        // `assertNotNull` (unlike the other kotlin.test assertions) returns the asserted value, not
        // Unit — as the LAST expression of an `= runBlocking { ... }` that would silently make this
        // whole test method return a non-void value, which JUnit5 discovery then skips as "not a
        // test method" (no failure, no report, just quietly not run).
        runBlocking {
            riduciMovimentoSistema() // must not throw: the catch now covers more than IOException
            assertNotNull(riduciMovimentoSistemaNoto(), "una lettura reale e definitiva deve restare in cache")
        }
    }
}
