# Conventions proposals

- create display-paths: a folder shown to the user is written from the home as `~/…` through ONE pure function, `snastro.ui.testi.percorsoDaCasa(percorso, cartellaUtente)`; `:ui` never reads `user.home`, `:avvio` injects it (`cartellaUtenteReale()` in `Grafo.kt`) into the presenter that builds the message — `ui/src/main/kotlin/snastro/ui/testi/TestiProgetti.kt`, `avvio/src/main/kotlin/snastro/avvio/Presenter.kt`, `ui/src/test/kotlin/snastro/ui/testi/TestiProgettiTest.kt` (first `~` path in a message, EX-9/EX-10; EX-13's "Cartella non trovata: ~/…" needs the same)
