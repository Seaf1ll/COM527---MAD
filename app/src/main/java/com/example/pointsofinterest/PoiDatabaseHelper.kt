package com.example.Q102009411

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class PoiDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "poi_database.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE points_of_interest (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                type TEXT,
                description TEXT,
                latitude REAL,
                longitude REAL
            )
        """
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS points_of_interest")
        onCreate(db)
    }

    fun addPoi(
        name: String,
        type: String,
        description: String,
        latitude: Double,
        longitude: Double
    ) {
        val db = writableDatabase
        val values = ContentValues()
        values.put("name", name)
        values.put("type", type)
        values.put("description", description)
        values.put("latitude", latitude)
        values.put("longitude", longitude)

        db.insert("points_of_interest", null, values)
        db.close()
    }
}