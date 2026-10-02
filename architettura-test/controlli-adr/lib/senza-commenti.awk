# Kotlin source without comments: for each input line prints `file:n:code`, where `//` comments and (nested, multi-line)
# `/* */` comments are blanked and everything inside a string or char literal is kept (so a `//` inside a string is code,
# and a code line that starts with `*` or `/* */` is still code). String templates `${ … }` nest: a string inside a
# template expression inside a string is tracked on a stack, so its closing quote never ends the outer string early.
# Lines left blank are not printed.
# Usage: awk -f senza-commenti.awk file...
# Stack `pila[1..alto]` of open contexts: "s" a "…" string, "r" a """…""" raw string, "t" a template expression (its
# brace depth in `graffe[k]`). Empty stack = plain code. A "…" string never spans lines; a raw string may.
FNR == 1 { blocco = 0; alto = 0 }
{
  riga = $0; n = length(riga); out = ""; i = 1
  while (alto > 0 && pila[alto] != "r") alto--
  while (i <= n) {
    c = substr(riga, i, 1); d = substr(riga, i, 2); t = substr(riga, i, 3)
    cima = alto > 0 ? pila[alto] : ""
    if (blocco > 0) {
      if (d == "/*") { blocco++; i += 2 }
      else if (d == "*/") { blocco--; i += 2; out = out " " }
      else i++
    } else if (cima == "r" || cima == "s") {
      if (d == "${") { out = out d; i += 2; pila[++alto] = "t"; graffe[alto] = 1 }
      else if (cima == "r" && t == "\"\"\"") { out = out t; i += 3; alto-- }
      else if (cima == "s" && c == "\\") { out = out d; i += 2 }
      else if (cima == "s" && c == "\"") { out = out c; i++; alto-- }
      else { out = out c; i++ }
    } else if (t == "\"\"\"") { out = out t; pila[++alto] = "r"; i += 3 }
    else if (c == "\"") { out = out c; pila[++alto] = "s"; i++ }
    else if (c == "'") {
      resto = substr(riga, i + (substr(riga, i + 1, 1) == "\\" ? 2 : 1) + 1)
      fine = index(resto, "'")
      if (fine == 0) { out = out c; i++ }
      else { l = i + (substr(riga, i + 1, 1) == "\\" ? 2 : 1) + fine; out = out substr(riga, i, l - i + 1); i = l + 1 }
    }
    else if (cima == "t" && c == "{") { graffe[alto]++; out = out c; i++ }
    else if (cima == "t" && c == "}") { if (--graffe[alto] == 0) alto--; out = out c; i++ }
    else if (cima == "" && d == "//") break
    else if (cima == "" && d == "/*") { blocco = 1; i += 2 }
    else { out = out c; i++ }
  }
  if (out ~ /[^[:space:]]/) print FILENAME ":" FNR ":" out
}
