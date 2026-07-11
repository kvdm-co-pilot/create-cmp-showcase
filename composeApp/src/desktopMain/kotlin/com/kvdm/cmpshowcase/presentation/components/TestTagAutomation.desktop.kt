package com.kvdm.cmpshowcase.presentation.components

import androidx.compose.ui.Modifier

/** Desktop: no resource-id concept (skiko `ui` lacks testTagsAsResourceId) — no-op. */
actual fun Modifier.exposeTestTagsForAutomation(): Modifier = this
