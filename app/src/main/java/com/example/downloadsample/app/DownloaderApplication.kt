package com.example.downloadsample.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DownloaderApplication : Application() {

  @Inject
  lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        val config = androidx.work.Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
        WorkManager.initialize(this, config)
    }
}
