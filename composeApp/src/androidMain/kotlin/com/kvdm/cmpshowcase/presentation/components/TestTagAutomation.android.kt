package com.kvdm.cmpshowcase.presentation.components

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

/** Android: surface testTags as `resource-id` entries for uiautomator/Appium id-selectors. */
@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.exposeTestTagsForAutomation(): Modifier =
    semantics { testTagsAsResourceId = true }
