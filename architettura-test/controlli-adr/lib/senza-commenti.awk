# Kotlin source without comments: for each input line prints `file:n:code`, where `//` comments and (nested, multi-line)
# `/* */` comments are blanked and everything inside a string or char literal is kept (so a `//` inside a string is code,
# and a code line that starts with `*` or `/* */` is still code). Lines left blank are not printed.
# Usage: awk -f senza-commenti.awk file...
FNR == 1 { blocco = 0; grezza = 0 }
{
  riga = $0; n = length(riga); out = ""; i = 1; stringa = 0
  while (i <= n) {
    c = substr(riga, i, 1); d = substr(riga, i, 2); t = substr(riga, i, 3)
    if (blocco > 0) {
      if (d == "/*") { blocco++; i += 2 }
      else if (d == "*/") { blocco--; i += 2; out = out " " }
      else i++
    } else if (grezza) {
      if (t == "\"\"\"") { out = out t; i += 3; grezza = 0 } else { out = out c; i++ }
    } else if (stringa) {
      if (c == "\\") { out = out substr(riga, i, 2); i += 2 }
      else { if (c == "\"") stringa = 0; out = out c; i++ }
    } else if (t == "\"\"\"") { out = out t; grezza = 1; i += 3 }
    else if (c == "\"") { out = out c; stringa = 1; i++ }
    else if (c == "'") {
      resto = substr(riga, i + (substr(riga, i + 1, 1) == "\\" ? 2 : 1) + 1)
      fine = index(resto, "'")
      if (fine == 0) { out = out c; i++ }
      else { l = i + (substr(riga, i + 1, 1) == "\\" ? 2 : 1) + fine; out = out substr(riga, i, l - i + 1); i = l + 1 }
    }
    else if (d == "//") break
    else if (d == "/*") { blocco = 1; i += 2 }
    else { out = out c; i++ }
  }
  if (out ~ /[^[:space:]]/) print FILENAME ":" FNR ":" out
}
