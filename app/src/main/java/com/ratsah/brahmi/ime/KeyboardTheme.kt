package com.ratsah.brahmi.ime

/**
 * All colors and stroke widths needed to paint the keyboard.
 *
 * Every property is resolved once when [KeyboardView] rebuilds its rows, so
 * changing any value here flows through to key backgrounds, popups, the press
 * preview, function-key icons, and the IME window's navigation-bar band.
 *
 * Contrast note: the high-contrast palettes are designed to meet WCAG AAA
 * for the main glyph (≥ 7:1 luminance ratio). They also bump [strokeWidthDp]
 * so the border of every key remains visible for users with low vision.
 */
data class KeyboardPalette(
  /** Solid color painted behind the keys (and behind the nav-bar inset). */
  val background: Int,
  /** Fill color for regular letter keys. */
  val keyFill: Int,
  /** Fill color for function/accent keys (shift, page-switch, enter, …). */
  val accentKeyFill: Int,
  /** Color of the 1-px (or [strokeWidthDp]-px) border around each key. */
  val keyStroke: Int,
  /** Border thickness in dp. Bumped for the high-contrast themes. */
  val strokeWidthDp: Int,
  /** Color of the primary glyph on each key (Brahmi character or label). */
  val keyTextColor: Int,
  /** Color of the small descendant-script hint drawn under the main glyph. */
  val hintTextColor: Int,
  /** Tint applied to function-key vector icons (e.g. the IME-switcher globe). */
  val iconTint: Int,
  /** Overlay color used for the touch ripple. Alpha channel matters. */
  val rippleColor: Int,
  /** Color of the little dot that hints at long-press alternates. */
  val popupDotColor: Int,
  /** Background fill of long-press popups and the press-preview balloon. */
  val popupBackground: Int,
  /** Border color of long-press popups. */
  val popupStroke: Int,
  /**
   * `true` when the keyboard is dark overall. Used to pick the appearance of
   * the system navigation-bar icons so they stay legible against the
   * keyboard background.
   */
  val isDarkKeyboard: Boolean,
)

/**
 * A user-selectable keyboard theme.
 *
 * A theme is either:
 *   - **Fixed**: a single [palette] applied regardless of system dark mode
 *     (e.g. "Light", "High contrast dark", "Sepia").
 *   - **System-following**: has both [lightPalette] and [darkPalette] and
 *     picks between them based on [android.content.res.Configuration].
 *
 * Add a new theme by declaring a `KeyboardTheme` in [KeyboardThemes] and
 * registering it in [KeyboardThemes.ALL].
 */
data class KeyboardTheme(
  val id: String,
  val displayName: String,
  private val palette: KeyboardPalette? = null,
  private val lightPalette: KeyboardPalette? = null,
  private val darkPalette: KeyboardPalette? = null,
) {
  init {
    require(
      (palette != null) xor (lightPalette != null && darkPalette != null),
    ) {
      "KeyboardTheme '$id' must either pin a single palette or provide both light+dark variants"
    }
  }

  /** Resolve the palette to use given the current system dark-mode state. */
  fun paletteFor(isSystemDark: Boolean): KeyboardPalette =
    palette ?: if (isSystemDark) darkPalette!! else lightPalette!!
}

/**
 * Registry of built-in keyboard themes. The order in [ALL] is the order
 * shown in the settings dropdown.
 */
object KeyboardThemes {

  // --- Baseline (matches the original hard-coded look) --------------------

  private val LIGHT = KeyboardPalette(
    background = 0xFFECEFF1.toInt(),
    keyFill = 0xFFFFFFFF.toInt(),
    accentKeyFill = 0xFFCFD8DC.toInt(),
    keyStroke = 0xFFB0BEC5.toInt(),
    strokeWidthDp = 1,
    keyTextColor = 0xFF000000.toInt(),
    hintTextColor = 0xFF707070.toInt(),
    iconTint = 0xFF000000.toInt(),
    rippleColor = 0x22000000,
    popupDotColor = 0xFF1E88E5.toInt(),
    popupBackground = 0xFFFFFFFF.toInt(),
    popupStroke = 0xFF90A4AE.toInt(),
    isDarkKeyboard = false,
  )

  private val DARK = KeyboardPalette(
    background = 0xFF000000.toInt(),
    keyFill = 0xFF2D2E31.toInt(),
    accentKeyFill = 0xFF3C4043.toInt(),
    keyStroke = 0xFF5F6368.toInt(),
    strokeWidthDp = 1,
    keyTextColor = 0xFFFFFFFF.toInt(),
    hintTextColor = 0xFFB0B0B0.toInt(),
    iconTint = 0xFFFFFFFF.toInt(),
    rippleColor = 0x33FFFFFF,
    popupDotColor = 0xFF64B5F6.toInt(),
    popupBackground = 0xFF424549.toInt(),
    popupStroke = 0xFF5F6368.toInt(),
    isDarkKeyboard = true,
  )

  // --- Accessibility: maximum contrast, thicker borders -------------------

  private val HIGH_CONTRAST_LIGHT = KeyboardPalette(
    background = 0xFFFFFFFF.toInt(),
    keyFill = 0xFFFFFFFF.toInt(),
    accentKeyFill = 0xFFDDDDDD.toInt(),
    keyStroke = 0xFF000000.toInt(),
    strokeWidthDp = 2,
    keyTextColor = 0xFF000000.toInt(),
    // Hint text stays fully black too - the whole point of the theme is
    // that no glyph is rendered in a low-contrast gray.
    hintTextColor = 0xFF000000.toInt(),
    iconTint = 0xFF000000.toInt(),
    rippleColor = 0x55000000,
    // Pure black dot on white keys - the accent blue we normally use
    // drops below AAA contrast on a white background.
    popupDotColor = 0xFF000000.toInt(),
    popupBackground = 0xFFFFFFFF.toInt(),
    popupStroke = 0xFF000000.toInt(),
    isDarkKeyboard = false,
  )

  private val HIGH_CONTRAST_DARK = KeyboardPalette(
    background = 0xFF000000.toInt(),
    keyFill = 0xFF000000.toInt(),
    accentKeyFill = 0xFF1A1A1A.toInt(),
    keyStroke = 0xFFFFFFFF.toInt(),
    strokeWidthDp = 2,
    keyTextColor = 0xFFFFFFFF.toInt(),
    hintTextColor = 0xFFFFFFFF.toInt(),
    iconTint = 0xFFFFFFFF.toInt(),
    rippleColor = 0x66FFFFFF,
    // Yellow reads clearly against pure black and is a common
    // accessibility accent color (used by e.g. Windows High Contrast).
    popupDotColor = 0xFFFFEB3B.toInt(),
    popupBackground = 0xFF000000.toInt(),
    popupStroke = 0xFFFFFFFF.toInt(),
    isDarkKeyboard = true,
  )

  // --- Optional flair -----------------------------------------------------

  /** Warm cream/brown, easy on the eyes for extended reading. */
  private val SEPIA = KeyboardPalette(
    background = 0xFFEFE5CC.toInt(),
    keyFill = 0xFFFBF6E7.toInt(),
    accentKeyFill = 0xFFE0D3AF.toInt(),
    keyStroke = 0xFFB09A6E.toInt(),
    strokeWidthDp = 1,
    keyTextColor = 0xFF4B3621.toInt(),
    hintTextColor = 0xFF8B6E4E.toInt(),
    iconTint = 0xFF4B3621.toInt(),
    rippleColor = 0x33704214,
    popupDotColor = 0xFFA0522D.toInt(),
    popupBackground = 0xFFFBF6E7.toInt(),
    popupStroke = 0xFFB09A6E.toInt(),
    isDarkKeyboard = false,
  )

  /** Solarized Dark palette (Ethan Schoonover). */
  private val SOLARIZED_DARK = KeyboardPalette(
    background = 0xFF002B36.toInt(), // base03
    keyFill = 0xFF073642.toInt(), // base02
    accentKeyFill = 0xFF586E75.toInt(), // base01
    keyStroke = 0xFF586E75.toInt(),
    strokeWidthDp = 1,
    keyTextColor = 0xFFEEE8D5.toInt(), // base2
    hintTextColor = 0xFF93A1A1.toInt(), // base1
    iconTint = 0xFFEEE8D5.toInt(),
    rippleColor = 0x338AB4C7,
    popupDotColor = 0xFFB58900.toInt(), // yellow
    popupBackground = 0xFF073642.toInt(),
    popupStroke = 0xFF586E75.toInt(),
    isDarkKeyboard = true,
  )

  /** Deep-blue dark theme, softer than pure black. */
  private val MIDNIGHT_BLUE = KeyboardPalette(
    background = 0xFF0D1B2A.toInt(),
    keyFill = 0xFF1B2A41.toInt(),
    accentKeyFill = 0xFF2A3F5F.toInt(),
    keyStroke = 0xFF415A77.toInt(),
    strokeWidthDp = 1,
    keyTextColor = 0xFFE0E1DD.toInt(),
    hintTextColor = 0xFFA9BCD0.toInt(),
    iconTint = 0xFFE0E1DD.toInt(),
    rippleColor = 0x33A9BCD0,
    popupDotColor = 0xFF64B5F6.toInt(),
    popupBackground = 0xFF1B2A41.toInt(),
    popupStroke = 0xFF415A77.toInt(),
    isDarkKeyboard = true,
  )

  /**
   * Monokai palette (Wimer Hazenberg), AMOLED-friendly variant. Pure
   * black background with white primary glyphs, and Monokai's signature
   * magenta threaded through every key border, ripple and popup edge so
   * the theme reads as unmistakably Monokai. The hint script keeps
   * Monokai's function-name green.
   */
  private val MONOKAI = KeyboardPalette(
    background = 0xFF000000.toInt(),
    keyFill = 0xFF3E3D32.toInt(),       // classic Monokai line-highlight color
    accentKeyFill = 0xFF49483E.toInt(), // selection color
    keyStroke = 0xFFF92672.toInt(),     // Monokai keyword magenta - the pop
    strokeWidthDp = 1,
    keyTextColor = 0xFFFFFFFF.toInt(),  // pure white primary alphabet
    hintTextColor = 0xFFA6E22E.toInt(), // function-name green
    iconTint = 0xFFFFFFFF.toInt(),
    rippleColor = 0x66F92672,           // magenta ripple, clearly visible
    popupDotColor = 0xFFE6DB74.toInt(), // Monokai yellow - reads well against
                                        // the magenta border
    popupBackground = 0xFF3E3D32.toInt(),
    popupStroke = 0xFFF92672.toInt(),   // magenta popup border matches keys
    isDarkKeyboard = true,
  )

  /**
   * Darcula palette (JetBrains IntelliJ IDEA default dark theme),
   * AMOLED-friendly variant. Pure black background with white primary
   * glyphs, JetBrains keyword-orange borders and orange function-key
   * icons, and JetBrains number-blue for the hint script - so the theme
   * carries a distinct JetBrains identity beyond "another neutral dark".
   */
  private val DARCULA = KeyboardPalette(
    background = 0xFF000000.toInt(),
    keyFill = 0xFF3C3F41.toInt(),       // JetBrains panel background
    accentKeyFill = 0xFF4C5052.toInt(),
    keyStroke = 0xFFCC7832.toInt(),     // JetBrains keyword orange - the pop
    strokeWidthDp = 1,
    keyTextColor = 0xFFFFFFFF.toInt(),  // pure white primary alphabet
    hintTextColor = 0xFF6897BB.toInt(), // JetBrains number-blue
    iconTint = 0xFFCC7832.toInt(),      // orange icons complete the accent
    rippleColor = 0x66CC7832,           // orange ripple
    popupDotColor = 0xFF6A8759.toInt(), // JetBrains string-green pops against
                                        // the orange border
    popupBackground = 0xFF3C3F41.toInt(),
    popupStroke = 0xFFCC7832.toInt(),   // orange popup border matches keys
    isDarkKeyboard = true,
  )

  // --- Public registry ----------------------------------------------------

  /** Follows the system light/dark configuration - default for new installs. */
  val SYSTEM: KeyboardTheme = KeyboardTheme(
    id = "system",
    displayName = "System default",
    lightPalette = LIGHT,
    darkPalette = DARK,
  )

  val LIGHT_THEME: KeyboardTheme = KeyboardTheme(
    id = "light",
    displayName = "Light",
    palette = LIGHT,
  )

  val DARK_THEME: KeyboardTheme = KeyboardTheme(
    id = "dark",
    displayName = "Dark",
    palette = DARK,
  )

  val HIGH_CONTRAST_LIGHT_THEME: KeyboardTheme = KeyboardTheme(
    id = "high_contrast_light",
    displayName = "High contrast (light)",
    palette = HIGH_CONTRAST_LIGHT,
  )

  val HIGH_CONTRAST_DARK_THEME: KeyboardTheme = KeyboardTheme(
    id = "high_contrast_dark",
    displayName = "High contrast (dark)",
    palette = HIGH_CONTRAST_DARK,
  )

  val SEPIA_THEME: KeyboardTheme = KeyboardTheme(
    id = "sepia",
    displayName = "Sepia",
    palette = SEPIA,
  )

  val SOLARIZED_DARK_THEME: KeyboardTheme = KeyboardTheme(
    id = "solarized_dark",
    displayName = "Solarized dark",
    palette = SOLARIZED_DARK,
  )

  val MIDNIGHT_BLUE_THEME: KeyboardTheme = KeyboardTheme(
    id = "midnight_blue",
    displayName = "Midnight blue",
    palette = MIDNIGHT_BLUE,
  )

  val MONOKAI_THEME: KeyboardTheme = KeyboardTheme(
    id = "monokai",
    displayName = "Monokai",
    palette = MONOKAI,
  )

  val DARCULA_THEME: KeyboardTheme = KeyboardTheme(
    id = "darcula",
    displayName = "Darcula",
    palette = DARCULA,
  )

  /** Order shown in the settings UI. Add new themes here. */
  val ALL: List<KeyboardTheme> = listOf(
    SYSTEM,
    LIGHT_THEME,
    DARK_THEME,
    HIGH_CONTRAST_LIGHT_THEME,
    HIGH_CONTRAST_DARK_THEME,
    SEPIA_THEME,
    SOLARIZED_DARK_THEME,
    MIDNIGHT_BLUE_THEME,
    MONOKAI_THEME,
    DARCULA_THEME,
  )

  val DEFAULT: KeyboardTheme = SYSTEM

  fun byId(id: String?): KeyboardTheme =
    ALL.firstOrNull { it.id == id } ?: DEFAULT
}
