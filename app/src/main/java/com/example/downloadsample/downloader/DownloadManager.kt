package com.example.downloadsample.downloader

import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.downloadsample.R
import dagger.Module
import dagger.Provides
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.random.nextLong

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
    fun start(uuids: List<String>) {
        uuids.forEach { start(it) }
    }

    fun start(uuid: String) {
        workManager.enqueueUniqueWork(
            "download-$uuid",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(Data.Builder().putString("uuid", uuid).build())
                .addTag("download")
                .build(),
        )
        startNotificationWorker()
    }

    private fun startNotificationWorker() {
        workManager.enqueueUniqueWork(
            "download-notification",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<NotificationWorker>()
                .build(),
        )
    }

    fun cancel(uuid: String) {
        workManager.cancelUniqueWork("download-$uuid")
    }

    fun deleteAll() {
        workManager.cancelAllWork()
        workManager.pruneWork()
    }

    fun observeTotalStatus(): Flow<TotalStatus> {
        return workManager.getWorkInfosByTagFlow("download")
            .conflate()
            .map { workInfos ->
                TotalStatus(
                    downloaded = workInfos.count { it.state == WorkInfo.State.SUCCEEDED },
                    total = workInfos.size,
                )
            }
    }

    fun observeStatus(uuid: String): Flow<DownloadStatus> {
        return workManager.getWorkInfosForUniqueWorkFlow("download-$uuid")
            .map { it.firstOrNull().toDownloadStatus() }
    }

    private fun WorkInfo?.toDownloadStatus() = when (this?.state) {
        WorkInfo.State.SUCCEEDED -> DownloadStatus.Downloaded
        WorkInfo.State.RUNNING -> DownloadStatus.Downloading(this.progress.getFloat("progress", 0f))
        else -> DownloadStatus.NotDownloaded
    }
}

data class TotalStatus(
    val downloaded: Int,
    val total: Int,
) {
    companion object {
        val EMPTY = TotalStatus(0, 0)
    }

    val progress get() = if (total == 0) 0f else downloaded / total.toFloat()
}

sealed class DownloadStatus(open val progress: Float) {
    val percent get() = (progress * 100).toInt()

    data object NotDownloaded : DownloadStatus(0f)
    data object Downloaded : DownloadStatus(1f)
    data class Downloading(override val progress: Float) : DownloadStatus(progress)
}


@HiltWorker
class NotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val downloadManager: DownloadManager,
) : CoroutineWorker(appContext, workerParams) {
    companion object {
        const val CHANNEL_ID = "main"
        const val NOTIFICATION_ID = 1
    }

    override suspend fun doWork(): Result {

        setForeground(getForegroundInfo(TotalStatus.EMPTY))

        downloadManager.observeTotalStatus()
            .takeWhile { it.total != it.downloaded }
            .collect { totalStatus ->
                setForeground(getForegroundInfo(totalStatus))
            }
        return Result.success()
    }

    private fun getForegroundInfo(totalStatus: TotalStatus): ForegroundInfo {
        val intent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        return ForegroundInfo(
            NOTIFICATION_ID,
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle("downloading ${totalStatus.downloaded} / ${totalStatus.total}")
                .setProgress(totalStatus.total, totalStatus.downloaded, false)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(
                    PendingIntent.getActivity(
                        applicationContext,
                        0,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                .build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }
}

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        val semaphore = Semaphore(3)
    }

    override suspend fun doWork(): Result {
        val uuid = inputData.getString("uuid") ?: error("no uuid")
        Log.d(TAG, "download start uuid $uuid")
        semaphore.withPermit {
            val delay = Random.nextLong(100L..200L)
            repeat(10) {
                val progress = it / 10f
                setProgress(Data.Builder().putFloat("progress", progress).build())
                Log.d(TAG, "download progress $progress, uuid $uuid")
                delay(delay)
            }
            setProgress(Data.Builder().putFloat("progress", 1f).build())
            Log.d(TAG, "download finished uuid $uuid")
        }
        return Result.success()
    }
}
