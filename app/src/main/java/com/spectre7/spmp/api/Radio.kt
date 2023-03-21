package com.spectre7.spmp.api

import com.spectre7.spmp.model.Artist
import com.spectre7.spmp.model.MediaItem
import com.spectre7.spmp.model.Playlist
import com.spectre7.spmp.model.Song
import com.spectre7.spmp.ui.component.MediaItemLayout
import okhttp3.Request
import java.util.zip.GZIPInputStream

class RadioInstance {
    private var item: MediaItem? = null
    private var continuation: MediaItemLayout.Continuation? = null

    val active: Boolean get() = item != null
    val has_continuation: Boolean get() = continuation != null

    fun playMediaItem(item: MediaItem): Result<List<Song>> {
        this.item = item
        continuation = null
        return getContinuation()
    }

    fun cancelRadio() {
        item = null
        continuation = null
    }

    fun getContinuation(): Result<List<Song>> {
        if (continuation == null) {
            return getInitialSongs()
        }

        val result = continuation!!.loadContinuation()
        if (result.isFailure) {
            return result.cast()
        }

        val (items, cont) = result.getOrThrow()

        if (cont != null) {
            continuation!!.update(cont)
        }
        else {
            continuation = null
        }

        return Result.success(items.filterIsInstance<Song>())
    }

    private fun getInitialSongs(): Result<List<Song>> {
        when (val item = item!!) {
            is Song -> {
                val result = getSongRadio(item.id, null)
                return result.fold(
                    {
                        continuation = it.continuation?.let { MediaItemLayout.Continuation(it, MediaItemLayout.Continuation.Type.SONG, item!!.id) }
                        Result.success(it.items)
                    },
                    { Result.failure(it) }
                )
            }
            is Playlist -> {
                if (item.feed_layouts == null) {
                    val result = item.loadData()
                    if (result.isFailure) {
                        return result.cast()
                    }
                }

                val layout = item.feed_layouts?.firstOrNull()
                if (layout == null) {
                    return Result.success(emptyList())
                }

                continuation = layout.continuation
                return Result.success(layout.items.filterIsInstance<Song>())
            }
            is Artist -> TODO()
            else -> throw NotImplementedError(item.javaClass.name)
        }
    }
}

data class RadioData(val items: List<Song>, var continuation: String?)

data class YoutubeiNextResponse(
    val contents: Contents
) {
    data class Contents(val singleColumnMusicWatchNextResultsRenderer: SingleColumnMusicWatchNextResultsRenderer)
    data class SingleColumnMusicWatchNextResultsRenderer(val tabbedRenderer: TabbedRenderer)
    data class TabbedRenderer(val watchNextTabbedResultsRenderer: WatchNextTabbedResultsRenderer)
    data class WatchNextTabbedResultsRenderer(val tabs: List<Tab>)
    data class Tab(val tabRenderer: TabRenderer)
    data class TabRenderer(val content: Content? = null)
    data class Content(val musicQueueRenderer: MusicQueueRenderer)
    data class MusicQueueRenderer(val content: MusicQueueRendererContent)
    data class MusicQueueRendererContent(val playlistPanelRenderer: PlaylistPanelRenderer)
    data class PlaylistPanelRenderer(val contents: List<ResponseRadioItem>, val continuations: List<Continuation>? = null)
    data class ResponseRadioItem(val playlistPanelVideoRenderer: PlaylistPanelVideoRenderer? = null)
    data class PlaylistPanelVideoRenderer(
        val videoId: String,
        val title: TextRuns,
        val longBylineText: TextRuns,
        val menu: Menu
    ) {
        // Artist, certain
        fun getArtist(host_item: Song): Result<Pair<Artist?, Boolean>> {
            // Get artist ID directly
            for (run in longBylineText.runs!! + title.runs!!) {
                if (run.browse_endpoint_type != "MUSIC_PAGE_TYPE_ARTIST" && run.browse_endpoint_type != "MUSIC_PAGE_TYPE_USER_CHANNEL") {
                    continue
                }

                return Result.success(Pair(
                    Artist.fromId(run.navigationEndpoint!!.browseEndpoint!!.browseId).supplyTitle(run.text) as Artist,
                    true
                ))
            }

            val menu_artist = menu.menuRenderer.getArtist()?.menuNavigationItemRenderer?.navigationEndpoint?.browseEndpoint?.browseId
            if (menu_artist != null) {
                return Result.success(Pair(
                    Artist.fromId(menu_artist),
                    false
                ))
            }

            // Get artist from album
            for (run in longBylineText.runs!!) {
                if (run.navigationEndpoint?.browseEndpoint?.page_type != "MUSIC_PAGE_TYPE_ALBUM") {
                    continue
                }

                val playlist_result = Playlist.fromId(run.navigationEndpoint.browseEndpoint.browseId).loadData()
                if (playlist_result.isFailure) {
                    return playlist_result.cast()
                }

                val artist = playlist_result.getOrThrowHere()?.artist
                if (artist != null) {
                    return Result.success(Pair(artist, false))
                }
            }

            // Get title-only artist (Resolves to 'Various artists' when viewed on YouTube)
            val artist_title = longBylineText.runs?.firstOrNull { it.navigationEndpoint == null }
            if (artist_title != null) {
                return Result.success(Pair(
                    Artist.createForItem(host_item).supplyTitle(artist_title.text) as Artist,
                    false
                ))
            }

            return Result.success(Pair(null, false))
        }
    }
    data class Menu(val menuRenderer: MenuRenderer)
    data class MenuRenderer(val items: List<MenuItem>) {
        fun getArtist(): MenuItem? {
            return items.firstOrNull {
                it.menuNavigationItemRenderer?.icon?.iconType == "ARTIST"
            }
        }
    }
    data class MenuItem(val menuNavigationItemRenderer: MenuNavigationItemRenderer? = null)
    data class MenuNavigationItemRenderer(val icon: MenuIcon, val navigationEndpoint: NavigationEndpoint)
    data class MenuIcon(val iconType: String)
    data class Continuation(val nextContinuationData: ContinuationData? = null, val nextRadioContinuationData: ContinuationData? = null) {
        val data: ContinuationData? get() = nextContinuationData ?: nextRadioContinuationData
    }
    data class ContinuationData(val continuation: String)
}

data class YoutubeiNextContinuationResponse(
    val continuationContents: Contents
) {
    data class Contents(val playlistPanelContinuation: YoutubeiNextResponse.PlaylistPanelRenderer)
}

fun getSongRadio(video_id: String, continuation: String?): Result<RadioData> {
    val request = Request.Builder()
        .url("https://music.youtube.com/youtubei/v1/next")
        .header("accept", "*/*")
        .header("accept-encoding", "gzip, deflate")
        .header("content-encoding", "gzip")
        .header("origin", "https://music.youtube.com")
        .header("X-Goog-Visitor-Id", "CgtUYXUtLWtyZ3ZvTSj3pNWaBg%3D%3D")
        .header("Content-type", "application/json")
        .header("Cookie", "CONSENT=YES+1")
        .post(DataApi.getYoutubeiRequestBody(
        """
        {
            "enablePersistentPlaylistPanel": true,
            "isAudioOnly": true,
            "tunerSettingValue": "AUTOMIX_SETTING_NORMAL",
            "videoId": "$video_id",
            "playlistId": "RDAMVM$video_id",
            "watchEndpointMusicSupportedConfigs": {
                "watchEndpointMusicConfig": {
                    "hasPersistentPlaylistPanel": true,
                    "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
                }
            }
            ${if (continuation != null) """, "continuation": "$continuation" """ else ""}
        }
        """
        ))
        .build()
    
    val result = DataApi.request(request)
    if (result.isFailure) {
        return result.cast()
    }

    val stream = GZIPInputStream(result.getOrThrowHere().body!!.byteStream())

    val radio: YoutubeiNextResponse.PlaylistPanelRenderer

    if (continuation == null) {
        radio = DataApi.klaxon.parse<YoutubeiNextResponse>(stream)!!
            .contents
            .singleColumnMusicWatchNextResultsRenderer
            .tabbedRenderer
            .watchNextTabbedResultsRenderer
            .tabs
            .first()
            .tabRenderer
            .content!!
            .musicQueueRenderer
            .content
            .playlistPanelRenderer
    }
    else {
        radio = DataApi.klaxon.parse<YoutubeiNextContinuationResponse>(stream)!!
            .continuationContents
            .playlistPanelContinuation
    }

    stream.close()

    return Result.success(
        RadioData(
            radio.contents.map { item ->
                val song = Song.fromId(item.playlistPanelVideoRenderer!!.videoId)
                    .supplyTitle(item.playlistPanelVideoRenderer.title.first_text) as Song

                val artist_result = item.playlistPanelVideoRenderer.getArtist(song)
                if (artist_result.isFailure) {
                    return artist_result.cast()
                }

                val (artist, certain) = artist_result.getOrThrow()
                if (artist != null) {
                    song.supplyArtist(artist, certain)
                }

                return@map song
            },
            radio.continuations?.firstOrNull()?.data?.continuation
        )
    )
}