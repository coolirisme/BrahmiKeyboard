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
import android.widget.PopupWindow
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

  /**
   * Fraction of the current window height the keyboard is allowed to
   * occupy. Landscape windows are much shorter than portrait, so we
   * give the keyboard a bigger slice there to keep keys tappable
   * without eating the entire text field on rotate/unfold.
   */
  private val portraitHeightFraction: Float = 0.36f
  private val landscapeHeightFraction: Float = 0.55f

  /**
   * On wide windows (unfolded foldable landscape, tablet, ChromeOS
   * free-form) a full-width keyboard makes each key absurdly wide and
   * out of thumb reach. When the current window is wider than
   * [wideWindowThresholdDp], the rows container is capped to
   * [maxKeyboardWidthDp] and centered - the extra strip on each side
   * keeps painting the keyboard palette so it still looks like one
   * continuous surface.
   */
  private val wideWindowThresholdDp: Int = 720
  private val maxKeyboardWidthDp: Int = 720

  /** Maximum row count across all pages (consonants page = top row + 5). */
  private val maxRows: Int = 6

  /** Absolute row-height bounds. Prevent absurdly tall keys on giant
   *  tablets and absurdly short keys on phone landscape / split-screen. */
  private val minRowHeightPx: Int get() = dp(36)
  private val maxRowHeightPx: Int get() = dp(56)

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
   * Row height derived from the current window configuration. Recomputed
   * on [onConfigurationChanged] so rotate/fold/resize all resize keys
   * correctly without needing a fresh IME view. Backed by a `var` (not a
   * one-shot construction-time snapshot) so subsequent row builds pick
   * up the new value.
   */
  private var rowHeight: Int = computeRowHeight()

  // Text sizes scale with row height so the visual ratios stay constant.
  // Using computed getters (not one-shot vals) so a rebuild after a
  // config change automatically picks up the new [rowHeight].
  private val rowHeightDp: Float get() = rowHeight / resources.displayMetrics.density
  private val mainTextSp: Float get() = (rowHeightDp * 0.38f).coerceIn(11f, 22f)
  private val mainTextSmallSp: Float get() = mainTextSp * 0.64f
  private val hintTextSp: Float get() = mainTextSp * 0.45f

  /**
   * Row-height budget for the current [resources] configuration.
   *
   * Uses [Configuration.screenHeightDp] (the current window height, not
   * the physical display) so free-form / split-screen windows on
   * ChromeOS and multi-window on Android also get a keyboard sized for
   * the space they actually have. The landscape branch bumps the
   * fraction so short landscape windows still get tappable keys.
   */
  private fun computeRowHeight(): Int {
    val config = resources.configuration
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val fraction = if (isLandscape) landscapeHeightFraction else portraitHeightFraction
    val screenHeightPx = (config.screenHeightDp * resources.displayMetrics.density).toInt()
    val budget = (screenHeightPx * fraction).toInt() - outerVerticalPadding * 2
    return (budget / maxRows).coerceIn(minRowHeightPx, maxRowHeightPx)
  }

  /**
   * Width the rows container should occupy. Full-window on phones and
   * small tablets; capped and centered on wide windows so keys stay in
   * reachable thumb range on unfolded foldables, landscape tablets,
   * and free-form ChromeOS windows.
   */
  private fun computeRowsContainerWidth(): Int {
    val screenWidthDp = resources.configuration.screenWidthDp
    return if (screenWidthDp > wideWindowThresholdDp) dp(maxKeyboardWidthDp)
    else MATCH_PARENT
  }

  /** Holds the keyboard rows. The outer FrameLayout overlays popups on top. */
  private val rowsContainer: LinearLayout = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(2), outerVerticalPadding, dp(2), outerVerticalPadding)
    isMotionEventSplittingEnabled = true
  }

  private var currentPopup: View? = null
  private var currentScrim: View? = null
  private var currentPressPreview: PopupWindow? = null
  private var topRowView: View? = null
  private val pageRowViews: MutableList<View> = mutableListOf()

  /**
   * Recycled views for the top vowel row.
   */
  private val topRowSlots: MutableList<TopRowSlot> = mutableListOf()

  /**
   * Persistent view holder for one slot in the recycled top vowel row.
   */
  private class TopRowSlot(
    val container: LinearLayout,
    val primary: TextView,
    val hint: TextView,
  ) {
    var currentKey: Key? = null
  }

  init {
    // Paint the keyboard background on the outer FrameLayout (not just on
    // [rowsContainer]) so the navigation-bar inset strip below the rows
    // also shows the keyboard color in dark theme, Otherwise the nav-bar
    // area falls back to the IME window's default.
    setBackgroundColor(palette.background)
    // Rows container width follows the current window class: full-width
    // on phone-class windows, capped + centered on wide (tablet /
    // unfolded foldable / ChromeOS free-form) windows.
    addView(
      rowsContainer,
      LayoutParams(
        computeRowsContainerWidth(),
        LayoutParams.WRAP_CONTENT,
      ).apply { gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP },
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
    // Page rows carry guide-derived hints and need a fresh build.
    rebuildPageRows()
    // Re-derive the top row's hints against the new guide.
    renderTopRow()
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
    // Slot backgrounds bake the palette into their RippleDrawable at
    // build time; force a fresh top-row structure to pick up the new
    // colors.
    invalidateTopRow()
    rebuildPageRows()
  }

  /** Palette resolved for the current system dark-mode state - lets the
   *  IME service tint its host window (nav bar, contrast enforcement) to
   *  match what the keyboard actually paints. */
  val activePalette: KeyboardPalette
    get() = palette

  /**
   * Reacts to every window-configuration change the framework tells us
   * about - rotation, fold/unfold, split-screen resize, ChromeOS
   * free-form resize, day/night mode flip, etc. Three things can move:
   *
   *  1. The palette, when the "System default" theme resolves to a new
   *     dark-mode variant.
   *  2. [rowHeight], when the window height changes (rotation, fold,
   *     resize, or an orientation flip that toggles the portrait vs
   *     landscape height fraction). Rebuilding all rows picks up the
   *     new value through the [rowHeightDp] getter.
   *  3. The rows container width, when the window crosses the
   *     [wideWindowThresholdDp] boundary - so a phone rotated into
   *     landscape can promote to a centered narrow keyboard on a
   *     large-screen device, and an unfolded foldable can collapse
   *     back to full-width when refolded.
   *
   * All three refreshes are coalesced into a single rebuild pass so
   * the view swap only happens once per config change.
   */
  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)

    var dirty = false

    val newPalette = theme.paletteFor(isSystemDark)
    if (newPalette != palette) {
      palette = newPalette
      setBackgroundColor(palette.background)
      dirty = true
    }

    val newRowHeight = computeRowHeight()
    if (newRowHeight != rowHeight) {
      rowHeight = newRowHeight
      dirty = true
    }

    val newContainerWidth = computeRowsContainerWidth()
    val lp = rowsContainer.layoutParams as LayoutParams
    if (lp.width != newContainerWidth) {
      lp.width = newContainerWidth
      lp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
      rowsContainer.layoutParams = lp
      dirty = true
    }

    if (dirty) {
      invalidateTopRow()
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
    renderTopRow()
  }

  /**
   * Reconcile the top vowel row with the current [topRow] list, reusing
   * existing slot views when the structure hasn't changed.
   *
   * A "structural" change (theme, guide, configuration, slot count)
   * still needs a fresh build, and those callers go through
   * [invalidateTopRow] first so this method rebuilds from scratch. The
   * common case - the IME service handing us a new list of 10 vowel
   * keys after a keystroke - takes the fast path: update text, hint
   * text/visibility, colors, and the slot's [TopRowSlot.currentKey]
   * pointer, then let the framework re-measure just the TextViews
   * whose contents changed.
   */
  private fun renderTopRow() {
    val keys = topRow
    if (keys.isNullOrEmpty()) {
      topRowView?.let { rowsContainer.removeView(it) }
      topRowView = null
      topRowSlots.clear()
      return
    }
    if (topRowView == null || topRowSlots.size != keys.size) {
      buildTopRowStructure(keys.size)
    }
    for (i in keys.indices) {
      applyKeyToSlot(topRowSlots[i], keys[i])
    }
  }

  /**
   * Discard the top-row structure so the next [renderTopRow] rebuilds
   * it from scratch. Used when the palette, guide, row height, or
   * container width have changed and simply re-applying content isn't
   * enough (slot backgrounds and layout params bake those in at build
   * time).
   */
  private fun invalidateTopRow() {
    topRowView?.let { rowsContainer.removeView(it) }
    topRowView = null
    topRowSlots.clear()
    renderTopRow()
  }

  private fun buildTopRowStructure(slotCount: Int) {
    topRowView?.let { rowsContainer.removeView(it) }
    topRowSlots.clear()
    val row = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, rowHeight)
    }
    repeat(slotCount) {
      val slot = buildTopRowSlot()
      val lp = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply {
        setMargins(keyMargin, keyMargin, keyMargin, keyMargin)
      }
      row.addView(slot.container, lp)
      topRowSlots.add(slot)
    }
    rowsContainer.addView(row, 0)
    topRowView = row
  }

  /**
   * Build the persistent view tree for one vowel-row slot. Listeners
   * are attached exactly once here and read the current key back from
   * [TopRowSlot.currentKey], so per-keystroke updates never have to
   * allocate a fresh lambda.
   */
  @SuppressLint("ClickableViewAccessibility")
  private fun buildTopRowSlot(): TopRowSlot {
    val primary = TextView(context).apply {
      // Vowel-row labels are always Brahmi (independent vowel or
      // consonant + matra), so the bundled Noto Sans Brahmi is
      // unconditionally the right typeface here.
      setTypeface(brahmiTypeface, Typeface.BOLD)
      gravity = Gravity.CENTER_HORIZONTAL
      includeFontPadding = false
      layoutParams = LinearLayout.LayoutParams(
        MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
      )
    }
    val hint = TextView(context).apply {
      setTypeface(null, Typeface.BOLD)
      gravity = Gravity.CENTER
      includeFontPadding = false
      layoutParams = LinearLayout.LayoutParams(
        MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
      )
    }
    val container = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      setPadding(0, keyInnerVerticalPadding, 0, keyInnerVerticalPadding)
      isClickable = true
      isFocusable = true
      background = makeKeyBackground(accent = false, hasPopup = false)
      addView(primary)
      addView(hint)
    }
    val slot = TopRowSlot(container, primary, hint)
    container.setOnClickListener { v ->
      slot.currentKey?.let { dispatchAction(v, it.action) }
    }
    container.setOnTouchListener { _, event ->
      val k = slot.currentKey
      if (k != null) {
        when (event.actionMasked) {
          MotionEvent.ACTION_DOWN -> showPressPreview(container, k)
          MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dismissPressPreview()
        }
      }
      false
    }
    return slot
  }

  private fun applyKeyToSlot(slot: TopRowSlot, key: Key) {
    slot.currentKey = key
    // Guard the [TextView.setText] call: setText always re-runs the
    // full text pipeline (measure + layout + spannable rebuild) even
    // when the string is identical, so short-circuiting here is the
    // main reason recycling the row saves work. The size and color
    // setters have their own internal "did anything change" checks
    // so we call them unconditionally.
    if (slot.primary.text?.toString() != key.label) {
      slot.primary.text = key.label
    }
    val codePoints = key.label.codePointCount(0, key.label.length)
    val sp = if (codePoints > 2) mainTextSmallSp else mainTextSp
    slot.primary.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    slot.primary.setTextColor(palette.keyTextColor)

    val hintText = key.hint ?: key.brahmiCodePoint?.let { guide.labelFor(it) }
    if (hintText != null) {
      if (slot.hint.text?.toString() != hintText) {
        slot.hint.text = hintText
      }
      slot.hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, hintTextSp)
      slot.hint.setTextColor(palette.hintTextColor)
      slot.hint.visibility = View.VISIBLE
    } else {
      slot.hint.visibility = View.GONE
    }
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
    if (!anchor.isAttachedToWindow || anchor.windowToken == null) return

    val container = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      background = makePopupBackground()
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
    // lone vowels) still appear centred over the press. Cap against
    // the display width, not the keyboard width, since the balloon is
    // hosted in a top-level PopupWindow.
    val screenWidthPx = resources.displayMetrics.widthPixels
    val maxPreviewWidth = (screenWidthPx - dp(8) * 2).coerceAtLeast(dp(64))
    container.measure(
      MeasureSpec.makeMeasureSpec(maxPreviewWidth, MeasureSpec.AT_MOST),
      MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
    )
    val previewW = container.measuredWidth.coerceAtLeast(anchor.width)
    val previewH = container.measuredHeight

    // Position the balloon in IME-window-relative coordinates. A
    // [PopupWindow] created with our window token behaves as a
    // subwindow of the IME window, so the (x, y) passed to
    // [PopupWindow.showAtLocation] are offsets from the *IME window's*
    // top-left corner - not screen offsets. Passing screen offsets
    // would push the popup down by the IME window's top-Y (typically
    // the whole rest of the display), which lands the balloon at the
    // bottom of the screen after screen-clipping.
    //
    // With window-relative coordinates, y goes negative for the top
    // rows - and that's fine: subwindows are allowed to render above
    // their parent window's bounds, up to the display edge, which is
    // exactly the "balloon above the top row of keys" behavior we
    // want.
    val anchorLoc = IntArray(2)
    anchor.getLocationInWindow(anchorLoc)
    val gap = dp(2)

    val x = (anchorLoc[0] + (anchor.width - previewW) / 2)
      .coerceIn(0, (screenWidthPx - previewW).coerceAtLeast(0))
    var y = anchorLoc[1] - previewH - gap
    // Fallback: only if the "above" placement would spill off the
    // top of the *display* (not just the IME window - a subwindow
    // above the IME window is fine). Screen top-Y of the IME window
    // equals our own screen-Y minus our in-window offset.
    val myScreenLoc = IntArray(2)
    getLocationOnScreen(myScreenLoc)
    val myWindowLoc = IntArray(2)
    getLocationInWindow(myWindowLoc)
    val imeWindowTopScreenY = myScreenLoc[1] - myWindowLoc[1]
    if (imeWindowTopScreenY + y < 0) {
      y = anchorLoc[1] + anchor.height + gap
    }

    val popup = PopupWindow(container, previewW, previewH, false).apply {
      isTouchable = false
      isFocusable = false
      isOutsideTouchable = false
      elevation = dp(8).toFloat()
    }
    runCatching {
      popup.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
    }.onFailure { return }
    currentPressPreview = popup
  }

  private fun dismissPressPreview() {
    currentPressPreview?.let { runCatching { it.dismiss() } }
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
          // Initial press: fire the haptic + action together.
          dispatchAction(view, action)
          val r = object : Runnable {
            override fun run() {
              // Auto-repeat ticks fire the action only. Buzzing the
              // vibrator on every tick (13+ Hz while backspace is
              // held) is a real battery cost and adds no tactile
              // signal the user can actually distinguish from the
              // sustained buzz, so we deliberately skip haptic here.
              onKeyAction?.invoke(action)
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

    /**
     * Interval between auto-repeated events while the key is held.
     * Set well above 50 ms - a faster tick drains battery (more
     * [android.view.inputmethod.InputConnection] round-trips, more
     * ripple redraws) without letting the user actually meter their
     * deletions any better than ~13 Hz already allows.
     */
    private const val REPEAT_INTERVAL_MS: Long = 75L

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
