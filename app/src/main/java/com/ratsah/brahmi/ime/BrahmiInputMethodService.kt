package com.ratsah.brahmi.ime

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.WindowCompat

/**
 * The Brahmi script Input Method Service.
 *
 * Responsibilities:
 *  - Build a [KeyboardView] for the current page.
 *  - Maintain a small piece of state - [activeConsonant] - and use it to
 *    drive a dynamic top "vowel row": tapping a consonant arms it, then
 *    tapping a key in the top row replaces it with the corresponding
 *    `consonant + matra` syllable.
 *  - Translate every [KeyAction] coming from the view into edits on the
 *    active text field.
 *
 * All keyboard *content* lives in [BrahmiLayouts]; this class only handles
 * the input plumbing.
 */
class BrahmiInputMethodService : InputMethodService() {

  private var keyboardView: KeyboardView? = null
  private var currentPageId: String = BrahmiLayouts.PAGE_CONSONANTS
  private var guide: ScriptGuide = ScriptGuides.NONE
  private var theme: KeyboardTheme = KeyboardThemes.DEFAULT

  /**
   * Code point of the consonant the user just typed. While this is set,
   * the top vowel row shows `consonant + matra` combined forms instead of
   * the bare independent vowels. Cleared on virtually every other action.
   */
  private var activeConsonant: Int? = null

  /**
   * Watches the IME's user-facing settings in real time. The setup
   * activity runs in this same process, so any preference change fires
   * immediately and we push the new value to the live keyboard view -
   * no need to hide and reshow the IME.
   */
  private val prefsListener =
    SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
      when (key) {
        ScriptPreferences.KEY_GUIDE_ID -> applyGuideFromPreferences()
        ScriptPreferences.KEY_THEME_ID -> applyThemeFromPreferences()
      }
    }

  override fun onCreate() {
    super.onCreate()
    ScriptPreferences.registerListener(this, prefsListener)
  }

  override fun onDestroy() {
    ScriptPreferences.unregisterListener(this, prefsListener)
    super.onDestroy()
  }

  override fun onCreateInputView(): View {
    guide = ScriptPreferences.getGuide(this)
    theme = ScriptPreferences.getTheme(this)
    val view = KeyboardView(this).also { keyboardView = it }
    view.onKeyAction = ::handleAction
    view.setTheme(theme)
    view.setGuide(guide)
    view.setPage(BrahmiLayouts.byId(currentPageId))
    refreshTopRow()
    return view
  }

  // Always show the soft keyboard, even when a hardware keyboard is
  // reported. for Android 15+
  override fun onEvaluateInputViewShown(): Boolean = true

  override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
    super.onStartInputView(info, restarting)
    // Order matters: apply theme first so the nav-bar styling below
    // uses the fresh palette instead of the previous session's colors.
    applyThemeFromPreferences()
    applyWindowStyling()
    // Always start a fresh editing session on the consonants page,
    // and pick up any change the user made to the script-guide setting.
    currentPageId = BrahmiLayouts.PAGE_CONSONANTS
    activeConsonant = null
    applyGuideFromPreferences()
    keyboardView?.setPage(BrahmiLayouts.byId(currentPageId))
  }

  /**
   * Reload the active [ScriptGuide] from [ScriptPreferences] and push it
   * through to the live [KeyboardView]. Safe to call any time -
   * [KeyboardView.setGuide] no-ops when the guide hasn't actually
   * changed, and [refreshTopRow] uses [keyboardView]'s null-safe call so
   * it's a no-op before the view exists.
   */
  private fun applyGuideFromPreferences() {
    guide = ScriptPreferences.getGuide(this)
    keyboardView?.setGuide(guide)
    refreshTopRow()
  }

  /**
   * Reload the active [KeyboardTheme] from [ScriptPreferences] and push
   * it through to the live [KeyboardView]. Also re-applies the IME
   * window styling so the system navigation-bar band tracks the new
   * palette (relevant for the high-contrast themes, which paint the
   * keyboard pure white or pure black regardless of system dark mode).
   */
  private fun applyThemeFromPreferences() {
    theme = ScriptPreferences.getTheme(this)
    keyboardView?.setTheme(theme)
    applyWindowStyling()
  }

  /**
   * Style the IME's host window so the system navigation bar blends with
   * the keyboard background.
   *
   *  - On Android 15+ the IME window draws edge-to-edge under the nav
   *    bar, but the system still paints a [Window.setNavigationBarColor]
   *    band behind the nav-bar icons. With the default light Material
   *    theme that band is white, which leaks through in dark mode.
   *  - Disabling [Window.setNavigationBarContrastEnforced] prevents the
   *    system from adding its own translucent contrast scrim on top.
   *  - The nav-bar icon color (light vs dark) is chosen from the active
   *    palette's [KeyboardPalette.isDarkKeyboard] flag, so users on a
   *    light theme with dark system mode still see legible icons.
   *
   * Called on every IME show and every theme change so it tracks both
   * day/night mode changes and the user's theme selection.
   */
  private fun applyWindowStyling() {
    val w = window?.window ?: return
    val isSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
      Configuration.UI_MODE_NIGHT_YES
    val palette = keyboardView?.activePalette ?: theme.paletteFor(isSystemDark)
    @Suppress("DEPRECATION")
    w.navigationBarColor = palette.background
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      w.isNavigationBarContrastEnforced = false
    }
    WindowCompat.getInsetsController(w, w.decorView)
      .isAppearanceLightNavigationBars = !palette.isDarkKeyboard
  }

  /** Recompute the top vowel row based on [activeConsonant] and [guide]. */
  private fun refreshTopRow() {
    // The vowel row only makes sense above the consonants page.
    val row = if (currentPageId == BrahmiLayouts.PAGE_CONSONANTS) {
      BrahmiLayouts.buildVowelRow(activeConsonant, guide)
    } else {
      null
    }
    keyboardView?.setTopRow(row)
  }

  private fun handleAction(action: KeyAction) {
    val ic = currentInputConnection ?: return
    when (action) {
      is KeyAction.Input -> {
        ic.commitText(action.text, 1)
        activeConsonant = brahmiConsonantOf(action.text)
        refreshTopRow()
      }

      is KeyAction.Vowel -> {
        handleVowel(action)
        activeConsonant = null
        refreshTopRow()
      }

      KeyAction.Space -> {
        ic.commitText(" ", 1)
        activeConsonant = null
        refreshTopRow()
      }

      KeyAction.Delete -> {
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
          ic.commitText("", 1)
        } else {
          // Delete one Unicode code point - Brahmi characters are
          // surrogate pairs, so deleting one Java char would leave
          // an orphan surrogate.
          ic.deleteSurroundingTextInCodePoints(1, 0)
        }
        activeConsonant = null
        refreshTopRow()
      }

      KeyAction.Enter -> {
        // Respect the editor's IME action if it has one (Send, Search,
        // Next, ...); otherwise fall back to a literal newline.
        val editorInfo = currentInputEditorInfo
        val actionId = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: 0
        val hasNoEnterAction =
          (editorInfo?.imeOptions?.and(EditorInfo.IME_FLAG_NO_ENTER_ACTION) ?: 0) != 0
        if (actionId != EditorInfo.IME_ACTION_NONE &&
          actionId != EditorInfo.IME_ACTION_UNSPECIFIED &&
          !hasNoEnterAction
        ) {
          ic.performEditorAction(actionId)
        } else {
          sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        activeConsonant = null
        refreshTopRow()
      }

      is KeyAction.SwitchPage -> {
        currentPageId = action.pageId
        keyboardView?.setPage(BrahmiLayouts.byId(currentPageId))
        refreshTopRow()
        // Note: [activeConsonant] is intentionally preserved across
        // page switches, so the user can flip to numbers/vowels and
        // back without losing the syllable context.
      }

      KeyAction.SwitchIme -> {
        if (!switchToNextInputMethod(false)) {
          showImePicker()
        }
      }
      KeyAction.ShowImePicker -> showImePicker()
    }
  }

  private fun showImePicker() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showInputMethodPicker()
  }

  /**
   * Apply a [KeyAction.Vowel] to the active editor.
   *
   *  - **No active consonant** → insert the independent vowel.
   *  - **Inherent /a/ (matra == null)** → no change to the editor: the
   *    bare consonant already represents `consonant + a`.
   *  - **Otherwise** → delete the active consonant and commit
   *    `consonant + matra` as one combined form, but only after verifying
   *    the consonant is still right before the cursor (the user might
   *    have moved the caret manually). If the verification fails, fall
   *    back to inserting just the matra so we don't delete the wrong
   *    character.
   */
  private fun handleVowel(action: KeyAction.Vowel) {
    val ic = currentInputConnection ?: return
    val active = activeConsonant
    if (active == null) {
      ic.commitText(String(Character.toChars(action.independent)), 1)
      return
    }
    val matra = action.matra ?: return  // inherent /a/, nothing to do
    val consonantStr = String(Character.toChars(active))
    val combinedStr = consonantStr + String(Character.toChars(matra))
    val before = ic.getTextBeforeCursor(consonantStr.length, 0)?.toString()
    if (before == consonantStr) {
      ic.deleteSurroundingTextInCodePoints(1, 0)
      ic.commitText(combinedStr, 1)
    } else {
      // Cursor moved away from the consonant we expected. Don't delete
      // anything we don't recognise - just insert the matra alone.
      ic.commitText(String(Character.toChars(matra)), 1)
    }
  }

  /**
   * If [text] is exactly one Brahmi consonant code point (U+11013-U+11034),
   * return that code point so it can be tracked as the active consonant.
   * Anything else (vowels, matras, signs, virama, digits, punctuation)
   * returns `null`, which clears any previous active state.
   */
  private fun brahmiConsonantOf(text: String): Int? {
    if (text.isEmpty()) return null
    val firstCp = text.codePointAt(0)
    if (Character.charCount(firstCp) != text.length) return null // multi-cp string
    return if (firstCp in 0x11013..0x11034) firstCp else null
  }
}
