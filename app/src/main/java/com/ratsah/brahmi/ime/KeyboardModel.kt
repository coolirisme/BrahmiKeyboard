package com.ratsah.brahmi.ime

import androidx.annotation.DrawableRes

/**
 * Action triggered when a key is pressed. Adding a new behavior to the
 * keyboard is just a matter of adding a new variant here and handling it
 * in [BrahmiInputMethodService.handleAction].
 */
sealed class KeyAction {
  /** Commit [text] to the editor (any string, including multi-codepoint). */
  data class Input(val text: String) : KeyAction()

  /** Switch the keyboard to another page (see [BrahmiLayouts]). */
  data class SwitchPage(val pageId: String) : KeyAction()

  /**
   * A key from the top "vowel row".
   *
   *  - With **no active consonant**, inserts the [independent] vowel.
   *  - With **an active consonant**, replaces it with `consonant + matra`
   *    so the user gets the combined syllable. If [matra] is `null` the
   *    key represents the inherent /a/, in which case the consonant is
   *    left untouched.
   */
  data class Vowel(val matra: Int?, val independent: Int) : KeyAction()

  object Space : KeyAction()
  object Delete : KeyAction()
  object Enter : KeyAction()
  object SwitchIme : KeyAction()
  object ShowImePicker : KeyAction()
}

/**
 * A single key in the on-screen keyboard.
 *
 * @param label default text drawn on the key - used as-is unless a
 *              [ScriptGuide] is active and provides an override for
 *              [brahmiCodePoint].
 * @param action what happens when the key is tapped.
 * @param weight horizontal weight inside the row (1 = a normal letter key).
 * @param accent whether this is a "function" key (rendered with a darker tint).
 * @param brahmiCodePoint the Brahmi code point this key represents, if any.
 *              When set, the active [ScriptGuide] can replace [label] with
 *              the corresponding descendant-script glyph.
 * @param hint  pre-computed descendant-script hint that overrides any guide
 *              lookup. Used for keys whose label is composed of multiple
 *              code points (e.g. consonant + matra in the top vowel row).
 * @param popups optional list of alternate keys shown in a floating overlay
 *              when the user long-presses this key. Tapping any popup item
 *              dispatches its [KeyAction] and dismisses the overlay.
 * @param longPressAction optional action fired when the user long-presses
 *              this key. Mutually exclusive with [popups]: a key that has
 *              alternates uses the popup, a key without alternates can use
 *              this to give the long-press a different behavior than the
 *              tap (e.g. globe = next IME on tap, picker on long-press).
 * @param iconRes optional drawable resource used as the key's main glyph
 *              instead of [label]. Drawn as a tinted [android.widget.ImageView],
 *              so it always matches the active theme. Used for function
 *              keys like the IME switcher (globe).
 * @param repeatable when `true`, holding the key down auto-fires its
 *              [action] repeatedly (Gboard-style). Used for backspace.
 */
data class Key(
  val label: String,
  val action: KeyAction,
  val weight: Float = 1f,
  val accent: Boolean = false,
  val brahmiCodePoint: Int? = null,
  val hint: String? = null,
  val popups: List<Key> = emptyList(),
  val longPressAction: KeyAction? = null,
  @param:DrawableRes val iconRes: Int? = null,
  val repeatable: Boolean = false,
)

/** A whole keyboard page: just a stack of rows. */
data class KeyboardPage(
  val id: String,
  val rows: List<List<Key>>,
)
