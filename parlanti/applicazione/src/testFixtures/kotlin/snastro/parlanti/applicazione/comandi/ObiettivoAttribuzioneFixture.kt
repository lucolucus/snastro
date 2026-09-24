package snastro.parlanti.applicazione.comandi

import snastro.parlanti.dominio.TipoParlante

/**
 * [ObiettivoAttribuzione.NuovoParlante] with [TipoParlante.OCCASIONALE] — an `applicazione`-level
 * fixture (L653) so a CROSS-CONTEXT test (e.g. `documento:adattatori`'s `LettoreNomiDaParlantiTest`,
 * boundary `nomi-per-documento`) never needs `parlanti:dominio`'s `TipoParlante` itself, by FQN or
 * otherwise (CR-1: `consumer:adattatori -> supplier:applicazione` only). The RICORRENTE case needs no
 * fixture: it is [ObiettivoAttribuzione.NuovoParlante]'s own default.
 */
public fun unObiettivoOccasionale(nome: String): ObiettivoAttribuzione =
    ObiettivoAttribuzione.NuovoParlante(nome, TipoParlante.OCCASIONALE)
