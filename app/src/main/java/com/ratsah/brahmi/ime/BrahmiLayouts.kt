package com.ratsah.brahmi.ime

import com.ratsah.brahmi.R

/**
 * Brahmi script keyboard layouts.
 *
 * The keys are organized in three Gboard-style pages:
 *   - [CONSONANTS] (default): the 33 Brahmi consonants in varga order, plus
 *     anusvara/visarga/virama and a toggle to vowels.
 *   - [VOWELS]: independent vowels and the dependent vowel signs (matras).
 *   - [NUMBERS]: Brahmi digits, Latin digits and common punctuation.
 *
 * Adding a key/row/page is intentionally trivial: append to the corresponding
 * list. Codepoints are taken from the Unicode "Brahmi" block (U+11000-U+1107F).
 */
object BrahmiLayouts {
  const val PAGE_CONSONANTS = "consonants"
  const val PAGE_VOWELS = "vowels"
  const val PAGE_NUMBERS = "numbers"

  /** Convert a Unicode code point to a (possibly surrogate-pair) [String]. */
  private fun cp(codePoint: Int): String = String(Character.toChars(codePoint))

  /**
   * Letter key that types the character at [codePoint]. The code point is
   * also remembered on the key so that the active [ScriptGuide] can swap
   * the label for the corresponding descendant-script character.
   */
  private fun k(codePoint: Int): Key {
    val s = cp(codePoint)
    return Key(
      label = s,
      action = KeyAction.Input(s),
      brahmiCodePoint = codePoint,
    )
  }

  /** Letter key that types the literal [text] (no script-guide mapping). */
  private fun k(text: String): Key = Key(label = text, action = KeyAction.Input(text))

  /** Build a row from a list of code points, all weight = 1. */
  private fun row(vararg codePoints: Int): List<Key> = codePoints.map(::k)

  // -- Function keys reused across pages -----------------------------------

  private val keyDelete =
    Key("\u232B", KeyAction.Delete, weight = 1.5f, accent = true, repeatable = true)

  private val keyDeleteWide =
    Key("\u232B", KeyAction.Delete, weight = 2f, accent = true, repeatable = true)
  private val keyEnter = Key("\u23CE", KeyAction.Enter, weight = 1.5f, accent = true)

  /**
   * The word "Brāhmī" (the script's own name) written in Brahmi:
   */
  private const val BRAHMI_WORD: String = "𑀩𑁆𑀭𑀸𑀳𑁆𑀫𑀻"

  private val keySpace = Key(BRAHMI_WORD, KeyAction.Space, weight = 4f)
  private val keyGlobe = Key(
    label = "",
    action = KeyAction.SwitchIme,
    accent = true,
    iconRes = R.drawable.ic_keyboard_globe,
    longPressAction = KeyAction.ShowImePicker,
  )
  private val keyComma = Key(",", KeyAction.Input(","))

  /**
   * The Brahmi danda - the script-native sentence terminator, equivalent
   * to "." in Devanagari/Bengali (। U+0964). Long-pressing the key opens
   * a popup with the rest of the Brahmi punctuation block.
   *
   *   U+11047 𑁇 BRAHMI DANDA                    (default tap)
   *   U+11048 𑁈 BRAHMI DOUBLE DANDA             (popup)
   *   U+11049 𑁉 BRAHMI PUNCTUATION DOT          (popup)
   *   U+1104A 𑁊 BRAHMI PUNCTUATION DOUBLE DOT   (popup)
   *   U+1104B 𑁋 BRAHMI PUNCTUATION LINE         (popup)
   *   U+1104C 𑁌 BRAHMI PUNCTUATION CRESCENT BAR (popup)
   *   U+1104D 𑁍 BRAHMI PUNCTUATION LOTUS        (popup)
   */
  private fun punctuationKey(codePoint: Int): Key = Key(
    label = cp(codePoint),
    action = KeyAction.Input(cp(codePoint)),
    brahmiCodePoint = codePoint,
  )

  private val keyDanda: Key = Key(
    label = cp(0x11047),
    action = KeyAction.Input(cp(0x11047)),
    brahmiCodePoint = 0x11047,
    popups = listOf(
      punctuationKey(0x11048),
      punctuationKey(0x11049),
      punctuationKey(0x1104A),
      punctuationKey(0x1104B),
      punctuationKey(0x1104C),
      punctuationKey(0x1104D),
    ),
  )

  /**
   * Anusvara key. Long-press exposes the three other Brahmi nasal /
   * voiceless-aspiration signs that don't earn their own slot on the
   * main grid:
   *
   *   U+11001 𑀁 BRAHMI SIGN ANUSVARA           (default tap)
   *   U+11000 𑀀 BRAHMI SIGN CHANDRABINDU        (popup)
   *   U+11003 𑀃 BRAHMI SIGN JIHVAMULIYA        (popup)
   *   U+11004 𑀄 BRAHMI SIGN UPADHMANIYA        (popup)
   *
   * Pairing them follows the same convention as most Devanagari IMEs
   * (anusvara = tap, chandrabindu et al. = long-press), and keeps the
   * consonants signs row aligned with the 10-column grid above.
   */
  private fun signKey(codePoint: Int): Key = Key(
    label = cp(codePoint),
    action = KeyAction.Input(cp(codePoint)),
    brahmiCodePoint = codePoint,
  )

  private val keyAnusvara: Key = Key(
    label = cp(0x11001),
    action = KeyAction.Input(cp(0x11001)),
    brahmiCodePoint = 0x11001,
    popups = listOf(
      signKey(0x11000),
      signKey(0x11003),
      signKey(0x11004),
    ),
  )

  /**
   * Vocalic-L matra key (U+11040) with its long counterpart, vocalic-LL
   * (U+11041), tucked behind a long-press. Long vocalic matras are rare
   * enough to live behind a popup, freeing a column on the matras row
   * and keeping it aligned with the 10-column grid of the rows above.
   *
   *   U+11040 𑁀 BRAHMI VOWEL SIGN VOCALIC L   (default tap)
   *   U+11041 𑁁 BRAHMI VOWEL SIGN VOCALIC LL  (popup)
   */
  private val keyVocalicLMatra: Key = k(0x11040).copy(
    popups = listOf(k(0x11041)),
  )

  /**
   * AA-matra key (U+11038) with the Bhattiprolu regional variant
   * (U+11039) tucked behind a long-press. Both represent the same
   * `ā` vowel sign; the Bhattiprolu form is an inscriptional variant
   * needed only for scholarly transcription of that corpus.
   *
   *   U+11038 𑀸 BRAHMI VOWEL SIGN AA              (default tap)
   *   U+11039 𑀹 BRAHMI VOWEL SIGN BHATTIPROLU AA  (popup)
   */
  private val keyAaMatra: Key = k(0x11038).copy(
    popups = listOf(k(0x11039)),
  )

  /**
   * One-hundred numeral key (U+11064) with the Brahmi number joiner
   * (U+1107F) tucked behind a long-press. The joiner is a zero-width
   * format character used to mark consecutive additive numerals as a
   * single compound number (e.g. 𑁤 + JOINER + 𑁜 + JOINER + 𑁔 to
   * write "123"). It's hosted on the hundred key since that's the
   * most common pivot for compound numbers.
   *
   *   U+11064 𑁤 BRAHMI NUMBER ONE HUNDRED  (default tap)
   *   U+1107F 𑁿 BRAHMI NUMBER JOINER       (popup, zero-width glyph)
   */
  private val keyHundredNumeral: Key = k(0x11064).copy(
    popups = listOf(k(0x1107F)),
  )

  /**
   * Old-Tamil-extension key. The Brahmi block carries a small set of letters
   * and signs that were added specifically for transcribing Old Tamil; they
   * have no analogue in the mainstream Brahmi inventory and would clutter
   * the consonants page if exposed individually. Bundling them behind one
   * key keeps the main layout clean while still making the characters one
   * long-press away.
   *
   *   U+11035 𑀵 BRAHMI LETTER OLD TAMIL LLLA   (default tap, "ḻa")
   *   U+11036 𑀶 BRAHMI LETTER OLD TAMIL RRA           (popup)
   *   U+11037 𑀷 BRAHMI LETTER OLD TAMIL NNNA          (popup)
   *   U+11070 𑁰 BRAHMI SIGN OLD TAMIL VIRAMA          (popup)
   *   U+11071 𑁱 BRAHMI LETTER OLD TAMIL SHORT E       (popup)
   *   U+11072 𑁲 BRAHMI LETTER OLD TAMIL SHORT O       (popup)
   *   U+11073 𑁳 BRAHMI VOWEL SIGN OLD TAMIL SHORT E   (popup)
   *   U+11074 𑁴 BRAHMI VOWEL SIGN OLD TAMIL SHORT O   (popup)
   *   U+11075 𑁵 BRAHMI LETTER OLD TAMIL LLA           (popup)
   */
  private fun oldTamilKey(codePoint: Int): Key = Key(
    label = cp(codePoint),
    action = KeyAction.Input(cp(codePoint)),
    brahmiCodePoint = codePoint,
  )

  private val keyOldTamil: Key = Key(
    label = cp(0x11035),
    action = KeyAction.Input(cp(0x11035)),
    brahmiCodePoint = 0x11035,
    popups = listOf(
      oldTamilKey(0x11036),
      oldTamilKey(0x11037),
      oldTamilKey(0x11070),
      oldTamilKey(0x11071),
      oldTamilKey(0x11072),
      oldTamilKey(0x11073),
      oldTamilKey(0x11074),
      oldTamilKey(0x11075),
    ),
  )

  /** Bottom function row, identical across pages except for the leftmost toggle. */
  private fun bottomRow(leftToggle: Key): List<Key> = listOf(
    leftToggle,
    keyGlobe,
    keyComma,
    keySpace,
    keyOldTamil,
    keyDanda,
    keyEnter,
  )

  private val toggleToNumbers = Key(
    label = "?" + cp(0x11067) + cp(0x11068) + cp(0x11069), //𑁧𑁨𑁩
    action = KeyAction.SwitchPage(PAGE_NUMBERS),
    weight = 1.5f,
    accent = true,
  )
  private val toggleToVowels = Key(
    label = cp(0x11005), // 𑀅
    action = KeyAction.SwitchPage(PAGE_VOWELS),
    weight = 1.5f,
    accent = true,
    brahmiCodePoint = 0x11005,
  )
  private val toggleToConsonants = Key(
    label = cp(0x11013), // 𑀓
    action = KeyAction.SwitchPage(PAGE_CONSONANTS),
    weight = 1.5f,
    accent = true,
    brahmiCodePoint = 0x11013,
  )
  private val toggleToConsonantsAbc = Key(
    label = cp(0x11013) + cp(0x11014) + cp(0x11015),//𑀓𑀔𑀕
    action = KeyAction.SwitchPage(PAGE_CONSONANTS),
    weight = 1.5f,
    accent = true,
  )

  // -- Pages ---------------------------------------------------------------

  /**
   * Default page: consonants in varga (phonetic class) order.
   *
   *  Velars      0x11013..0x11017  ka kha ga gha ṅa
   *  Palatals    0x11018..0x1101C  ca cha ja jha ña
   *  Retroflex   0x1101D..0x11021  ṭa ṭha ḍa ḍha ṇa
   *  Dentals     0x11022..0x11026  ta tha da dha na
   *  Labials     0x11027..0x1102B  pa pha ba bha ma
   *  Semivowels  0x1102C..0x1102F  ya ra la va
   *  Sibilants   0x11030..0x11032  śa ṣa sa
   *  ha          0x11033
   *  ḷa          0x11034
   *  Signs:      0x11001 anusvara (long-press -> 0x11000 chandrabindu),
   *              0x11002 visarga, 0x11046 virama
   */
  val CONSONANTS: KeyboardPage = KeyboardPage(
    id = PAGE_CONSONANTS,
    rows = listOf(
      row(
        0x11013, 0x11014, 0x11015, 0x11016, 0x11017, 0x11018, 0x11019, 0x1101A, 0x1101B, 0x1101C
      ),
      row(
        0x1101D, 0x1101E, 0x1101F, 0x11020, 0x11021, 0x11022, 0x11023, 0x11024, 0x11025, 0x11026
      ),
      row(
        0x11027, 0x11028, 0x11029, 0x1102A, 0x1102B, 0x1102C, 0x1102D, 0x1102E, 0x1102F, 0x11034
      ),
      listOf(
        toggleToVowels,
        k(0x11030), k(0x11031), k(0x11032), k(0x11033),
        keyAnusvara, k(0x11002), k(0x11046),
        keyDelete,
      ),
      bottomRow(toggleToNumbers),
    ),
  )

  /**
   * Vowels and matras (dependent vowel signs).
   *
   *  Independent vowels  0x11005..0x11012  a ā i ī u ū ṛ ṝ ḷ ḹ e ai o au
   *  Vowel signs         0x11038..0x11045  ā-sign … au-sign
   *  Virama              0x11046
   *
   * Matras only render correctly when typed AFTER a consonant; standalone
   * they show with a dotted-circle placeholder, just like in any other
   * Indic IME.
   */
  val VOWELS: KeyboardPage = KeyboardPage(
    id = PAGE_VOWELS,
    rows = listOf(
      // Independent vowels
      row(
        0x11005, 0x11006, 0x11007, 0x11008, 0x11009, 0x1100A, 0x1100B, 0x1100C, 0x1100D, 0x1100E
      ),
      // Last 4 vowels + first 6 matras. The AA-matra slot also exposes
      // the Bhattiprolu AA variant via long-press.
      listOf(
        k(0x1100F), k(0x11010), k(0x11011), k(0x11012),
        keyAaMatra, k(0x1103A), k(0x1103B), k(0x1103C), k(0x1103D), k(0x1103E),
      ),
      // Toggle + remaining 6 matras + virama + delete (weights sum to 10).
      // The vocalic-L slot also exposes vocalic-LL via long-press.
      listOf(
        toggleToConsonants,
        k(0x1103F), keyVocalicLMatra, k(0x11042), k(0x11043),
        k(0x11044), k(0x11045), k(0x11046),
        keyDelete,
      ),
      bottomRow(toggleToNumbers),
    ),
  )

  /**
   * Numbers and punctuation.
   *
   *  Row 1 - positional Brahmi digits 0...9         0x11066...0x1106F
   *  Row 2 - traditional Brahmi numerals 1...10     0x11052...0x1105B
   *  Row 3 - traditional Brahmi numerals 20...1000  0x1105C...0x11065
   *           (tens 20-90, then 100, then 1000)
   *  Row 4 - common punctuation + wide delete
   *  Row 5 - bottom function row
   */
  val NUMBERS: KeyboardPage = KeyboardPage(
    id = PAGE_NUMBERS,
    rows = listOf(
      row(
        0x11066, 0x11067, 0x11068, 0x11069, 0x1106A, 0x1106B, 0x1106C, 0x1106D, 0x1106E, 0x1106F
      ),
      // Traditional Brahmi 1..10 (𑁒 𑁓 𑁔 𑁕 𑁖 𑁗 𑁘 𑁙 𑁚 𑁛)
      row(
        0x11052, 0x11053, 0x11054, 0x11055, 0x11056, 0x11057, 0x11058, 0x11059, 0x1105A, 0x1105B
      ),
      // Traditional Brahmi 20, 30, 40, 50, 60, 70, 80, 90, 100, 1000
      // (𑁜 𑁝 𑁞 𑁟 𑁠 𑁡 𑁢 𑁣 𑁤 𑁥). The hundred key also exposes
      // the Brahmi number joiner (U+1107F) via long-press.
      listOf(
        k(0x1105C), k(0x1105D), k(0x1105E), k(0x1105F), k(0x11060),
        k(0x11061), k(0x11062), k(0x11063), keyHundredNumeral, k(0x11065),
      ),
      // 8 punctuation @ weight 1 + delete @ weight 2 = 10, matching
      // the digit rows above column-for-column.
      listOf(
        k("."), k(","), k("?"), k("!"), k("'"), k("\""), k("("), k(")"),
        keyDeleteWide,
      ),
      bottomRow(toggleToConsonantsAbc),
    ),
  )

  /** Look up a page by its id; falls back to the consonants page. */
  fun byId(id: String): KeyboardPage = when (id) {
    PAGE_VOWELS -> VOWELS
    PAGE_NUMBERS -> NUMBERS
    else -> CONSONANTS
  }

  // ----------------------------------------------------------------------
  // Top vowel row (dynamic, depends on the currently "active" consonant).
  // ----------------------------------------------------------------------

  /**
   * One slot in the top vowel row.
   *
   * @param matra dependent vowel sign (or `null` for the inherent /a/).
   * @param independent the independent vowel that's inserted when no
   *                    consonant is active.
   */
  private data class VowelSlot(val matra: Int?, val independent: Int)

  /**
   * The 10 vowel slots shown on the top row, in modern Indic order.
   * Add more slots here to extend the row.
   */
  private val VOWEL_SLOTS: List<VowelSlot> = listOf(
    VowelSlot(matra = null, independent = 0x11005), // a   (inherent)
    VowelSlot(matra = 0x11038, independent = 0x11006), // ā
    VowelSlot(matra = 0x1103A, independent = 0x11007), // i
    VowelSlot(matra = 0x1103B, independent = 0x11008), // ī
    VowelSlot(matra = 0x1103C, independent = 0x11009), // u
    VowelSlot(matra = 0x1103D, independent = 0x1100A), // ū
    VowelSlot(matra = 0x11042, independent = 0x1100F), // e
    VowelSlot(matra = 0x11043, independent = 0x11010), // ai
    VowelSlot(matra = 0x11044, independent = 0x11011), // o
    VowelSlot(matra = 0x11045, independent = 0x11012), // au
  )

  /**
   * Build the top vowel row for the given state.
   *
   * @param activeConsonant code point of the consonant that was just typed,
   *        or `null` if the user hasn't (or has moved past it).
   * @param guide active script guide; used to compose composite descendant
   *        hints for combined-form keys.
   */
  fun buildVowelRow(activeConsonant: Int?, guide: ScriptGuide): List<Key> =
    VOWEL_SLOTS.map { slot -> vowelKey(slot, activeConsonant, guide) }

  private fun vowelKey(
    slot: VowelSlot,
    activeConsonant: Int?,
    guide: ScriptGuide,
  ): Key {
    if (activeConsonant == null) {
      // Default state: independent vowel.
      return Key(
        label = cp(slot.independent),
        action = KeyAction.Vowel(slot.matra, slot.independent),
        brahmiCodePoint = slot.independent,
      )
    }
    if (slot.matra == null) {
      // Inherent /a/: bare consonants already represent C+a so the
      // "a" key just shows the consonant and won't change the editor.
      return Key(
        label = cp(activeConsonant),
        action = KeyAction.Vowel(matra = null, independent = slot.independent),
        brahmiCodePoint = activeConsonant,
      )
    }
    // Combined form: consonant + matra.
    val combinedBrahmi = cp(activeConsonant) + cp(slot.matra)
    val descConsonant = guide.labelFor(activeConsonant)
    val descMatra = guide.labelFor(slot.matra)
    val combinedHint = if (descConsonant != null && descMatra != null) {
      descConsonant + descMatra
    } else {
      null
    }
    return Key(
      label = combinedBrahmi,
      action = KeyAction.Vowel(slot.matra, slot.independent),
      hint = combinedHint,
    )
  }
}
