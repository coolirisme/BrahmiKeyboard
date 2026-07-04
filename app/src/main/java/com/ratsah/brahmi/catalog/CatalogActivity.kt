package com.ratsah.brahmi.catalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratsah.brahmi.R
import com.ratsah.brahmi.ime.ScriptGuide
import com.ratsah.brahmi.ime.ScriptGuides
import com.ratsah.brahmi.ime.ScriptPreferences
import com.ratsah.brahmi.ui.theme.BrahmiKeyboardTheme

/**
 * Read-only "browse the alphabet" screen for learners. Lists every
 * assigned code point in the Unicode Brahmi block (U+11000-U+1107F)
 * grouped into the same categories as the official Unicode chart.
 *
 * Each entry shows:
 *  - the Brahmi glyph (rendered with the bundled Noto Sans Brahmi font,
 *    prefixed with a dotted-circle for dependent characters so they
 *    don't look broken),
 *  - the official Unicode name and code point,
 *  - an IAST-style transliteration learners can read aloud,
 *  - the equivalent glyph in whichever script-guide the user picked on
 *    the setup screen, if a mapping exists.
 */
class CatalogActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      BrahmiKeyboardTheme {
        CatalogScreen(onBack = { finish() })
      }
    }
  }
}

private val BrahmiFont: FontFamily = FontFamily(Font(R.font.noto_sans_brahmi))

/** U+25CC DOTTED CIRCLE - prefix for matras/signs so they render visibly. */
private const val DOTTED_CIRCLE: String = "\u25CC"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  // Read the active script guide once per Activity launch. The activity
  // is recreated whenever the user navigates into the catalog, so any
  // change made on the setup screen will be picked up next time the
  // catalog is opened.
  val guide = remember { ScriptGuides.byId(ScriptPreferences.getGuideId(context)) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.catalog_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.catalog_back),
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface,
        ),
      )
    },
    modifier = Modifier.fillMaxSize(),
  ) { innerPadding ->
    CatalogList(
      guide = guide,
      contentPadding = innerPadding,
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatalogList(
  guide: ScriptGuide,
  contentPadding: PaddingValues,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(
      start = 16.dp,
      end = 16.dp,
      top = contentPadding.calculateTopPadding() + 8.dp,
      bottom = contentPadding.calculateBottomPadding() + 24.dp,
    ),
    verticalArrangement = Arrangement.spacedBy(0.dp),
  ) {
    item(key = "header") {
      CatalogIntro()
      Spacer(Modifier.size(8.dp))
    }

    BrahmiCatalog.CATEGORIES.forEach { category ->
      stickyHeader(key = "h:${category.title}") {
        CategoryHeader(category)
      }
      items(items = category.entries, key = { "e:${it.codePoint}" }) { entry ->
        CatalogRow(entry = entry, guide = guide)
        HorizontalDivider(
          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        )
      }
      item(key = "s:${category.title}") {
        Spacer(Modifier.size(16.dp))
      }
    }
  }
}

@Composable
private fun CatalogIntro() {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text = stringResource(R.string.catalog_intro_title),
      style = MaterialTheme.typography.titleLarge,
    )
    Text(
      text = stringResource(
        R.string.catalog_intro_body,
        BrahmiCatalog.TOTAL_COUNT,
      ),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun CategoryHeader(category: BrahmiCatalog.Category) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = category.title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
        )
        Text(
          text = "${category.entries.size}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = category.subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      HorizontalDivider(
        modifier = Modifier.padding(top = 6.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
      )
    }
  }
}

@Composable
private fun CatalogRow(entry: BrahmiCatalog.Entry, guide: ScriptGuide) {
  val descendant = guide.labelFor(entry.codePoint)
  Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    GlyphTile(entry = entry)

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = entry.name,
        style = MaterialTheme.typography.bodyLarge,
      )
      Text(
        text = entry.codePointLabel,
        style = TextStyle(
          fontFamily = FontFamily.Monospace,
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
      )
    }

    if (entry.transliteration != null) {
      Text(
        text = entry.transliteration,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.width(64.dp),
        textAlign = TextAlign.End,
      )
    } else {
      Spacer(Modifier.width(64.dp))
    }

    if (descendant != null) {
      Text(
        text = descendant,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.width(40.dp),
        textAlign = TextAlign.End,
      )
    }
  }
}

@Composable
private fun GlyphTile(entry: BrahmiCatalog.Entry) {
  val displayGlyph = if (entry.dependent) DOTTED_CIRCLE + entry.glyph else entry.glyph
  Box(
    modifier = Modifier
        .size(56.dp)
        .background(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          shape = MaterialTheme.shapes.small,
        ),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = displayGlyph,
      style = TextStyle(
        fontFamily = BrahmiFont,
        fontSize = 30.sp,
        fontWeight = FontWeight.Normal,
      ),
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
    )
  }
}
