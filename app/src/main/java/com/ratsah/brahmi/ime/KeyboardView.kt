package com.ratsah.brahmi.ime

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ratsah.brahmi.R

/**
 * Renders the keyboard. The view itself is a [FrameLayout] so it can host
 * three layers, in z-order:
 *
 *   1. The rows container (vertical [LinearLayout] of rows). Always present.
 *   2. An invisible scrim that catches "tap-outside" events when a long-press
 *      popup is showing.
 *   3. The popup view itself, anchored above (or below) the long-pressed key.
 *
 * Letter keys carry a Brahmi code point. When a [ScriptGuide] is active and
 * has a mapping for that code point, the key is drawn with two lines:
 *
 *   - large Brahmi character on top (what will actually be inserted)
 *   - small descendant-script glyph below (reading hint)
 *
 * For function keys (no code point or non-`Input` action) the view falls
 * back to a single-line label.
 */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context) : FrameLayout(context) {

  /** Called when the user taps any key. Set by the IME. */
  var onKeyAction: ((KeyAction) -> Unit)? = null

  private var guide: ScriptGuide = ScriptGuides.NONE
  private var page: KeyboardPage? = null
  private var topRow: List<Key>? = null

  private var theme: KeyboardTheme = KeyboardThemes.DEFAULT

  private val isSystemDark: Boolean
    get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

  /**
   * Colors currently in use. Recomputed on [setTheme] and whenever the
   * system dark-mode configuration changes (via [onConfigurationChanged]).
   */
  private var palette: KeyboardPalette = theme.paletteFor(isSystemDark)

  /** The keyboard is never taller than this fraction of the screen. */
  private val maxKeyboardHeightFraction: Float = 0.36f

  /** Maximum row count across all pages (consonants page = top row + 5). */
  private val maxRows: Int = 6

  private val outerVerticalPadding: Int = dp(0)
  private val keyMargin: Int = dp(2)
  private val keyCornerRadius: Float = dp(6).toFloat()

  /** Vertical breathing room inside each key, so glyphs (especially Brahmi
   *  matras with descenders) don't touch the rounded border. */
  private val keyInnerVerticalPadding: Int = dp(0)

  /**
   * Bundled Noto Sans Brahmi font used for the primary glyph on every
   * Brahmi key. Vendor system fonts on most Android devices ship with at
   * best partial Brahmi coverage - the Old Tamil extension (U+11035-37,
   * U+11070-75) in particular is usually missing - so we ship our own to
   * guarantee every Brahmi code point has a glyph. Lazy-loaded so we don't
   * hit the resource decoder before the view is ever drawn. `null` if the
   * font fails to load, in which case rendering falls back to the system
   * default (the previous behavior).
   */
  private val brahmiTypeface: Typeface? by lazy {
    ResourcesCompat.getFont(context, R.font.noto_sans_brahmi)
  }

  /**
   * Row height derived from the screen height so that the tallest page
   * (the consonants page, with [maxRows] rows) fits inside
   * [maxKeyboardHeightFraction] of the screen.
   */
  private val rowHeight: Int = run {
    val screenH = resources.displayMetrics.heightPixels
    val budget = (screenH * maxKeyboardHeightFraction).toInt() - outerVerticalPadding * 2
    (budget / maxRows).coerceAtLeast(dp(28))
  }

  // Text sizes scale with row height so the visual ratios stay constant.
  private val rowHeightDp: Float = rowHeight / resources.displayMetrics.density
  private val mainTextSp: Float = (rowHeightDp * 0.38f).coerceIn(11f, 22f)
  private val mainTextSmallSp: Float = mainTextSp * 0.64f
  private val hintTextSp: Float = mainTextSp * 0.45f

  /** Holds the keyboard rows. The outer FrameLayout overlays popups on top. */
  private val rowsContainer: LinearLayout = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(2), outerVerticalPadding, dp(2), outerVerticalPadding)
    isMotionEventSplittingEnabled = true
  }

  private var currentPopup: View? = null
  private var currentScrim: View? = null
  private var currentPressPreview: View? = null
  private var topRowView: View? = null
  private val pageRowViews: MutableList<View> = mutableListOf()

  init {
    // Paint the keyboard background on the outer FrameLayout (not just on
    // [rowsContainer]) so the navigation-bar inset strip below the rows
    // also shows the keyboard color in dark theme, Otherwise the nav-bar
    // area falls back to the IME window's default.
    setBackgroundColor(palette.background)
    // Width fills the IME window; height wraps the rows so the
    // FrameLayout has a natural size to report to the IME framework.
    addView(
      rowsContainer,
      LayoutParams(MATCH_PARENT, LayoutParams.WRAP_CONTENT),
    )

    // On Android 15+ (and mandatory for apps targeting Android 16) the IME
    // window draws edge-to-edge under the system navigation bar. Apply the
    // nav-bar bottom inset as bottom padding so the keys never sit behind
    // the gesture/nav bar.
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
      val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
      v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, navBar.bottom)
      insets
    }
  }

  /**
   * Pin the FrameLayout's measured height to the rows container so that
   * adding overlay popups never enlarges the IME window. Without this,
   * a WRAP_CONTENT popup with a large `topMargin`, or a `MATCH_PARENT`
   * scrim, can inflate the parent's measured height and the keyboard
   * suddenly takes up the whole screen.
   *
   * Also adds [paddingTop] + [paddingBottom] to the reported height so the
   * navigation-bar inset applied via the on-apply-window-insets listener is
   * accounted for in the IME window size.
   */
  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    val rowsHeight = rowsContainer.measuredHeight
    if (rowsHeight > 0) {
      setMeasuredDimension(measuredWidth, rowsHeight + paddingTop + paddingBottom)
    }
  }

  fun setGuide(guide: ScriptGuide) {
    if (this.guide == guide) return
    this.guide = guide
    rebuildTopRow()
    rebuildPageRows()
  }

  /**
   * Swap the active [KeyboardTheme]. Resolves the palette against the
   * current system dark-mode state and rebuilds every visible surface so
   * the new colors take effect immediately.
   */
  fun setTheme(theme: KeyboardTheme) {
    val newPalette = theme.paletteFor(isSystemDark)
    if (this.theme == theme && this.palette == newPalette) return
    this.theme = theme
    this.palette = newPalette
    setBackgroundColor(palette.background)
    rebuildTopRow()
    rebuildPageRows()
  }

  /** Palette resolved for the current system dark-mode state - lets the
   *  IME service tint its host window (nav bar, contrast enforcement) to
   *  match what the keyboard actually paints. */
  val activePalette: KeyboardPalette
    get() = palette

  /**
   * The "System default" theme depends on the system dark-mode flag; when
   * the user flips it, re-resolve the palette so we swap between light and
   * dark variants without having to hide and reshow the IME.
   */
  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    val newPalette = theme.paletteFor(isSystemDark)
    if (newPalette != palette) {
      palette = newPalette
      setBackgroundColor(palette.background)
      rebuildTopRow()
      rebuildPageRows()
    }
  }

  fun setPage(page: KeyboardPage) {
    if (this.page == page) return
    this.page = page
    rebuildPageRows()
  }

  /** Set (or clear with `null`) an extra row that's drawn above the page. */
  fun setTopRow(row: List<Key>?) {
    if (this.topRow == row) return
    this.topRow = row
    rebuildTopRow()
  }

  /**
   * Rebuild just the dynamic top vowel row, leaving every other key in
   * place.
   */
  private fun rebuildTopRow() {
    topRowView?.let { rowsContainer.removeView(it) }
    topRowView = null
    val keys = topRow ?: return
    val view = buildRow(keys)
    rowsContainer.addView(view, 0)
    topRowView = view
  }

  /**
   * Rebuild the page rows (everything *below* the top vowel row).
   * Called on page switches and guide changes - both rare actions
   * compared to per-keypress refreshes.
   */
  private fun rebuildPageRows() {
    dismissPopup()
    dismissPressPreview()
    for (view in pageRowViews) {
      rowsContainer.removeView(view)
    }
    pageRowViews.clear()
    val p = page ?: return
    for (rowKeys in p.rows) {
      val view = buildRow(rowKeys)
      rowsContainer.addView(view)
      pageRowViews.add(view)
    }
  }

  private fun buildRow(keys: List<Key>): LinearLayout {
    val row = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      layoutParams = LinearLayout.LayoutParams(
        MATCH_PARENT, rowHeight
      )
    }
    for (key in keys) {
      row.addView(buildKey(key))
    }
    return row
  }

  private fun buildKey(key: Key): View {
    val view = buildKeyView(key, accentOverride = null)
    val spanFillBase = ((key.weight - 1f) * 2f * keyMargin).coerceAtLeast(0f).toInt()
    val params = LinearLayout.LayoutParams(spanFillBase, MATCH_PARENT, key.weight)
    params.setMargins(keyMargin, keyMargin, keyMargin, keyMargin)
    view.layoutParams = params
    if (key.repeatable) {
      attachAutoRepeat(view, key.action)
    } else if (shouldShowPressPreview(key.action)) {
      // Repeatable keys (just backspace) own the touch listener for
      // auto-repeat; everything that outputs a character gets the
      // Gboard-style balloon preview instead.
      attachPressPreview(view, key)
    }
    if (key.popups.isNotEmpty()) {
      view.isLongClickable = true
      view.setOnLongClickListener { v ->
        showPopup(v, key.popups)
        true
      }
    } else if (key.longPressAction != null) {
      // Long-press on a key with no alternates routes to a separate
      // action (e.g. globe: tap = next IME, long-press = picker).
      val longPressAction = key.longPressAction
      view.isLongClickable = true
      view.setOnLongClickListener { v ->
        // Cancel the tap balloon if one is showing so the long-press
        // doesn't leave a stale preview behind.
        dismissPressPreview()
        dispatchAction(v, longPressAction)
        true
      }
    }
    return view
  }

  /**
   * True for actions whose visible output is a character: tapping such a
   * key inserts a glyph the user wants to confirm, so a press preview is
   * useful. Function keys (space, backspace, enter, page-switchers, IME
   * switcher) deliberately stay quiet - that matches Gboard's behavior
   * and avoids stray balloons on every space tap.
   */
  private fun shouldShowPressPreview(action: KeyAction): Boolean = when (action) {
    is KeyAction.Input, is KeyAction.Vowel -> true
    else -> false
  }

  /**
   * Mirror Gboard's "key preview popup": while the user holds the key
   * down, a small balloon hovers above (or below, if there's no room
   * above) showing the glyph being typed. Dismissed on ACTION_UP /
   * ACTION_CANCEL - including the cancel that fires when the finger
   * slides off the key, so the preview always tracks the visible press
   * state.
   *
   * Returning `false` from the touch listener keeps the framework's
   * normal click/long-click pipeline intact (the click listener still
   * fires `dispatchAction`, the long-click listener still opens any
   * alternates popup).
   */
  @SuppressLint("ClickableViewAccessibility")
  private fun attachPressPreview(view: View, key: Key) {
    view.setOnTouchListener { _, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> showPressPreview(view, key)
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dismissPressPreview()
      }
      false
    }
  }

  private fun showPressPreview(anchor: View, key: Key) {
    dismissPressPreview()

    val container = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      background = makePopupBackground()
      elevation = dp(8).toFloat()
      val pad = dp(8)
      setPadding(pad, dp(6), pad, dp(6))
    }

    // The preview shows the same glyph the key would type, scaled up so
    // it's visible above the user's fingertip.
    if (key.iconRes != null) {
      val iconSize = (rowHeight * 0.7f).toInt()
      container.addView(
        ImageView(context).apply {
          setImageResource(key.iconRes)
          setColorFilter(palette.iconTint)
          scaleType = ImageView.ScaleType.FIT_CENTER
          layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        })
    } else {
      container.addView(
        TextView(context).apply {
          text = key.label
          setTextColor(palette.keyTextColor)
          val tf = if (containsBrahmi(key.label)) brahmiTypeface else null
          setTypeface(tf, Typeface.BOLD)
          setTextSize(TypedValue.COMPLEX_UNIT_SP, mainTextSp * 1.6f)
          gravity = Gravity.CENTER
          includeFontPadding = false
        })
    }

    // Measure the balloon so we know where to place it. Width is at
    // least as wide as the underlying key so narrow glyphs (matras,
    // lone vowels) still appear centred over the press.
    val maxPreviewWidth = (width - dp(8) * 2).coerceAtLeast(dp(64))
    container.measure(
      MeasureSpec.makeMeasureSpec(maxPreviewWidth, MeasureSpec.AT_MOST),
      MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
    )
    val previewW = container.measuredWidth.coerceAtLeast(anchor.width)
    val previewH = container.measuredHeight

    // Position the balloon as a child of the keyboard FrameLayout, in
    // coordinates relative to it. Center horizontally on the key, then
    // clamp inside the keyboard so wide balloons don't overflow the
    // edge. Place above the key when there's room, otherwise below
    // (the top-row case).
    val anchorLoc = IntArray(2)
    anchor.getLocationInWindow(anchorLoc)
    val myLoc = IntArray(2)
    getLocationInWindow(myLoc)
    val anchorRelX = anchorLoc[0] - myLoc[0]
    val anchorRelY = anchorLoc[1] - myLoc[1]
    val gap = dp(2)

    var x = anchorRelX + (anchor.width - previewW) / 2
    x = x.coerceIn(0, (width - previewW).coerceAtLeast(0))
    var y = anchorRelY - previewH - gap
    if (y < 0) {
      y = anchorRelY + anchor.height + gap
    }

    val params = LayoutParams(previewW, previewH)
    params.leftMargin = x
    params.topMargin = y
    addView(container, params)
    currentPressPreview = container
  }

  private fun dismissPressPreview() {
    currentPressPreview?.let { removeView(it) }
    currentPressPreview = null
  }

  /**
   * Dispatch a key action and play the standard keyboard haptic on
   * [view]. The haptic respects the system's "Touch feedback" / "Vibrate
   * on keypress" toggle (Settings → Sound & vibration → Haptic feedback)
   * because [HapticFeedbackConstants.KEYBOARD_TAP] is dispatched without
   * `FLAG_IGNORE_GLOBAL_SETTING`. All key fire sites - main click,
   * auto-repeat ticks, and long-press popup items - go through here so
   * haptic and action stay in lockstep.
   */
  private fun dispatchAction(view: View, action: KeyAction) {
    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    onKeyAction?.invoke(action)
  }

  /**
   * Wire a key for Gboard-style auto-repeat: pressing the key fires
   * [action] once immediately and then keeps re-firing while the user
   * keeps their finger down.
   *
   *  - Initial wait of [INITIAL_REPEAT_DELAY_MS] before the second event
   *    so a normal tap fires exactly once.
   *  - After that, repeats every [REPEAT_INTERVAL_MS] until ACTION_UP /
   *    ACTION_CANCEL - including the case where the finger slides off
   *    the key, which sends ACTION_CANCEL to the original view.
   *
   * The view's existing click listener (set in [buildKeyView]) is cleared
   * so we don't fire the action twice on a normal tap (once in
   * ACTION_DOWN here, once in `performClick` on ACTION_UP). The view
   * stays clickable so the ripple/pressed visuals still play.
   */
  @SuppressLint("ClickableViewAccessibility")
  private fun attachAutoRepeat(view: View, action: KeyAction) {
    view.setOnClickListener(null)
    val handler = Handler(Looper.getMainLooper())
    var repeater: Runnable? = null

    fun stop() {
      repeater?.let { handler.removeCallbacks(it) }
      repeater = null
    }

    view.setOnTouchListener { _, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          dispatchAction(view, action)
          val r = object : Runnable {
            override fun run() {
              dispatchAction(view, action)
              handler.postDelayed(this, REPEAT_INTERVAL_MS)
            }
          }
          repeater = r
          handler.postDelayed(r, INITIAL_REPEAT_DELAY_MS)
        }

        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stop()
      }
      // Don't consume - let the framework drive the pressed/ripple state.
      false
    }
  }

  /** Build a key's content (background + 1 or 2 text lines). Used by both
   *  the main keys and the items inside long-press popups. */
  private fun buildKeyView(key: Key, accentOverride: Boolean?): LinearLayout {
    val descendantHint = key.hint ?: key.brahmiCodePoint?.let { guide.labelFor(it) }
    val accent = accentOverride ?: key.accent

    val container = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      background = makeKeyBackground(accent, hasPopup = key.popups.isNotEmpty())
      // Inner padding so glyphs don't touch the rounded border.
      setPadding(0, keyInnerVerticalPadding, 0, keyInnerVerticalPadding)
      isClickable = true
      isFocusable = true
      setOnClickListener { dispatchAction(it, key.action) }
    }

    // 1. Primary glyph (top): Brahmi character or function-key icon.
    if (key.iconRes != null) {
      // Function key with a vector icon (e.g. the IME-switcher globe).
      // Tinted to match the current text color so it stays monochrome
      // and matches the active theme.
      val iconSize = (rowHeight * 0.5f).toInt()
      container.addView(
        ImageView(context).apply {
          setImageResource(key.iconRes)
          setColorFilter(palette.iconTint)
          scaleType = ImageView.ScaleType.FIT_CENTER
          layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        })
    } else {
      container.addView(
        TextView(context).apply {
          text = key.label // the Brahmi character - what gets inserted
          // Size based on code-point count, not Java char count, so a
          // Brahmi syllable (2 surrogate-pair code points = 4 chars)
          // still renders large.
          val codePoints = key.label.codePointCount(0, key.label.length)
          val sp = if (codePoints > 2) mainTextSmallSp else mainTextSp
          setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
          setTextColor(palette.keyTextColor)
          // Use the bundled Noto Sans Brahmi whenever the label contains
          // any character from the Brahmi block (U+11000-U+1107F). This
          // covers single-codepoint letter keys *and* multi-codepoint
          // labels like the space bar's "Brāhmī" word. ASCII labels
          // ("ABC", "?123", …) fall back to the system UI typeface.
          val typeface = if (containsBrahmi(key.label)) brahmiTypeface else null
          setTypeface(typeface, Typeface.BOLD)
          gravity = Gravity.CENTER_HORIZONTAL
          includeFontPadding = false
          layoutParams = LinearLayout.LayoutParams(
            MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
          )
        })
    }

    // 2. Secondary glyph (bottom): descendant-script hint, if any.
    if (descendantHint != null) {
      container.addView(
        TextView(context).apply {
          text = descendantHint
          setTextSize(TypedValue.COMPLEX_UNIT_SP, hintTextSp)
          setTextColor(palette.hintTextColor)
          setTypeface(null, Typeface.BOLD)
          gravity = Gravity.CENTER
          includeFontPadding = false
          layoutParams = LinearLayout.LayoutParams(
            MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
          )
        })
    }

    return container
  }

  private fun makeKeyBackground(accent: Boolean, hasPopup: Boolean): RippleDrawable {
    val fill = if (accent) palette.accentKeyFill else palette.keyFill
    val base = GradientDrawable().apply {
      cornerRadius = keyCornerRadius
      setColor(fill)
      setStroke(dp(palette.strokeWidthDp), palette.keyStroke)
    }
    val content: Drawable = if (hasPopup) {
      // Tiny circular dot in the top-right corner that hints at the
      // existence of long-press alternates.
      val dot = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(palette.popupDotColor)
      }
      val dotSize = dp(4)
      val dotInset = dp(5)
      LayerDrawable(arrayOf(base, dot)).apply {
        setLayerSize(1, dotSize, dotSize)
        setLayerGravity(1, Gravity.TOP or Gravity.END)
        setLayerInsetTop(1, dotInset)
        setLayerInsetEnd(1, dotInset)
      }
    } else {
      base
    }
    return RippleDrawable(ColorStateList.valueOf(palette.rippleColor), content, null)
  }

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

  // -- Long-press popup ----------------------------------------------------
  private fun showPopup(anchor: View, items: List<Key>) {
    if (items.isEmpty()) return
    dismissPopup()
    // Long-press took over from the simple key tap - the press balloon
    // would just clutter the alternates popup, so retire it.
    dismissPressPreview()

    val scrim = View(context).apply {
      isClickable = true
      setBackgroundColor(SCRIM_DIM_COLOR)
      setOnClickListener { dismissPopup() }
    }
    addView(
      scrim,
      LayoutParams(MATCH_PARENT, MATCH_PARENT),
    )
    currentScrim = scrim

    val popupView = buildPopupView(
      items,
      itemWidth = anchor.width,
      itemHeight = anchor.height,
    )
    // Measure the popup so we know how to position it. Width is capped to
    // the keyboard width (minus a small horizontal margin) so popups wider
    // than the screen become horizontally scrollable instead of overflowing.
    val popupSideMargin = dp(8)
    val maxPopupWidth = (width - popupSideMargin * 2).coerceAtLeast(dp(64))
    popupView.measure(
      MeasureSpec.makeMeasureSpec(maxPopupWidth, MeasureSpec.AT_MOST),
      MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
    )
    val popupW = popupView.measuredWidth
    val popupH = popupView.measuredHeight

    // Anchor's position in the window, relative to the keyboard view.
    val anchorLoc = IntArray(2)
    anchor.getLocationInWindow(anchorLoc)
    val myLoc = IntArray(2)
    getLocationInWindow(myLoc)
    val anchorRelX = anchorLoc[0] - myLoc[0]
    val anchorRelY = anchorLoc[1] - myLoc[1]

    // Center the popup horizontally over the key, then clamp inside the
    // keyboard. Place above the key by default; if there's not enough
    // room above, place below.
    var x = anchorRelX + (anchor.width - popupW) / 2
    x = x.coerceIn(0, (width - popupW).coerceAtLeast(0))
    var y = anchorRelY - popupH - dp(4)
    if (y < 0) {
      y = anchorRelY + anchor.height + dp(4)
    }

    val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    params.leftMargin = x
    params.topMargin = y
    addView(popupView, params)
    currentPopup = popupView
  }

  private fun buildPopupView(items: List<Key>, itemWidth: Int, itemHeight: Int): View {
    // The horizontal row of items. No padding/background here - those go on
    // the outer scroll container so the rounded background isn't clipped
    // mid-scroll.
    val row = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
    }

    for (item in items) {
      val itemView = buildKeyView(item, accentOverride = false)
      // Override the click so we also dismiss the popup after the
      // action fires.
      itemView.setOnClickListener {
        dispatchAction(itemView, item.action)
        dismissPopup()
      }
      // Match the anchor's visible dimensions and use the same margin as
      // main keys, so each popup entry is a pixel-for-pixel stand-in for
      // the key the user long-pressed.
      val params = LinearLayout.LayoutParams(itemWidth, itemHeight)
      params.setMargins(keyMargin, keyMargin, keyMargin, keyMargin)
      itemView.layoutParams = params
      row.addView(itemView)
    }

    // Wrap in a HorizontalScrollView so popups wider than the keyboard
    // (e.g. the Old Tamil block, with 8 alternates) become scrollable
    // instead of being clipped or pushed off-screen.
    return HorizontalScrollView(context).apply {
      isHorizontalScrollBarEnabled = false
      overScrollMode = OVER_SCROLL_NEVER
      background = makePopupBackground()
      // Small padding so the scroll container's rounded background
      // doesn't clip the outermost popup keys' own borders.
      setPadding(dp(4), dp(4), dp(4), dp(4))
      elevation = dp(6).toFloat()
      addView(
        row,
        LayoutParams(
          LayoutParams.WRAP_CONTENT,
          LayoutParams.WRAP_CONTENT,
        ),
      )
    }
  }

  private fun makePopupBackground(): GradientDrawable {
    return GradientDrawable().apply {
      cornerRadius = dp(8).toFloat()
      setColor(palette.popupBackground)
      setStroke(dp(palette.strokeWidthDp), palette.popupStroke)
    }
  }

  private fun dismissPopup() {
    currentPopup?.let { removeView(it) }
    currentScrim?.let { removeView(it) }
    currentPopup = null
    currentScrim = null
  }

  companion object {
    /** How long to wait after the initial press before auto-repeat begins. */
    private const val INITIAL_REPEAT_DELAY_MS: Long = 400L

    /** Interval between auto-repeated events while the key is held. */
    private const val REPEAT_INTERVAL_MS: Long = 50L

    /**
     * Overlay color painted on the scrim behind the long-press popup.
     * ~40% opaque black - the standard Material "modal scrim" opacity.
     * Kept theme-independent because the popup itself already stands out
     * via its own background, border, and elevation in every palette.
     */
    private const val SCRIM_DIM_COLOR: Int = 0x66000000

    /**
     * Returns `true` if [text] contains any code point from the Unicode
     * Brahmi block (U+11000–U+1107F). Used to decide whether a key's
     * label should be rendered with the bundled Noto Sans Brahmi font.
     */
    private fun containsBrahmi(text: String): Boolean {
      var i = 0
      while (i < text.length) {
        val cp = text.codePointAt(i)
        if (cp in 0x11000..0x1107F) return true
        i += Character.charCount(cp)
      }
      return false
    }
  }
}
