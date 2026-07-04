package com.ratsah.brahmi.catalog

/**
 * The full Brahmi alphabet (Unicode block U+11000-U+1107F), organized
 * exactly the way the Unicode chart [U11000.pdf](https://www.unicode.org/charts/PDF/U11000.pdf)
 * groups it. This is the single source of truth for the learning /
 * reference screen - all 109 assigned code points are listed, plus an
 * IAST-style transliteration where one applies.
 */
object BrahmiCatalog {

  data class Entry(
    val codePoint: Int,
    val name: String,
    val transliteration: String? = null,
    val dependent: Boolean = false,
  ) {
    /** Hex form used in the UI, e.g. "U+11013". Pre-computed. */
    val codePointLabel: String = "U+%05X".format(codePoint)

    /** Brahmi glyph as a [String]; handles the surrogate pair encoding. */
    val glyph: String = String(Character.toChars(codePoint))

  }

  data class Category(
    val title: String,
    val subtitle: String,
    val entries: List<Entry>,
  )

  // -- helpers -------------------------------------------------------------

  private fun letter(codePoint: Int, name: String, translit: String): Entry =
    Entry(codePoint = codePoint, name = name, transliteration = translit)

  private fun sign(codePoint: Int, name: String, translit: String?): Entry =
    Entry(codePoint = codePoint, name = name, transliteration = translit, dependent = true)

  private fun matra(codePoint: Int, name: String, translit: String): Entry =
    Entry(codePoint = codePoint, name = name, transliteration = "-$translit", dependent = true)

  private fun number(codePoint: Int, name: String, value: String): Entry =
    Entry(codePoint = codePoint, name = name, transliteration = value)

  // -- Categories ----------------------------------------------------------

  private val VARIOUS_SIGNS = Category(
    title = "Various signs",
    subtitle = "Modifier marks that attach to a base letter to indicate nasalisation or pre-aspiration.",
    entries = listOf(
      sign(0x11000, "Sign Candrabindu", "m̐"),
      sign(0x11001, "Sign Anusvara", "ṃ"),
      sign(0x11002, "Sign Visarga", "ḥ"),
      sign(0x11003, "Sign Jihvamuliya", "ẖ"),
      sign(0x11004, "Sign Upadhmaniya", "ḫ"),
    ),
  )

  private val INDEPENDENT_VOWELS = Category(
    title = "Independent vowels",
    subtitle = "Standalone vowels used at the start of a word or syllable.",
    entries = listOf(
      letter(0x11005, "Letter A", "a"),
      letter(0x11006, "Letter AA", "ā"),
      letter(0x11007, "Letter I", "i"),
      letter(0x11008, "Letter II", "ī"),
      letter(0x11009, "Letter U", "u"),
      letter(0x1100A, "Letter UU", "ū"),
      letter(0x1100B, "Letter Vocalic R", "r̥"),
      letter(0x1100C, "Letter Vocalic RR", "r̥̄"),
      letter(0x1100D, "Letter Vocalic L", "l̥"),
      letter(0x1100E, "Letter Vocalic LL", "l̥̄"),
      letter(0x1100F, "Letter E", "e"),
      letter(0x11010, "Letter AI", "ai"),
      letter(0x11011, "Letter O", "o"),
      letter(0x11012, "Letter AU", "au"),
    ),
  )

  private val CONSONANTS = Category(
    title = "Consonants",
    subtitle = "33 consonants in varga (phonetic class) order: velars, palatals, retroflex, dentals, labials, semivowels, sibilants, and ha/ḷa.",
    entries = listOf(
      letter(0x11013, "Letter KA", "ka"),
      letter(0x11014, "Letter KHA", "kha"),
      letter(0x11015, "Letter GA", "ga"),
      letter(0x11016, "Letter GHA", "gha"),
      letter(0x11017, "Letter NGA", "ṅa"),
      letter(0x11018, "Letter CA", "ca"),
      letter(0x11019, "Letter CHA", "cha"),
      letter(0x1101A, "Letter JA", "ja"),
      letter(0x1101B, "Letter JHA", "jha"),
      letter(0x1101C, "Letter NYA", "ña"),
      letter(0x1101D, "Letter TTA", "ṭa"),
      letter(0x1101E, "Letter TTHA", "ṭha"),
      letter(0x1101F, "Letter DDA", "ḍa"),
      letter(0x11020, "Letter DDHA", "ḍha"),
      letter(0x11021, "Letter NNA", "ṇa"),
      letter(0x11022, "Letter TA", "ta"),
      letter(0x11023, "Letter THA", "tha"),
      letter(0x11024, "Letter DA", "da"),
      letter(0x11025, "Letter DHA", "dha"),
      letter(0x11026, "Letter NA", "na"),
      letter(0x11027, "Letter PA", "pa"),
      letter(0x11028, "Letter PHA", "pha"),
      letter(0x11029, "Letter BA", "ba"),
      letter(0x1102A, "Letter BHA", "bha"),
      letter(0x1102B, "Letter MA", "ma"),
      letter(0x1102C, "Letter YA", "ya"),
      letter(0x1102D, "Letter RA", "ra"),
      letter(0x1102E, "Letter LA", "la"),
      letter(0x1102F, "Letter VA", "va"),
      letter(0x11030, "Letter SHA", "śa"),
      letter(0x11031, "Letter SSA", "ṣa"),
      letter(0x11032, "Letter SA", "sa"),
      letter(0x11033, "Letter HA", "ha"),
      letter(0x11034, "Letter LLA", "ḷa"),
    ),
  )

  private val OLD_TAMIL_LETTERS = Category(
    title = "Old Tamil consonants",
    subtitle = "Three extra consonants added to the Brahmi block for transcribing Old Tamil.",
    entries = listOf(
      letter(0x11035, "Letter Old Tamil LLLA", "ḻa"),
      letter(0x11036, "Letter Old Tamil RRA", "ṟa"),
      letter(0x11037, "Letter Old Tamil NNNA", "ṉa"),
    ),
  )

  private val DEPENDENT_VOWEL_SIGNS = Category(
    title = "Dependent vowel signs (matras)",
    subtitle = "Vowel signs that attach to a consonant. They never appear alone - the chart shows them on a dotted circle.",
    entries = listOf(
      matra(0x11038, "Vowel Sign AA", "ā"),
      matra(0x11039, "Vowel Sign Bhattiprolu AA", "ā"),
      matra(0x1103A, "Vowel Sign I", "i"),
      matra(0x1103B, "Vowel Sign II", "ī"),
      matra(0x1103C, "Vowel Sign U", "u"),
      matra(0x1103D, "Vowel Sign UU", "ū"),
      matra(0x1103E, "Vowel Sign Vocalic R", "r̥"),
      matra(0x1103F, "Vowel Sign Vocalic RR", "r̥̄"),
      matra(0x11040, "Vowel Sign Vocalic L", "l̥"),
      matra(0x11041, "Vowel Sign Vocalic LL", "l̥̄"),
      matra(0x11042, "Vowel Sign E", "e"),
      matra(0x11043, "Vowel Sign AI", "ai"),
      matra(0x11044, "Vowel Sign O", "o"),
      matra(0x11045, "Vowel Sign AU", "au"),
    ),
  )

  private val OLD_TAMIL_SIGNS = Category(
    title = "Old Tamil signs & letters",
    subtitle = "Additional characters added for transcribing Old Tamil: a separate virama, short E/O vowels and matras, and a second LLA.",
    entries = listOf(
      sign(0x11070, "Sign Old Tamil Virama", "∅"),
      letter(0x11071, "Letter Old Tamil Short E", "e"),
      letter(0x11072, "Letter Old Tamil Short O", "o"),
      matra(0x11073, "Vowel Sign Old Tamil Short E", "e"),
      matra(0x11074, "Vowel Sign Old Tamil Short O", "o"),
      letter(0x11075, "Letter Old Tamil LLA", "ḷa"),
    ),
  )


  private val VIRAMA = Category(
    title = "Virama",
    subtitle = "Suppresses the inherent /a/ of the preceding consonant, used to build conjuncts.",
    entries = listOf(
      sign(0x11046, "Virama", "∅"),
    ),
  )

  private val PUNCTUATION = Category(
    title = "Punctuation",
    subtitle = "Sentence and verse separators used in inscriptions.",
    entries = listOf(
      Entry(0x11047, "Danda", transliteration = "|"),
      Entry(0x11048, "Double Danda", transliteration = "‖"),
      Entry(0x11049, "Punctuation Dot", transliteration = "·"),
      Entry(0x1104A, "Punctuation Double Dot", transliteration = "⁝"),
      Entry(0x1104B, "Punctuation Line", transliteration = "-"),
      Entry(0x1104C, "Punctuation Crescent Bar", transliteration = "⌣"),
      Entry(0x1104D, "Punctuation Lotus", transliteration = "❀"),
    ),
  )

  private val DIGITS_POSITIONAL = Category(
    title = "Digits (positional)",
    subtitle = "Decimal digits 0-9 used for writing modern positional numbers.",
    entries = listOf(
      number(0x11066, "Digit Zero", "0"),
      number(0x11067, "Digit One", "1"),
      number(0x11068, "Digit Two", "2"),
      number(0x11069, "Digit Three", "3"),
      number(0x1106A, "Digit Four", "4"),
      number(0x1106B, "Digit Five", "5"),
      number(0x1106C, "Digit Six", "6"),
      number(0x1106D, "Digit Seven", "7"),
      number(0x1106E, "Digit Eight", "8"),
      number(0x1106F, "Digit Nine", "9"),
    ),
  )

  private val NUMBERS_ADDITIVE = Category(
    title = "Numbers (additive)",
    subtitle = "Traditional non-positional numerals: units, tens, hundred and thousand. Compound numbers are formed with the Number Joiner.",
    entries = listOf(
      number(0x11052, "Number One", "1"),
      number(0x11053, "Number Two", "2"),
      number(0x11054, "Number Three", "3"),
      number(0x11055, "Number Four", "4"),
      number(0x11056, "Number Five", "5"),
      number(0x11057, "Number Six", "6"),
      number(0x11058, "Number Seven", "7"),
      number(0x11059, "Number Eight", "8"),
      number(0x1105A, "Number Nine", "9"),
      number(0x1105B, "Number Ten", "10"),
      number(0x1105C, "Number Twenty", "20"),
      number(0x1105D, "Number Thirty", "30"),
      number(0x1105E, "Number Forty", "40"),
      number(0x1105F, "Number Fifty", "50"),
      number(0x11060, "Number Sixty", "60"),
      number(0x11061, "Number Seventy", "70"),
      number(0x11062, "Number Eighty", "80"),
      number(0x11063, "Number Ninety", "90"),
      number(0x11064, "Number One Hundred", "100"),
      number(0x11065, "Number One Thousand", "1000"),
    ),
  )

  private val NUMBER_JOINER = Category(
    title = "Number joiner",
    subtitle = "Zero-width format character that ties consecutive additive numerals into a single compound number (e.g. 𑁤𑁿𑁜𑁿𑁔 for 123).",
    entries = listOf(
      sign(0x1107F, "Number Joiner", null),
    ),
  )

  /** All categories in the order they appear in the Unicode chart. */
  val CATEGORIES: List<Category> = listOf(
    VARIOUS_SIGNS,
    INDEPENDENT_VOWELS,
    CONSONANTS,
    OLD_TAMIL_LETTERS,
    DEPENDENT_VOWEL_SIGNS,
    OLD_TAMIL_SIGNS,
    VIRAMA,
    PUNCTUATION,
    DIGITS_POSITIONAL,
    NUMBERS_ADDITIVE,
    NUMBER_JOINER,
  )

  /** Total number of catalogued characters. Used for the screen header. */
  val TOTAL_COUNT: Int = CATEGORIES.sumOf { it.entries.size }
}
