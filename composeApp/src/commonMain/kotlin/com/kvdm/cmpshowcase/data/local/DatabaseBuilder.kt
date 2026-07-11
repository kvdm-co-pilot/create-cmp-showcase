package com.kvdm.cmpshowcase.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

expect fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>

fun buildDatabase(): AppDatabase =
    getDatabaseBuilder()
        // BundledSQLiteDriver is required for Room KMP on Kotlin/Native (iOS).
        .setDriver(BundledSQLiteDriver())
        .fallbackToDestructiveMigration(dropAllTables = true)
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
        .build()
