# Brahmi Keyboard

A modern Android keyboard (IME) for typing in the ancient **Brāhmī** script -
the common ancestor of Devanagari, Bengali, Tamil, Sinhala, Tibetan, Thai, and
most other scripts of South and Southeast Asia.

The app ships every assigned character in the Unicode Brahmi block
(`U+11000`-`U+1107F`), a dynamic vowel row that composes consonant + matra
syllables as you type, on-key hints in your choice of descendant script
(Devanagari / Bengali / IPA), a browsable character catalog for learners, and
ten keyboard themes including WCAG-AAA high-contrast palettes.

> 𑀩𑁆𑀭𑀸𑀳𑁆𑀫𑀻 - _Brāhmī_, typed with the keyboard itself.

---

## Features

- **Complete Brahmi block coverage.** All 109 assigned code points from
  `U+11000` to `U+1107F`: consonants, independent vowels, matras (dependent
  vowel signs), virama, anusvara/chandrabindu/visarga, Old Tamil extensions,
  positional and traditional numerals (1–1000), the number joiner, and the full
  punctuation set.
- **Smart syllable composition.** Tap a consonant to arm it; the top row
  instantly switches from independent vowels to the corresponding
  `consonant + matra` combined forms, matching how Indic IMEs like Gboard
  behave.
- **Script guides.** Optionally show a small hint under each key in **Devanagari
  (देवनागरी)**, **Bengali (বাংলা)**, or **IPA (phonetic)** - so anyone who can read
  a modern descendant script can start typing Brahmi right away. The keyboard
  always types the Brahmi character; the guide only re-labels the key.
- **Long-press popups.** Rarer characters (Bhattiprolu variant `ā`, vocalic-LL
  matra, chandrabindu, jihvāmūlīya, upadhmānīya, Old Tamil letters, the Brahmi
  number joiner, extra punctuation dandas) live one long-press away, keeping the
  main grid clean.
- **Ten themes.** System-following light/dark, dedicated Light, Dark, **High
  Contrast (Light / Dark)** with WCAG-AAA text contrast and thicker borders for
  low-vision users, Sepia, Solarized Dark, Midnight Blue, Monokai, and Darcula.
- **Live settings.** Changing the guide or theme in the setup app updates the
  running IME immediately - no need to hide and reshow the keyboard.
- **Character catalog.** A dedicated read-only browser for the entire Brahmi
  Unicode block, organised by the official Unicode chart categories, with the
  Unicode name, code point, IAST transliteration, and equivalent in the chosen
  script guide for every character.
- **Bundled font.** Ships **Noto Sans Brahmi** so the keyboard, previews, and
  catalog always render correctly, even if the system font is missing the Brahmi
  block (which shows as `□` tofu).
- **Edge-to-edge, system-aware.** Correct handling of Android 15+ edge-to-edge
  IME windows: the navigation-bar band is tinted to match the active palette and
  nav-bar icons flip between light and dark to stay legible.

## Requirements

- **Android 10 (API 29)** or newer.
- Any device that can render the Unicode Brahmi block. The app bundles Noto Sans
  Brahmi, so this is essentially every modern Android device.

## Installation

### From source

1. Clone the repository:

   ```bash
   git clone https://github.com/coolirisme/BrahmiKeyboard.git
   cd BrahmiKeyboard
   ```

2. Open the project in **Android Studio** (Ladybug or newer recommended - the
   project targets `compileSdk = 36`).
3. Let Gradle sync, then run the `app` configuration on a device or emulator.

Or from the command line:

```bash
./gradlew :app:installDebug
```

### First-time setup

After installing, launch **Brahmi Keyboard** and follow the on-screen steps:

1. **Enable in system settings** - Languages & input → On-screen keyboard → turn
   on "Brahmi Keyboard".
2. **Pick the Brahmi keyboard** - Tap any text field, then choose "Brahmi
   Keyboard" from the keyboard switcher.
3. **Try it out** - Type in the built-in test field.

Optionally pick a **Hint script**, a **Keyboard theme**, and browse the
**Character Catalog** to learn the alphabet.

## Using the keyboard

The layout is organised into three Gboard-style pages, plus a dynamic vowel row
on top.

### Pages

| Page                     | Contents                                                                                                                                                                                                              |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Consonants** (default) | The 33 Brahmi consonants in varga (phonetic-class) order - velars, palatals, retroflex, dentals, labials, semivowels, sibilants, plus `ha`, `ḷa`. Includes anusvara, visarga, virama, and toggles to the other pages. |
| **Vowels**               | 14 independent vowels and their 13 matras (dependent vowel signs) + virama.                                                                                                                                           |
| **Numbers**              | Positional Brahmi digits `0`-`9`, traditional Brahmi numerals for `1`-`10`, `20`-`90`, `100`, `1000`, plus common Latin punctuation.                                                                                  |

### Composing syllables

Because Brahmi is an abugida, every bare consonant already carries an inherent
`/a/` vowel. To type any other syllable:

1. Tap a **consonant** - the top row immediately swaps its independent vowels
   for `consonant + matra` combined forms.
2. Tap the desired **vowel** in the top row - the bare consonant is replaced
   with the correct combined syllable.
3. To type the plain consonant + inherent `a`, do nothing; the bare consonant
   already represents it.
4. To type an independent vowel (no consonant), tap it in the top row when no
   consonant is armed.

If the cursor moves away between the two taps, the keyboard falls back to
inserting just the matra so it never deletes an unintended character.

### Long-press popups

Keys with a small dot expose extra characters on long-press:

- **Anusvara** → chandrabindu, jihvāmūlīya, upadhmānīya
- **AA-matra** → Bhattiprolu AA variant
- **Vocalic-L matra** → vocalic-LL matra
- **Danda** → double danda, punctuation dot, double dot, line, crescent bar,
  lotus
- **One-hundred numeral** → Brahmi number joiner (`U+1107F`, zero-width)
- **Old Tamil key** → RRA, NNNA, Old Tamil virama, short-E/O letters and matras,
  LLA

## Project structure

```
app/src/main/
├── AndroidManifest.xml
├── java/com/ratsah/brahmi/
│   ├── MainActivity.kt              # Compose setup / settings screen
│   ├── catalog/
│   │   ├── CatalogActivity.kt       # Learner-friendly character catalog
│   │   └── BrahmiCatalog.kt         # Category + entry data for U+11000–U+1107F
│   ├── ime/
│   │   ├── BrahmiInputMethodService.kt  # IME plumbing, syllable state machine
│   │   ├── KeyboardView.kt              # Custom Canvas-based key rendering
│   │   ├── KeyboardModel.kt             # Key / KeyAction / KeyboardPage types
│   │   ├── BrahmiLayouts.kt             # Consonants / Vowels / Numbers pages
│   │   ├── ScriptGuide.kt               # Devanagari / Bengali / IPA mappings
│   │   ├── KeyboardTheme.kt             # 10 palettes + light/dark selection
│   │   └── ScriptPreferences.kt         # SharedPreferences wrapper
│   └── ui/theme/                    # Compose Material 3 theme
└── res/
    ├── font/noto_sans_brahmi.ttf    # Bundled font
    ├── raw/license.txt              # Shown in the "About" dialog
    └── xml/method.xml               # IME subtype declaration
```

## Extending the keyboard

The code is deliberately structured so common additions are one file (and
usually one list) away.

- **Add a new script guide** - Drop a new `Map<Int, String>` and a `ScriptGuide`
  entry into `ScriptGuide.kt`, then append it to `ScriptGuides.ALL`. It shows up
  in the settings dropdown automatically.
- **Add a new theme** - Declare a `KeyboardPalette` (and optionally a light/dark
  pair) in `KeyboardTheme.kt`, wrap it in a `KeyboardTheme`, add it to
  `KeyboardThemes.ALL`.
- **Add a key / row / page** - Append to the relevant list in
  `BrahmiLayouts.kt`. Code points come straight from the Unicode Brahmi block.
- **Add a long-press popup** - Set `popups = listOf(...)` on the `Key` (see
  `keyAnusvara`, `keyDanda`, `keyOldTamil` for examples).

## Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (setup + catalog) and a custom `Canvas`-drawn `View`
  for the IME itself
- **Design system:** Material 3
- **Min / target SDK:** 29 / 36
- **Build:** Gradle Kotlin DSL, Android Gradle Plugin, version catalog
  (`libs.versions.toml`)
- **Font:**
  [Noto Sans Brahmi](https://fonts.google.com/noto/specimen/Noto+Sans+Brahmi)
  (SIL Open Font License)

## Contributing

Issues and pull requests are welcome. If you're adding a new descendant-script
guide, please include a source for the mapping (a standard reference grammar,
the Unicode chart, or a scholarly transliteration table) in the PR description.

## License

Released under the **MIT License**. See [`LICENSE`](LICENSE) for the full text.

Copyright © 2026 coolirisme/ratsah.
