package dev.peterdsp.odivrelo.ui.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.peterdsp.odivrelo.R
import dev.peterdsp.odivrelo.core.Brand
import dev.peterdsp.odivrelo.state.AppState
import dev.peterdsp.odivrelo.theme.Space
import dev.peterdsp.odivrelo.ui.common.DemoNotice
import dev.peterdsp.odivrelo.ui.common.OdivreloButton
import dev.peterdsp.odivrelo.ui.common.TouchRow

/**
 * First launch.
 *
 * No account, no permission request, no network call and no screen that cannot
 * be got past without a connection. The person picks a language, reads three
 * honest sentences about what this application is and is not, and lands on the
 * search form.
 */
@Composable
fun WelcomeScreen(
    state: AppState,
    onLanguage: (String) -> Unit,
    onStart: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = Space.readingWidth)) {
            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.semantics { heading() }.testTag("welcome-title"),
            )
            Spacer(Modifier.height(Space.x2))
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.x1))
            Text(
                text = stringResource(R.string.app_purpose),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.isDemo) {
                Spacer(Modifier.height(Space.x6))
                DemoNotice()
            }

            Spacer(Modifier.height(Space.x6))
            Text(
                text = stringResource(R.string.welcome_language_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Space.x2))
            Column(Modifier.selectableGroup()) {
                Brand.LANGUAGES.forEach { tag ->
                    val label = stringResource(
                        when (tag) {
                            "el" -> R.string.language_el
                            "en" -> R.string.language_en
                            else -> R.string.language_sq
                        },
                    )
                    TouchRow(
                        modifier = Modifier
                            .testTag("welcome-language-" + tag)
                            .semantics(mergeDescendants = true) { contentDescription = label },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = state.settings.resolvedLanguageTag == tag,
                                onClick = { onLanguage(tag) },
                            )
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.x6))
            Promise(stringResource(R.string.welcome_no_account))
            Promise(stringResource(R.string.welcome_no_tickets))
            Promise(stringResource(R.string.welcome_offline))

            Spacer(Modifier.height(Space.x8))
            OdivreloButton(
                text = stringResource(R.string.welcome_start),
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().testTag("welcome-start"),
            )
            Spacer(Modifier.height(Space.x6))
        }
    }
}

@Composable
private fun Promise(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.x2)
            .semantics(mergeDescendants = true) { contentDescription = text },
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.padding(horizontal = Space.x1))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}
