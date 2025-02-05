package com.crowdcognition.livegaze.androidClient

import android.app.Application
import com.crowdcognition.livegaze.androidClient.utils.NotLoggingTree
import info.hannes.logcat.BuildConfig
import timber.log.Timber

public class LiveGazeCompanionApp: Application() {

    override fun onCreate()
    {
        super.onCreate();
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(NotLoggingTree())
        }
    }
}