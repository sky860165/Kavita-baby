// data/KavitaDatabase.kt
package com.kavitababy.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PdfChunk::class], version = 1, exportSchema = false)
abstract class KavitaDatabase : RoomDatabase() {

    abstract fun pdfChunkDao(): PdfChunkDao

    companion object {
        @Volatile
        private var INSTANCE: KavitaDatabase? = null

        fun getInstance(context: Context): KavitaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KavitaDatabase::class.java,
                    "kavita_baby_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
