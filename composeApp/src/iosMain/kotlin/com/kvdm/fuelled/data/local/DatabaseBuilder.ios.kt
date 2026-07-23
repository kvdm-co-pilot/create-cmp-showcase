package com.kvdm.fuelled.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import platform.Foundation.NSHomeDirectory

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFilePath = NSHomeDirectory() + "/Documents/app.db"
    return Room.databaseBuilder<AppDatabase>(name = dbFilePath)
}
