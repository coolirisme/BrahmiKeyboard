package com.ratsah.brahmi

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.core.layout.WindowSizeClass
import com.ratsah.brahmi.catalog.CatalogActivity
import com.ratsah.brahmi.ime.KeyboardPalette
import com.ratsah.brahmi.ime.KeyboardThemes
import com.ratsah.brahmi.ime.ScriptGuides
import com.ratsah.brahmi.ime.ScriptPreferences
import com.ratsah.brahmi.ui.theme.BrahmiKeyboardTheme

/** Bundled Noto Sans Brahmi for the theme-preview swatch glyph. */
private val SwatchBrahmiFont: FontFamily = FontFamily(Font(R.font.noto_sans_brahmi))

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      BrahmiKeyboardTheme {
        SetupScaffold()
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScaffold() {
  val context = LocalContext.current
  var showAbout by remember { mutableStateOf(false) }
  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.setup_title)) },
        actions = {
          IconButton(onClick = { showAbout = true }) {
            Icon(
              painter = painterResource(R.drawable.ic_info_outline),
              contentDescription = stringResource(R.string.action_info),
            )
          }
          IconButton(onClick = { shareApp(context) }) {
            Icon(
              imageVector = Icons.Filled.Share,
              contentDescription = stringResource(R.string.action_share),
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface,
        ),
      )
    },
  ) { innerPadding ->
    SetupScreen(contentPadding = innerPadding)
  }
  if (showAbout) {
    AboutDialog(onDismiss = { showAbout = false })
  }
}

/**
 * Simple modal shown from the top-bar Info action. Surfaces the app
 * tagline, version (read from [PackageManager] so it stays in sync with
 * the manifest), and the full MIT LICENSE in a scrollable monospace
 * "code box" (raw resource so the text stays byte-identical with the
 * LICENSE file shipped in the repo).
 */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
  val context = LocalContext.current
  val versionName: String = remember(context) {
    runCatching {
      context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
  }
  val resources = LocalResources.current
  val licenseText: String = remember(context) {
      runCatching {
        resources.openRawResource(R.raw.license)
          .bufferedReader().use { it.readText() }
      }.getOrNull().orEmpty().trim()
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.info_dialog_dismiss))
      }
    },
    title = { Text(stringResource(R.string.info_dialog_title)) },
    text = {
      val uriHandler = LocalUriHandler.current
      val repoUrl = stringResource(R.string.info_dialog_repo_url)
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(R.string.info_dialog_tagline),
          style = MaterialTheme.typography.bodyMedium,
        )
        if (versionName.isNotBlank()) {
          Text(
            text = stringResource(R.string.info_dialog_version, versionName),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        Text(
          text = stringResource(R.string.info_dialog_repo_label),
          style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
          ),
          modifier = Modifier
            .padding(vertical = 5.dp)
            .clickable { uriHandler.openUri(repoUrl) },
        )
        if (licenseText.isNotBlank()) {
          Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .background(
                  color = MaterialTheme.colorScheme.surfaceVariant,
                  shape = RoundedCornerShape(8.dp),
                )
                .border(
                  width = 1.dp,
                  color = MaterialTheme.colorScheme.outlineVariant,
                  shape = RoundedCornerShape(8.dp),
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
          ) {
            Text(
              text = licenseText,
              style = MaterialTheme.typography.bodySmall,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    },
  )
}

/**
 * Fires a standard Android share chooser with a friendly blurb and a
 * Play Store link built from the current [Context.getPackageName], so
 * flavored or renamed builds always link to their own listing.
 */
private fun shareApp(context: Context) {
  val playUrl = "https://play.google.com/store/apps/details?id=${context.packageName}"
  val message = context.getString(R.string.share_message, playUrl)
  val sendIntent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, message)
    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_chooser_title))
  }
  val chooser = Intent.createChooser(
    sendIntent,
    context.getString(R.string.share_chooser_title),
  ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  context.startActivity(chooser)
}

/**
 * Adaptive setup screen. On compact widths (phone portrait) the setup
 * steps and preferences stack in a single scrolling column, preserving
 * the existing phone UX. On medium/expanded widths (unfolded foldables,
 * tablets, ChromeOS free-form windows) the content splits into two
 * side-by-side panes: setup steps on the left, preferences (script
 * guide + keyboard theme with live preview) on the right.
 *
 * The breakpoint is driven by [currentWindowAdaptiveInfo] from
 * Compose Material 3 Adaptive, using the standard 600dp width
 * threshold ([WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND]).
 */
@Composable
private fun SetupScreen(contentPadding: PaddingValues) {
  val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
  val useTwoPanes = windowSizeClass.isWidthAtLeastBreakpoint(
    WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
  )

  val outerModifier = Modifier
      .fillMaxSize()
      .padding(contentPadding)
      .consumeWindowInsets(contentPadding)
      .imePadding()
      .verticalScroll(rememberScrollState())
      .padding(16.dp)

  if (useTwoPanes) {
    Row(
      modifier = outerModifier,
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        SetupEnableSteps()
        SetupCatalogAndTest()
      }
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        SetupPreferencesContent()
      }
    }
  } else {
    Column(
      modifier = outerModifier,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      SetupEnableSteps()
      SetupPreferencesContent()
      SetupCatalogAndTest()
    }
  }
}

/**
 * The first two onboarding cards - enable the IME in system settings
 * and switch to it from the picker. On wide layouts they head the
 * primary (left) pane; on compact they lead the single scrolling column.
 */
@Composable
private fun SetupEnableSteps() {
  val context = LocalContext.current
  StepCard(
    title = stringResource(R.string.setup_step1_title),
    body = stringResource(R.string.setup_step1_body),
    actionLabel = stringResource(R.string.setup_step1_action),
    onAction = {
      context.startActivity(
        Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    },
  )
  StepCard(
    title = stringResource(R.string.setup_step2_title),
    body = stringResource(R.string.setup_step2_body),
    actionLabel = stringResource(R.string.setup_step2_action),
    onAction = {
      val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
      imm.showInputMethodPicker()
    },
  )
}

/**
 * The catalog launcher + "try it out" text field. Rendered after the
 * preference cards on compact so the user has already picked their
 * script/theme before they see the sample sheet and test input; on
 * wide layouts these tail the primary (left) pane below the enable
 * steps, since preferences live in the supporting (right) pane.
 */
@Composable
private fun SetupCatalogAndTest() {
  val context = LocalContext.current
  StepCard(
    title = stringResource(R.string.setup_catalog_title),
    body = stringResource(R.string.setup_catalog_body),
    actionLabel = stringResource(R.string.setup_catalog_action),
    onAction = {
      context.startActivity(Intent(context, CatalogActivity::class.java))
    },
  )
  Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
    Column(
      modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = stringResource(R.string.setup_step3_title),
        style = MaterialTheme.typography.titleMedium,
      )
      var text by remember { mutableStateOf("") }
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(stringResource(R.string.setup_step3_hint)) },
        modifier = Modifier.fillMaxWidth(),
      )
      Text(
        text = stringResource(R.string.setup_font_warning),
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

/**
 * Preference cards (script guide + keyboard theme). These preview the
 * IME's on-key labels and palette, so on wide layouts they live in the
 * supporting (right) pane where the user can tweak them while glancing
 * at the setup steps. On compact layout they sit between the enable steps and
 * the catalog + test field so the user picks their look-and-feel
 * before opening the sample sheet.
 */
@Composable
private fun SetupPreferencesContent() {
  ScriptGuideCard()
  KeyboardThemeCard()
}

/**
 * Lets the user pick which Brahmi-descendant script is used as the on-key
 * label. New scripts added to [ScriptGuides.ALL] show up here automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScriptGuideCard() {
  val context = LocalContext.current
  var selectedId by remember { mutableStateOf(ScriptPreferences.getGuideId(context)) }
  var expanded by remember { mutableStateOf(false) }

  val selectedGuide = ScriptGuides.byId(selectedId)
  val selectedPreview = selectedGuide.preview()

  Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
    Column(
      modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = stringResource(R.string.setup_guide_title),
        style = MaterialTheme.typography.titleMedium,
      )
      Text(
        text = stringResource(R.string.setup_guide_body),
        style = MaterialTheme.typography.bodyMedium,
      )

      ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
      ) {
        OutlinedTextField(
          value = selectedGuide.displayName,
          onValueChange = {},
          readOnly = true,
          label = { Text(stringResource(R.string.setup_guide_dropdown_label)) },
          supportingText = if (selectedPreview.isNotBlank()) {
            { Text(selectedPreview) }
          } else {
            null
          },
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
          colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
          modifier = Modifier
              .fillMaxWidth()
              .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
          expanded = expanded,
          onDismissRequest = { expanded = false },
        ) {
          ScriptGuides.ALL.forEach { guide ->
            val preview = guide.preview()
            DropdownMenuItem(
              text = {
                Column {
                  Text(
                    text = guide.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                  )
                  if (preview.isNotBlank()) {
                    Text(
                      text = preview,
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              },
              onClick = {
                selectedId = guide.id
                ScriptPreferences.setGuideId(context, guide.id)
                expanded = false
              },
              contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
            )
          }
        }
      }
    }
  }
}

/**
 * Lets the user pick a color theme for the on-screen keyboard, including
 * high-contrast themes for users with low vision. Each dropdown entry
 * previews the actual keyboard palette (background + regular key + accent
 * key + glyph color) so users can pick a theme without opening the IME.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyboardThemeCard() {
  val context = LocalContext.current
  val isDark = isSystemInDarkTheme()
  var selectedId by remember { mutableStateOf(ScriptPreferences.getThemeId(context)) }
  var expanded by remember { mutableStateOf(false) }

  val selectedTheme = KeyboardThemes.byId(selectedId)
  val selectedPalette = selectedTheme.paletteFor(isDark)

  Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
    Column(
      modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = stringResource(R.string.setup_theme_title),
        style = MaterialTheme.typography.titleMedium,
      )
      Text(
        text = stringResource(R.string.setup_theme_body),
        style = MaterialTheme.typography.bodyMedium,
      )

      ThemePreviewStrip(palette = selectedPalette)

      ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
      ) {
        OutlinedTextField(
          value = selectedTheme.displayName,
          onValueChange = {},
          readOnly = true,
          label = { Text(stringResource(R.string.setup_theme_dropdown_label)) },
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
          colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
          modifier = Modifier
              .fillMaxWidth()
              .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
          expanded = expanded,
          onDismissRequest = { expanded = false },
        ) {
          KeyboardThemes.ALL.forEach { theme ->
            val palette = theme.paletteFor(isDark)
            DropdownMenuItem(
              text = {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  ThemeSwatch(palette = palette)
                  Text(
                    text = theme.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                  )
                }
              },
              onClick = {
                selectedId = theme.id
                ScriptPreferences.setThemeId(context, theme.id)
                expanded = false
              },
              contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
            )
          }
        }
      }
    }
  }
}

/**
 * Larger preview of a [KeyboardPalette] used inside dropdown items: a
 * small "keyboard" tile with a background band, two keys (regular +
 * accent) and a Brahmi glyph in the palette's text color. Dropdown items
 * have plenty of horizontal room so we can show the full preview here.
 */
@Composable
private fun ThemeSwatch(palette: KeyboardPalette) {
  Box(
    modifier = Modifier
        .width(52.dp)
        .height(32.dp)
        .background(
          color = Color(palette.background),
          shape = RoundedCornerShape(4.dp),
        )
        .border(
          width = 1.dp,
          color = MaterialTheme.colorScheme.outlineVariant,
          shape = RoundedCornerShape(4.dp),
        )
        .padding(3.dp),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SwatchKey(
        fill = Color(palette.keyFill),
        stroke = Color(palette.keyStroke),
        // 𑀓 - Brahmi letter KA. Rendered with the bundled Noto Sans
        // Brahmi so the swatch shows the same glyph the keyboard would
        // actually paint, in the theme's chosen text color.
        text = "\uD804\uDC13",
        textColor = Color(palette.keyTextColor),
      )
      SwatchKey(
        fill = Color(palette.accentKeyFill),
        stroke = Color(palette.keyStroke),
        text = null,
        textColor = Color(palette.keyTextColor),
      )
    }
  }
}

/**
 * Full-width preview of a [KeyboardPalette], rendered as a miniature
 * keyboard row above the theme dropdown. Shows the background band
 * followed by a row of five Brahmi keys plus one accent key on each
 * side (matching the actual key layout), each painted with the
 * palette's key fill, stroke width, and text color - so users can see
 * exactly how the theme looks before opening the IME.
 *
 * The preview also inherits [KeyboardPalette.strokeWidthDp] so the
 * high-contrast themes get their thicker borders here too.
 */
@Composable
private fun ThemePreviewStrip(palette: KeyboardPalette) {
  // 𑀓 𑀔 𑀕 𑀖 𑀗 - Brahmi KA KHA GA GHA NGA. Rendered with the bundled
  // Noto Sans Brahmi so the preview shows the exact glyphs the keyboard
  // paints, in the theme's chosen text color.
  val previewLetters = listOf(
    "\uD804\uDC13", "\uD804\uDC14", "\uD804\uDC15",
    "\uD804\uDC16", "\uD804\uDC17",
  )
  Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(50.dp)
        .background(
          color = Color(palette.background),
          shape = RoundedCornerShape(6.dp),
        )
        .border(
          width = 1.dp,
          color = MaterialTheme.colorScheme.outlineVariant,
          shape = RoundedCornerShape(6.dp),
        )
        .padding(horizontal = 4.dp, vertical = 5.dp),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(3.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      PreviewKey(
        fill = Color(palette.accentKeyFill),
        stroke = Color(palette.keyStroke),
        strokeWidth = palette.strokeWidthDp.dp,
        letter = null,
        textColor = Color(palette.keyTextColor),
        modifier = Modifier.weight(1.4f),
      )
      previewLetters.forEach { letter ->
        PreviewKey(
          fill = Color(palette.keyFill),
          stroke = Color(palette.keyStroke),
          strokeWidth = palette.strokeWidthDp.dp,
          letter = letter,
          textColor = Color(palette.keyTextColor),
          modifier = Modifier.weight(1f),
        )
      }
      PreviewKey(
        fill = Color(palette.accentKeyFill),
        stroke = Color(palette.keyStroke),
        strokeWidth = palette.strokeWidthDp.dp,
        letter = null,
        textColor = Color(palette.keyTextColor),
        modifier = Modifier.weight(1.4f),
      )
    }
  }
}

@Composable
private fun PreviewKey(
  fill: Color,
  stroke: Color,
  strokeWidth: Dp,
  letter: String?,
  textColor: Color,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
        .fillMaxHeight()
        .background(color = fill, shape = RoundedCornerShape(4.dp))
        .border(width = strokeWidth, color = stroke, shape = RoundedCornerShape(4.dp)),
    contentAlignment = Alignment.Center,
  ) {
    if (letter != null) {
      Text(
        text = letter,
        color = textColor,
        fontFamily = SwatchBrahmiFont,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
      )
    }
  }
}

@Composable
private fun SwatchKey(
  fill: Color,
  stroke: Color,
  text: String?,
  textColor: Color,
) {
  Box(
    modifier = Modifier
        .width(20.dp)
        .height(20.dp)
        .background(color = fill, shape = RoundedCornerShape(2.dp))
        .border(width = 1.dp, color = stroke, shape = RoundedCornerShape(2.dp)),
    contentAlignment = Alignment.Center,
  ) {
    if (text != null) {
      Text(
        text = text,
        color = textColor,
        fontFamily = SwatchBrahmiFont,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
      )
    }
  }
}

@Composable
private fun StepCard(
  title: String,
  body: String,
  actionLabel: String,
  onAction: () -> Unit,
) {
  Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
    Column(
      modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(text = title, style = MaterialTheme.typography.titleMedium)
      Text(text = body, style = MaterialTheme.typography.bodyMedium)
      Button(onClick = onAction) { Text(actionLabel) }
    }
  }
}
