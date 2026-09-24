package snastro.ui.modelli

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import snastro.ui.SnastroTema
import snastro.ui.formattaByte
import snastro.ui.stile.BannerSn
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.CardSn
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.TipoBanner
import snastro.ui.stile.VarianteBottone
import snastro.ui.testi.ETICHETTA_DOWNLOAD_NON_RIUSCITO
import snastro.ui.testi.ETICHETTA_LICENZE
import snastro.ui.testi.ETICHETTA_RIPROVA
import snastro.ui.testi.ETICHETTA_SCARICA
import snastro.ui.testi.etichettaDownloadInCorso
import snastro.ui.testi.etichettaModelliMancanti

private val ALTEZZA_BARRA: Dp = 6.dp

/**
 * Thin view of S5 · Modelli (RC-2): only renders [stato] and forwards [azioni]'s events — AC-232's
 * "non blocca l'app" is structural (this is a plain screen, never a non-dismissible dialog): once
 * [ModelliUiStato.Pronti] it simply shows the licences, nothing here prevents the rest of the app
 * from being used.
 */
@Composable
fun SchermataModelli(
    stato: ModelliUiStato,
    azioni: AzioniModelli,
    scuro: Boolean = isSystemInDarkTheme(),
    riduciMovimento: Boolean? = null,
) {
    SnastroTema(scuro = scuro, riduciMovimento = riduciMovimento) {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (stato) {
                is ModelliUiStato.Mancanti -> ContenutoMancanti(stato, azioni)
                is ModelliUiStato.InDownload -> ContenutoInDownload(stato)
                is ModelliUiStato.Errore -> ContenutoErrore(stato, azioni)
                is ModelliUiStato.Pronti -> ContenutoPronti(stato)
            }
        }
    }
}

/** AC-578: "CardSn with total size + Primario 'Scarica'". */
@Composable
private fun ContenutoMancanti(stato: ModelliUiStato.Mancanti, azioni: AzioniModelli) {
    val colori = LocalSnastroColori.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = SnastroMisure.space5, horizontal = SnastroMisure.space6)
            .testTag("modelli-mancanti"),
    ) {
        CardSn {
            Text(
                text = etichettaModelliMancanti(stato.numero),
                style = LocalSnastroTipografia.current.heading,
                color = colori.ink,
            )
            Text(
                text = formattaByte(stato.totaleByte),
                style = LocalSnastroTipografia.current.caption,
                color = colori.inkMuted,
            )
            Spacer(modifier = Modifier.height(SnastroMisure.space4))
            BottoneSn(
                etichetta = ETICHETTA_SCARICA,
                onClick = azioni.scarica,
                variante = VarianteBottone.Primario,
                modifier = Modifier.testTag("modelli-scarica"),
            )
        }
    }
}

@Composable
private fun ContenutoInDownload(stato: ModelliUiStato.InDownload) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = SnastroMisure.space5, horizontal = SnastroMisure.space6)
            .testTag("modelli-download"),
    ) {
        BarraProgresso(
            etichetta = etichettaDownloadInCorso(stato.modelloId),
            valore = "${formattaByte(stato.scaricatiByte)} / ${formattaByte(stato.totaliByte)}",
            avanzamento = if (stato.totaliByte > 0) {
                (stato.scaricatiByte.toFloat() / stato.totaliByte).coerceIn(0f, 1f)
            } else {
                0f
            },
            modifier = Modifier.testTag("modelli-progresso"),
        )
    }
}

/**
 * AC-578: a determinate progress bar (6dp, sunken track, accent fill), label above and the value in
 * `timecode` — the shared kit (`snastro.ui.stile`) has no such component yet (it is not needed by any
 * other DONE block), so this stays a private, local composable built on the same design tokens
 * (frugality rung 6: the minimum that works, inside this block's own boundary).
 */
@Composable
private fun BarraProgresso(etichetta: String, valore: String, avanzamento: Float, modifier: Modifier = Modifier) {
    val colori = LocalSnastroColori.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = etichetta,
                style = LocalSnastroTipografia.current.body,
                color = colori.ink,
                modifier = Modifier.weight(1f),
            )
            Text(text = valore, style = LocalSnastroTipografia.current.timecode, color = colori.inkMuted)
        }
        Spacer(modifier = Modifier.height(SnastroMisure.space2))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ALTEZZA_BARRA)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(avanzamento, 0f..1f) }
                .background(colori.sunken, RoundedCornerShape(ALTEZZA_BARRA / 2)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(avanzamento)
                    .height(ALTEZZA_BARRA)
                    .background(colori.accent, RoundedCornerShape(ALTEZZA_BARRA / 2)),
            )
        }
    }
}

/** AC-578: "errors as BannerSn Errore with 'Riprova'" — the retry stays its own tagged control (not
 * `BannerSn`'s own single-action slot, which takes no external modifier/testTag to hang one on). */
@Composable
private fun ContenutoErrore(stato: ModelliUiStato.Errore, azioni: AzioniModelli) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = SnastroMisure.space5, horizontal = SnastroMisure.space6)
            .testTag("modelli-errore"),
    ) {
        BannerSn(tipo = TipoBanner.Errore, titolo = ETICHETTA_DOWNLOAD_NON_RIUSCITO, testo = stato.messaggio)
        Spacer(modifier = Modifier.height(SnastroMisure.space4))
        BottoneSn(
            etichetta = ETICHETTA_RIPROVA,
            onClick = azioni.scarica,
            variante = VarianteBottone.Secondario,
            modifier = Modifier.testTag("modelli-riprova"),
        )
    }
}

@Composable
private fun ContenutoPronti(stato: ModelliUiStato.Pronti) {
    val colori = LocalSnastroColori.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = SnastroMisure.space5, horizontal = SnastroMisure.space6)
            .testTag("modelli-pronti"),
    ) {
        Text(text = ETICHETTA_LICENZE, style = LocalSnastroTipografia.current.title, color = colori.ink)
        Spacer(modifier = Modifier.height(SnastroMisure.space4))
        LazyColumn(modifier = Modifier.fillMaxSize().testTag("modelli-licenze")) {
            items(stato.licenze, key = { it.nome }) { licenza -> RigaLicenza(licenza) }
        }
    }
}

/** AC-578: licences as a plain table (name, role, licence) in `caption`/`body`. */
@Composable
private fun RigaLicenza(licenza: LicenzaVista) {
    val colori = LocalSnastroColori.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = SnastroMisure.space2)
            .testTag("modelli-licenza-${licenza.nome}"),
    ) {
        Text(
            text = "${licenza.nome} · ${licenza.ruolo}",
            style = LocalSnastroTipografia.current.body,
            color = colori.ink,
        )
        Text(text = licenza.licenza, style = LocalSnastroTipografia.current.caption, color = colori.inkMuted)
        Text(text = licenza.attribuzione, style = LocalSnastroTipografia.current.caption, color = colori.inkMuted)
    }
}
