package com.kvdm.cmpshowcase.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.kvdm.cmpshowcase.presentation.theme.CMPShowcaseTokens
import com.kvdm.cmpshowcase.presentation.theme.designToken

// Feature stub. Copy the `home` feature's data→domain→presentation→DI wiring to flesh this out.
@Composable
fun ProfileScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .designToken(
                tokens = listOf("PaddingPage"),
                resolved = mapOf("padding" to "16dp"),
            )
            .padding(CMPShowcaseTokens.PaddingPage),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "Profile",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { testTag = "profile_title" },
        )
        Text(
            text = "This is a stub screen. Wire it up like the Home feature.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
