package com.growspace.testapp

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner

class MyApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver(
            applicationContext
        ))
    }
}