package com.ratsah.brahmi.ime

/**
 * A "script guide" is a mapping from a Brahmi code point to the visually
 * equivalent character in a Brahmi-descendant script.
 *
 * The keyboard always types the Brahmi character; the guide just changes the
 * **label** drawn on the key, so users who can read Devanagari/Bengali/…
 * recognize what they are inserting.
 *
 * To add a new descendant script:
 *   1. Add a new private map (e.g. `TAMIL_MAP`) below.
 *   2. Build a `ScriptGuide` object from it.
 *   3. Append the new guide to [ScriptGuides.ALL].
 * That's the only file you need to touch.
 */
data class ScriptGuide(
  val id: String,
  val displayName: String,
  private val mapping: Map<Int, String>,
) {
  /** Descendant-script label for the given Brahmi code point, or `null`. */
  fun labelFor(brahmiCodePoint: Int): String? = mapping[brahmiCodePoint]

  /** Convenience preview ("क ख ग …") used in the settings UI. */
  fun preview(): String =
    listOf(0x11013, 0x11014, 0x11015, 0x11005, 0x11007)
      .mapNotNull { mapping[it] }
      .joinToString(" ")
}

object ScriptGuides {

  // --- helpers ------------------------------------------------------------

  private fun cp(code: Int): String = String(Character.toChars(code))

  /**
   * Build a Brahmi → descendant code-point mapping by zipping two equally
   * sized lists. Anything that doesn't have a 1:1 equivalent in the
   * descendant script is just left out (the keyboard then falls back to
   * the Brahmi label).
   */
  private fun map(pairs: List<Pair<Int, Int>>): Map<Int, String> =
    pairs.associate { (brahmi, descendant) -> brahmi to cp(descendant) }

  // --- Devanagari (U+0900..U+097F) ----------------------------------------

  private val DEVANAGARI_MAP: Map<Int, String> = map(
    listOf(
      // Independent vowels
      0x11005 to 0x0905, 0x11006 to 0x0906, 0x11007 to 0x0907,
      0x11008 to 0x0908, 0x11009 to 0x0909, 0x1100A to 0x090A,
      0x1100B to 0x090B, 0x1100C to 0x0960, 0x1100D to 0x090C,
      0x1100E to 0x0961, 0x1100F to 0x090F, 0x11010 to 0x0910,
      0x11011 to 0x0913, 0x11012 to 0x0914,
      // Vowel signs (matras)
      0x11038 to 0x093E, 0x1103A to 0x093F, 0x1103B to 0x0940,
      0x1103C to 0x0941, 0x1103D to 0x0942, 0x1103E to 0x0943,
      0x1103F to 0x0944, 0x11040 to 0x0962, 0x11041 to 0x0963,
      0x11042 to 0x0947, 0x11043 to 0x0948, 0x11044 to 0x094B,
      0x11045 to 0x094C,
      // Consonants
      0x11013 to 0x0915, 0x11014 to 0x0916, 0x11015 to 0x0917,
      0x11016 to 0x0918, 0x11017 to 0x0919, 0x11018 to 0x091A,
      0x11019 to 0x091B, 0x1101A to 0x091C, 0x1101B to 0x091D,
      0x1101C to 0x091E, 0x1101D to 0x091F, 0x1101E to 0x0920,
      0x1101F to 0x0921, 0x11020 to 0x0922, 0x11021 to 0x0923,
      0x11022 to 0x0924, 0x11023 to 0x0925, 0x11024 to 0x0926,
      0x11025 to 0x0927, 0x11026 to 0x0928, 0x11027 to 0x092A,
      0x11028 to 0x092B, 0x11029 to 0x092C, 0x1102A to 0x092D,
      0x1102B to 0x092E, 0x1102C to 0x092F, 0x1102D to 0x0930,
      0x1102E to 0x0932, 0x1102F to 0x0935, 0x11030 to 0x0936,
      0x11031 to 0x0937, 0x11032 to 0x0938, 0x11033 to 0x0939,
      0x11034 to 0x0933,
      // Signs
      0x11000 to 0x0901, 0x11001 to 0x0902, 0x11002 to 0x0903,
      0x11046 to 0x094D,
      // Punctuation
      0x11047 to 0x0964, 0x11048 to 0x0965,
      // Digits 0..9
      0x11066 to 0x0966, 0x11067 to 0x0967, 0x11068 to 0x0968,
      0x11069 to 0x0969, 0x1106A to 0x096A, 0x1106B to 0x096B,
      0x1106C to 0x096C, 0x1106D to 0x096D, 0x1106E to 0x096E,
      0x1106F to 0x096F,
    )
  )

  // --- IPA (International Phonetic Alphabet) ------------------------------

  /**
   * IPA transcription guide. Unlike the Devanagari/Bengali maps the values
   * are arbitrary multi-codepoint strings (combining diacritics for
   * aspiration, dental subscript, syllabic markers, vowel length, …) so
   * we build the map literally instead of going through [map].
   *
   * Conventions used:
   *  - Voiceless aspirated stops use `ʰ`, voiced aspirated stops use `ʱ`.
   *  - Dentals carry the `◌̪` (combining bridge below) subscript.
   *  - Syllabic vocalic R/L use the `◌̩` syllabic marker.
   *  - Long vowels use the IPA length mark `ː` (Sanskrit /e/ and /o/ are
   *    long by default and shown as `eː` / `oː`).
   *  - Anusvara/chandrabindu show as a superscript `ⁿ`, visarga as `ḥ`.
   *  - Punctuation, virama and the Brahmi number block are omitted -
   *    those have no phonetic value, so the guide falls back to the
   *    Brahmi glyph for them.
   */
  private val IPA_MAP: Map<Int, String> = mapOf(
    // Independent vowels
    0x11005 to "a", 0x11006 to "aː",
    0x11007 to "i", 0x11008 to "iː",
    0x11009 to "u", 0x1100A to "uː",
    0x1100B to "r̩", 0x1100C to "r̩ː",
    0x1100D to "l̩", 0x1100E to "l̩ː",
    0x1100F to "eː", 0x11010 to "ai",
    0x11011 to "oː", 0x11012 to "au",
    // Vowel signs (matras) - same phonetic values as the independent vowels.
    0x11038 to "aː",
    0x1103A to "i", 0x1103B to "iː",
    0x1103C to "u", 0x1103D to "uː",
    0x1103E to "r̩", 0x1103F to "r̩ː",
    0x11040 to "l̩", 0x11041 to "l̩ː",
    0x11042 to "eː", 0x11043 to "ai",
    0x11044 to "oː", 0x11045 to "au",
    // Consonants (Sanskrit pronunciation)
    0x11013 to "k", 0x11014 to "kʰ", 0x11015 to "ɡ", 0x11016 to "ɡʱ",
    0x11017 to "ŋ",
    0x11018 to "c", 0x11019 to "cʰ", 0x1101A to "ɟ", 0x1101B to "ɟʱ",
    0x1101C to "ɲ",
    0x1101D to "ʈ", 0x1101E to "ʈʰ", 0x1101F to "ɖ", 0x11020 to "ɖʱ",
    0x11021 to "ɳ",
    0x11022 to "t̪", 0x11023 to "t̪ʰ", 0x11024 to "d̪", 0x11025 to "d̪ʱ",
    0x11026 to "n̪",
    0x11027 to "p", 0x11028 to "pʰ", 0x11029 to "b", 0x1102A to "bʱ",
    0x1102B to "m",
    0x1102C to "j", 0x1102D to "r", 0x1102E to "l", 0x1102F to "ʋ",
    0x11030 to "ɕ", 0x11031 to "ʂ", 0x11032 to "s", 0x11033 to "ɦ",
    0x11034 to "ɭ",
    // Old Tamil extension
    0x11035 to "ɻ",   // LLLA (Tamil ழ)
    0x11036 to "r",   // RRA (alveolar trill, Tamil ற)
    0x11037 to "n",   // NNNA (alveolar nasal, Tamil ன)
    0x11071 to "e",   // OLD TAMIL SHORT E
    0x11072 to "o",   // OLD TAMIL SHORT O
    0x11073 to "e",   // OLD TAMIL SHORT E vowel sign
    0x11074 to "o",   // OLD TAMIL SHORT O vowel sign
    0x11075 to "ɭ",   // OLD TAMIL LLA
    // Signs
    0x11000 to "ⁿ",   // chandrabindu (vowel nasalization)
    0x11001 to "ⁿ",   // anusvara
    0x11002 to "ḥ",   // visarga
    // Digits - show as Latin so users can read off the value.
    0x11066 to "0", 0x11067 to "1", 0x11068 to "2", 0x11069 to "3",
    0x1106A to "4", 0x1106B to "5", 0x1106C to "6", 0x1106D to "7",
    0x1106E to "8", 0x1106F to "9",
  )

  // --- Bengali (U+0980..U+09FF) -------------------------------------------

  private val BENGALI_MAP: Map<Int, String> = map(
    listOf(
      // Independent vowels
      0x11005 to 0x0985, 0x11006 to 0x0986, 0x11007 to 0x0987,
      0x11008 to 0x0988, 0x11009 to 0x0989, 0x1100A to 0x098A,
      0x1100B to 0x098B, 0x1100C to 0x09E0, 0x1100D to 0x098C,
      0x1100E to 0x09E1, 0x1100F to 0x098F, 0x11010 to 0x0990,
      0x11011 to 0x0993, 0x11012 to 0x0994,
      // Vowel signs (matras)
      0x11038 to 0x09BE, 0x1103A to 0x09BF, 0x1103B to 0x09C0,
      0x1103C to 0x09C1, 0x1103D to 0x09C2, 0x1103E to 0x09C3,
      0x1103F to 0x09C4, 0x11040 to 0x09E2, 0x11041 to 0x09E3,
      0x11042 to 0x09C7, 0x11043 to 0x09C8, 0x11044 to 0x09CB,
      0x11045 to 0x09CC,
      // Consonants. Note: Bengali merges Brahmi va into ba (ব), and
      // has no standalone equivalent for ḷa (𑀴) - that one is left
      // unmapped and falls back to its Brahmi glyph.
      0x11013 to 0x0995, 0x11014 to 0x0996, 0x11015 to 0x0997,
      0x11016 to 0x0998, 0x11017 to 0x0999, 0x11018 to 0x099A,
      0x11019 to 0x099B, 0x1101A to 0x099C, 0x1101B to 0x099D,
      0x1101C to 0x099E, 0x1101D to 0x099F, 0x1101E to 0x09A0,
      0x1101F to 0x09A1, 0x11020 to 0x09A2, 0x11021 to 0x09A3,
      0x11022 to 0x09A4, 0x11023 to 0x09A5, 0x11024 to 0x09A6,
      0x11025 to 0x09A7, 0x11026 to 0x09A8, 0x11027 to 0x09AA,
      0x11028 to 0x09AB, 0x11029 to 0x09AC, 0x1102A to 0x09AD,
      0x1102B to 0x09AE, 0x1102C to 0x09AF, 0x1102D to 0x09B0,
      0x1102E to 0x09B2, 0x1102F to 0x09AC, 0x11030 to 0x09B6,
      0x11031 to 0x09B7, 0x11032 to 0x09B8, 0x11033 to 0x09B9,
      // Signs
      0x11000 to 0x0981, 0x11001 to 0x0982, 0x11002 to 0x0983,
      0x11046 to 0x09CD,
      // Punctuation. Bengali shares the danda/double-danda code points
      // with the Devanagari block (U+0964/U+0965).
      0x11047 to 0x0964, 0x11048 to 0x0965,
      // Digits 0..9
      0x11066 to 0x09E6, 0x11067 to 0x09E7, 0x11068 to 0x09E8,
      0x11069 to 0x09E9, 0x1106A to 0x09EA, 0x1106B to 0x09EB,
      0x1106C to 0x09EC, 0x1106D to 0x09ED, 0x1106E to 0x09EE,
      0x1106F to 0x09EF,
    )
  )

  // --- Public registry ----------------------------------------------------

  /** "No guide" - labels stay in Brahmi. */
  val NONE: ScriptGuide = ScriptGuide(
    id = "brahmi",
    displayName = "Brahmi only",
    mapping = emptyMap(),
  )

  val DEVANAGARI: ScriptGuide = ScriptGuide(
    id = "devanagari",
    displayName = "Devanagari (देवनागरी)",
    mapping = DEVANAGARI_MAP,
  )

  val BENGALI: ScriptGuide = ScriptGuide(
    id = "bengali",
    displayName = "Bengali (বাংলা)",
    mapping = BENGALI_MAP,
  )

  val IPA: ScriptGuide = ScriptGuide(
    id = "ipa",
    displayName = "IPA (phonetic)",
    mapping = IPA_MAP,
  )

  /** Order shown in the settings UI. Add new guides here. */
  val ALL: List<ScriptGuide> = listOf(NONE, DEVANAGARI, BENGALI, IPA)

  /** Default guide for new installs. */
  val DEFAULT: ScriptGuide = BENGALI

  fun byId(id: String): ScriptGuide = ALL.firstOrNull { it.id == id } ?: DEFAULT
}
