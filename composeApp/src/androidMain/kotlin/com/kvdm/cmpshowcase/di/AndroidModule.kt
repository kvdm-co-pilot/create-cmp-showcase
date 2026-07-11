package com.kvdm.cmpshowcase.di

import org.koin.core.module.Module
import org.koin.dsl.module

// Android-only DI bindings (platform services that need a Context, etc.).
// Empty by default — add your platform-specific singletons here.
val androidModule: Module = module {
}
