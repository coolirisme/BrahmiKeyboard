package com.ratsah.brahmi.ime

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Tiny wrapper around [SharedPreferences] for the IME's user-facing
 * settings: the active [ScriptGuide] and the active [KeyboardTheme].
 */
object ScriptPreferences {
  private const val PREFS_NAME = "brahmi_keyboard_prefs"

  /**
   * Preference key for the active script-guide id. Public so the IME
   * service can filter [SharedPreferences.OnSharedPreferenceChangeListener]
   * callbacks down to the one preference it cares about.
   */
  const val KEY_GUIDE_ID = "script_guide_id"

  /** Preference key for the active [KeyboardTheme] id. */
  const val KEY_THEME_ID = "keyboard_theme_id"

  private fun prefs(context: Context): SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun getGuide(context: Context): ScriptGuide {
    val id = prefs(context).getString(KEY_GUIDE_ID, ScriptGuides.DEFAULT.id)
    return ScriptGuides.byId(id ?: ScriptGuides.DEFAULT.id)
  }

  fun getGuideId(context: Context): String =
    prefs(context).getString(KEY_GUIDE_ID, ScriptGuides.DEFAULT.id) ?: ScriptGuides.DEFAULT.id

  fun setGuideId(context: Context, id: String) {
    prefs(context).edit { putString(KEY_GUIDE_ID, id) }
  }

  fun getTheme(context: Context): KeyboardTheme =
    KeyboardThemes.byId(prefs(context).getString(KEY_THEME_ID, KeyboardThemes.DEFAULT.id))

  fun getThemeId(context: Context): String =
    prefs(context).getString(KEY_THEME_ID, KeyboardThemes.DEFAULT.id) ?: KeyboardThemes.DEFAULT.id

  fun setThemeId(context: Context, id: String) {
    prefs(context).edit { putString(KEY_THEME_ID, id) }
  }

  /**
   * Subscribe [listener] to live updates of any preference value. The
   * caller is responsible for calling [unregisterListener] later (e.g.
   * in `onDestroy`) - `SharedPreferences` keeps a strong reference to
   * the listener and would otherwise leak it.
   */
  fun registerListener(
    context: Context,
    listener: SharedPreferences.OnSharedPreferenceChangeListener,
  ) {
    prefs(context).registerOnSharedPreferenceChangeListener(listener)
  }

  fun unregisterListener(
    context: Context,
    listener: SharedPreferences.OnSharedPreferenceChangeListener,
  ) {
    prefs(context).unregisterOnSharedPreferenceChangeListener(listener)
  }
}
