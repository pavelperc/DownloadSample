package com.example.downloadsample.downloader

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.Module
import dagger.Provides
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

const val TAG = "TAGG"

@Module
@InstallIn(SingletonComponent::class)
class AppModule {

    @Provides
    fun provideWorkManager(@ApplicationContext context: Context) = WorkManager.getInstance(context)
}

@Singleton
class DownloadManager @Inject constructor(
    private val workManager: WorkManager,
) {
    fun start(uuid: String) {
        workManager.enqueueUniqueWork(
            "download-$uuid",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(Data.Builder().putString("uuid", uuid).build())
                .build(),
        )
    }

    fun cancel(uuid: String) {
        workManager.cancelUniqueWork("download-$uuid")
    }

    fun deleteAll() {
        workManager.pruneWork()
    }

    fun observeStatus(uuid: String): Flow<DownloadStatus> {
        return workManager.getWorkInfosForUniqueWorkFlow("download-$uuid")
            .map { it.firstOrNull() }
            .map {
                when (it?.state) {
                    WorkInfo.State.SUCCEEDED -> DownloadStatus.Downloaded
                    WorkInfo.State.RUNNING ->
                        DownloadStatus.Downloading(it.progress.getFloat("progress", 0f))

                    else -> DownloadStatus.NotDownloaded
                }
            }
    }
}

sealed class DownloadStatus(open val progress: Float) {
    val percent get() = (progress * 100).toInt()

    data object NotDownloaded : DownloadStatus(0f)
    data object Downloaded : DownloadStatus(1f)
    data class Downloading(override val progress: Float) : DownloadStatus(progress)
}

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "start download")
        repeat(10) {
            val progress = it / 10f
            setProgress(Data.Builder().putFloat("progress", progress).build())
            Log.d(TAG, "download progress $progress")
            delay(100)
        }
        setProgress(Data.Builder().putFloat("progress", 1f).build())
        return Result.success()
    }
}
