package com.kvdm.cmpshowcase.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

// Room KMP on the JVM uses the same BundledSQLiteDriver as iOS (set in buildDatabase). The
// dev-client keeps its cache in the OS temp dir — disposable by design, like the dev loop itself.
actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbDir = File(System.getProperty("java.io.tmpdir"), "com.kvdm.cmpshowcase-dev-client")
    dbDir.mkdirs()
    return Room.databaseBuilder<AppDatabase>(name = File(dbDir, "app.db").absolutePath)
}
