package com.kvdm.fuelled.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.presentation.brand.FuelledWordmark
import com.kvdm.fuelled.presentation.components.AppPrimaryButton
import com.kvdm.fuelled.presentation.components.AppTextButton
import com.kvdm.fuelled.presentation.components.BaseScreen
import com.kvdm.fuelled.presentation.components.exposeTestTagsForAutomation
import com.kvdm.fuelled.presentation.theme.FuelledColors
import com.kvdm.fuelled.presentation.theme.FuelledTokens
import org.koin.compose.viewmodel.koinViewModel

// ── Onboarding: the first-run interview (START-01) ───────────────────────────────────────
// The app's first words. It asks the three things that make the numbers MEAN something —
// your name, your calorie target, your protein goal — and nothing else: every other setting
// has a sensible default, and an interview that asks for everything is one people abandon.
// Skippable on purpose (the defaults are real, not placeholders), and never shown again.

/** The VM-backed first-run destination the shell renders while the gate is ONBOARDING. */
@Composable
fun OnboardingRoute(viewModel: OnboardingViewModel = koinViewModel()) {
    OnboardingScreen(onFinish = viewModel::finish, onSkip = viewModel::skip)
}

/**
 * The stateless interview — the preview/UI-first seam, sample-defaulted like every screen.
 * The defaults shown in the fields ARE the seeded defaults, so "Save" with nothing typed is
 * an honest, meaningful outcome rather than a trap.
 */
@Composable
fun OnboardingScreen(
    onFinish: (name: String, targetKcal: Int?, proteinGoalG: Int?) -> Unit = { _, _, _ -> },
    onSkip: () -> Unit = {},
) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("2400") }
    var protein by remember { mutableStateOf("180") }

    BaseScreen { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                // START-01 is a GATE above the nav graph (see App.kt), so this subtree never
                // passes through AppNavHost — which is the one place the automation exposure
                // was applied. Without this the interview's tags reach no id-based E2E tool at
                // all: uiautomator saw only `android:id/content`, and the smoke flow could not
                // select the very screen a fresh install always shows. Expose here too; the
                // modifier adds no layout node, so no golden tree moves.
                .exposeTestTagsForAutomation()
                .semantics { testTag = "onboarding_screen" },
            verticalArrangement = Arrangement.spacedBy(FuelledTokens.GapCard),
        ) {
            Spacer(Modifier.height(48.dp))
            FuelledWordmark(markSize = 34.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Let's make the numbers yours.",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { testTag = "onboarding_title" },
            )
            Text(
                text = "Three answers and you're logging. You can change any of them later.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Field(
                value = name,
                onValueChange = { name = it },
                label = "Your name",
                placeholder = "What should we call you?",
                tag = "onboarding_name",
            )
            Field(
                value = kcal,
                onValueChange = { kcal = it },
                label = "Daily calorie target",
                placeholder = "kcal",
                tag = "onboarding_kcal",
            )
            Field(
                value = protein,
                onValueChange = { protein = it },
                label = "Daily protein goal",
                placeholder = "grams",
                tag = "onboarding_protein",
            )

            Spacer(Modifier.height(8.dp))
            AppPrimaryButton(
                text = "Start tracking",
                onClick = { onFinish(name, kcal.trim().toIntOrNull(), protein.trim().toIntOrNull()) },
                modifier = Modifier.fillMaxWidth().semantics { testTag = "onboarding_start" },
            )
            // Skipping is a real answer: the seeded targets are usable, so the interview must
            // never be a wall between someone and the app they just installed.
            AppTextButton(
                text = "Skip for now",
                onClick = onSkip,
                modifier = Modifier.semantics { testTag = "onboarding_skip" },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    tag: String,
) {
    // The label goes in M3's OWN label slot rather than a Text above it: a field labelled
    // only by a sibling is invisible to a screen reader (caught by audit_a11y on this very
    // screen, 2026-08-01 — the fields with a value read as unlabelled clickables).
    Column(horizontalAlignment = Alignment.Start) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            shape = RoundedCornerShape(FuelledTokens.RadiusInput),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FuelledColors.Primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier.fillMaxWidth().semantics { testTag = tag },
        )
    }
}
