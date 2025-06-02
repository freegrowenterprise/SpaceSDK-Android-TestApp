package com.growspace.testapp

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

//class AppLifecycleObserver(private val context: Context) : DefaultLifecycleObserver {
//    override fun onStart(owner: LifecycleOwner) {
//        super.onStart(owner)
//        Log.d("AppLifecycleObserver", "앱이 포그라운드에 있음")
//    }
//
//    override fun onStop(owner: LifecycleOwner) {
//        super.onStop(owner)
//        Log.d("AppLifecycleObserver", "앱이 백그라운드로 감")
//
//        val intent = Intent(context, SpaceForegroundService::class.java)
//        ContextCompat.startForegroundService(context, intent)
//    }
//}