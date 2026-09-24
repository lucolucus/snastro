package snastro.ui.stile

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * AC-553: spacing/radius/size tokens of `UI/design-system/tokens.json`, as named constants —
 * screens use these names, never a literal `Dp` for these roles (review criterion; see
 * `SnastroMisureTest`'s grep for the mechanical half).
 */
public object SnastroMisure {
    public val space1: Dp = 4.dp
    public val space2: Dp = 8.dp
    public val space3: Dp = 12.dp
    public val space4: Dp = 16.dp
    public val space5: Dp = 24.dp
    public val space6: Dp = 32.dp
    public val space7: Dp = 48.dp

    public val radiusControl: Dp = 6.dp
    public val radiusCard: Dp = 10.dp
    public val radiusDialog: Dp = 14.dp
    public val radiusPill: Shape = CircleShape

    public val iconS: Dp = 16.dp
    public val iconM: Dp = 20.dp
    public val controlS: Dp = 28.dp
    public val controlM: Dp = 34.dp
    public val sidebar: Dp = 232.dp
    public val pannello: Dp = 320.dp
}
