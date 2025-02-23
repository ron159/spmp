package com.toasterofbread.spmp.platform

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import com.toasterofbread.composekit.platform.PlatformFile
import com.toasterofbread.spmp.model.mediaitem.db.getPlayCount
import com.toasterofbread.spmp.model.mediaitem.library.MediaItemLibrary
import com.toasterofbread.spmp.model.mediaitem.song.SongRef
import com.toasterofbread.spmp.model.mediaitem.song.getSongStreamFormat
import com.toasterofbread.spmp.model.settings.category.StreamingSettings
import com.toasterofbread.spmp.platform.download.DownloadStatus
import com.toasterofbread.spmp.platform.download.PlayerDownloadManager
import com.toasterofbread.spmp.platform.playerservice.AUTO_DOWNLOAD_SOFT_TIMEOUT
import com.toasterofbread.spmp.youtubeapi.YoutubeVideoFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@UnstableApi
internal suspend fun processMediaDataSpec(data_spec: DataSpec, context: AppContext, metered: Boolean): DataSpec {
    val song: SongRef = SongRef(data_spec.uri.toString())

    val download_manager: PlayerDownloadManager = context.download_manager
    var local_file: PlatformFile? = MediaItemLibrary.getLocalSong(song, context)?.file
    if (local_file != null) {
        println("Playing song ${song.id} from local file $local_file")
        return data_spec.withUri(Uri.parse(local_file.uri))
    }

    val auto_download_enabled: Boolean = StreamingSettings.Key.AUTO_DOWNLOAD_ENABLED.get(context)

    if (
        auto_download_enabled
        && song.getPlayCount(context.database, 7) >= StreamingSettings.Key.AUTO_DOWNLOAD_THRESHOLD.get<Int>(context)
        && (StreamingSettings.Key.AUTO_DOWNLOAD_ON_METERED.get(context) || !metered)
    ) {
        var done: Boolean = false
        runBlocking {
            val initial_status: DownloadStatus? = download_manager.getDownload(song)
            when (initial_status?.status) {
                DownloadStatus.Status.IDLE, DownloadStatus.Status.CANCELLED, DownloadStatus.Status.PAUSED, null -> {
                    download_manager.startDownload(song, true) { status ->
                        local_file = status?.file
                        done = true
                    }
                }
                DownloadStatus.Status.ALREADY_FINISHED, DownloadStatus.Status.FINISHED -> throw IllegalStateException()
                else -> {}
            }

            val listener: PlayerDownloadManager.DownloadStatusListener = object : PlayerDownloadManager.DownloadStatusListener() {
                override fun onDownloadChanged(status: DownloadStatus) {
                    if (status.song.id != song.id) {
                        return
                    }

                    when (status.status) {
                        DownloadStatus.Status.IDLE, DownloadStatus.Status.DOWNLOADING -> return
                        DownloadStatus.Status.PAUSED -> throw IllegalStateException()
                        DownloadStatus.Status.CANCELLED -> {
                            done = true
                        }
                        DownloadStatus.Status.FINISHED, DownloadStatus.Status.ALREADY_FINISHED -> {
                            launch {
                                local_file = MediaItemLibrary.getLocalSong(song, context)?.file
                                done = true
                            }
                        }
                    }

                    download_manager.removeDownloadStatusListener(this)
                }
            }
            download_manager.addDownloadStatusListener(listener)

            var elapsed: Int = 0
            while (!done && elapsed < AUTO_DOWNLOAD_SOFT_TIMEOUT) {
                delay(100)
                elapsed += 100
            }
        }

        if (local_file != null) {
            println("Playing song ${song.id} from local file $local_file")
            return data_spec.withUri(Uri.parse(local_file!!.uri))
        }
    }

    val format: YoutubeVideoFormat =
        getSongStreamFormat(song.id, context).fold(
            { it },
            { throw it }
        )

    try {
        song.LoudnessDb.setNotNull(format.loudness_db, context.database)
    }
    catch (e: Throwable) {
        e.printStackTrace()
    }

    if (local_file != null) {
        println("Playing song ${song.id} from local file $local_file")
        return data_spec.withUri(Uri.parse(local_file!!.uri))
    }
    else {
        println("Playing song ${song.id} from external format $format stream_url=${format.url}")
        return data_spec.withUri(Uri.parse(format.url))
    }
}
