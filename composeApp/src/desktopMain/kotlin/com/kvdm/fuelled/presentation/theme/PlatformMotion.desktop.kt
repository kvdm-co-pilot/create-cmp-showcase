package com.kvdm.fuelled.presentation.theme

import androidx.compose.runtime.Composable

/** The desktop dev-client has no reduce-motion setting; it runs the Full scheme. */
@Composable
actual fun platformReducedMotion(): Boolean = false
