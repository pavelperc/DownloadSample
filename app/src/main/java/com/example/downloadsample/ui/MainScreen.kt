package com.example.downloadsample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.downloadsample.downloader.DownloadManager
import com.example.downloadsample.downloader.DownloadStatus
import com.example.downloadsample.downloader.TotalStatus
import com.example.downloadsample.ui.theme.DownloadSampleTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf


val UUIDS = List(30) { "task$it" }

@Composable
fun MainScreen(
    downloadManager: DownloadManager,
) {
    val totalStatus by downloadManager.observeTotalStatus()
        .collectAsStateWithLifecycle(TotalStatus.EMPTY)

    MainScreen(
        uuids = UUIDS,
        observeDownloadStatus = downloadManager::observeStatus,
        totalStatus = totalStatus,
        onItemClick = { uuid, status ->
            when (status) {
                DownloadStatus.NotDownloaded -> downloadManager.start(uuid)
                is DownloadStatus.Downloading -> downloadManager.cancel(uuid)
                DownloadStatus.Downloaded -> downloadManager.deleteAll()
            }
        },
        onDeleteAllClick = { downloadManager.deleteAll() },
        onDownloadAllClick = { downloadManager.start(UUIDS) },
    )
}

@Composable
fun MainScreen(
    uuids: List<String>,
    totalStatus: TotalStatus,
    observeDownloadStatus: (uuid: String) -> Flow<DownloadStatus>,
    onItemClick: (uuid: String, status: DownloadStatus) -> Unit = { _, _ -> },
    onDeleteAllClick: () -> Unit = {},
    onDownloadAllClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
        ) {
            Button(onClick = onDownloadAllClick) {
                Text(text = "Download all")
            }
            Button(onClick = onDeleteAllClick) {
                Text(text = "Delete all")
            }
            Text(
                text = "total - ${totalStatus.downloaded} / ${totalStatus.total}",
                color = MaterialTheme.colorScheme.primary,
            )
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                progress = { totalStatus.progress },
                strokeCap = StrokeCap.Round,
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(uuids) { uuid ->
                ObservableDownloadItem(
                    uuid = uuid,
                    observeDownloadStatus = observeDownloadStatus,
                    onClick = onItemClick,
                )
            }
        }
    }
}

@Composable
fun ObservableDownloadItem(
    uuid: String,
    observeDownloadStatus: (uuid: String) -> Flow<DownloadStatus>,
    onClick: (uuid: String, status: DownloadStatus) -> Unit,
) {
    val status by observeDownloadStatus(uuid).collectAsStateWithLifecycle(DownloadStatus.NotDownloaded)

    DownloadItem(
        uuid = uuid,
        status = status,
        onClick = { onClick(uuid, status) },
    )
}

@Composable
fun DownloadItem(uuid: String, status: DownloadStatus, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        LinearProgressIndicator(
            progress = { status.progress },
            modifier = Modifier.fillMaxWidth(),
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            strokeCap = StrokeCap.Round,
        )
        Text(
            color = MaterialTheme.colorScheme.primary,
            text = "$uuid - ${status.percent}%",
        )
        Button(
            enabled = status != DownloadStatus.Downloaded,
            onClick = onClick,
        ) {
            Text(
                text = when (status) {
                    DownloadStatus.Downloaded -> "downloaded"
                    is DownloadStatus.Downloading -> "cancel"
                    DownloadStatus.NotDownloaded -> "download"
                },
            )
        }
    }
}

@Composable
private fun mainColor(status: DownloadStatus) = when (status) {
    DownloadStatus.Downloaded -> GreenColor
    else -> MaterialTheme.colorScheme.primary
}

private val GreenColor = Color(0xFF00A545)


@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    DownloadSampleTheme {
        MainScreen(
            uuids = UUIDS,
            observeDownloadStatus = { flowOf(DownloadStatus.Downloading(0.2f)) },
            totalStatus = TotalStatus(2, 5),
        )
    }
}
