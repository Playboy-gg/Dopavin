package com.dopavin

import android.app.Application

class DopavinApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
