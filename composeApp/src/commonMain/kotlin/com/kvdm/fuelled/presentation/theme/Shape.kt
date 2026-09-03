package com.kvdm.fuelled.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val FuelledShapes = Shapes(
    small      = RoundedCornerShape(14.dp),
    medium     = RoundedCornerShape(16.dp),
    // 16 dp, M3's scale. It was 999 dp — a full pill — which silently made every M3
    // component reading this rung (FAB, navigation drawer, rich tooltip) fully round.
    // Pill radii are an explicit choice via FuelledTokens.RadiusPill, not this rung.
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
