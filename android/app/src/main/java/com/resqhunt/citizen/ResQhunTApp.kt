package com.resqhunt.citizen

import android.app.Application
import com.resqhunt.citizen.data.local.AppDatabase

class ResQhunTApp : Application() {
    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
    }
}
