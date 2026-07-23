package com.kvdm.fuelled.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

lateinit var appContext: Context

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> =
    Room.databaseBuilder(
        context = appContext,
        name = appContext.getDatabasePath("app.db").absolutePath
    )
