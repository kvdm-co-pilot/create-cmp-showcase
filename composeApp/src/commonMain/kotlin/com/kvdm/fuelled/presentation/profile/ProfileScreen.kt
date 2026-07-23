package com.kvdm.fuelled.presentation.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.kvdm.fuelled.presentation.components.AppHeader
import com.kvdm.fuelled.presentation.components.ScreenColumn

// Feature stub. Copy the `home` feature's data→domain→presentation→DI wiring to flesh this out.
@Composable
fun ProfileScreen() {
    ScreenColumn(screenTag = "profile") {
        AppHeader(title = "Profile", screenTag = "profile")
        Text(
            text = "This is a stub screen. Wire it up like the Home feature.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
