package com.toasterofbread.spmp.platform.download

import com.toasterofbread.composekit.platform.PlatformFile
import com.toasterofbread.spmp.model.mediaitem.MediaItem
import com.toasterofbread.spmp.model.mediaitem.MediaItemData
import com.toasterofbread.spmp.model.mediaitem.artist.Artist
import com.toasterofbread.spmp.model.mediaitem.artist.ArtistData
import com.toasterofbread.spmp.model.mediaitem.artist.ArtistRef
import com.toasterofbread.spmp.model.mediaitem.playlist.RemotePlaylist
import com.toasterofbread.spmp.model.mediaitem.playlist.RemotePlaylistData
import com.toasterofbread.spmp.model.mediaitem.song.Song
import com.toasterofbread.spmp.model.mediaitem.song.SongData
import com.toasterofbread.spmp.platform.AppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.exceptions.CannotWriteException
import org.jaudiotagger.audio.mp4.Mp4TagReader
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.mp4.Mp4Tag
import java.io.File
import java.net.URI
import java.util.logging.Level

expect val LocalSongMetadataProcessor: MetadataProcessor

internal fun <T: MediaItemData> MediaItem.getItemWithOrForTitle(item_id: String?, item_title: String?, createItem: (String) -> T): T? {
    val item: T
    if (item_id != null) {
        item = createItem(item_id)
    }
    else if (item_title != null) {
        // TODO
        item = createItem(Artist.getForItemId(this))
    }
    else {
        return null
    }

    item.title = item_title
    return item
}

interface MetadataProcessor {
    suspend fun addMetadataToLocalSong(song: Song, file: PlatformFile, file_extension: String, context: AppContext)
    suspend fun readLocalSongMetadata(file: PlatformFile, context: AppContext, match_id: String? = null, load_data: Boolean = true): SongData?
}

object JAudioTaggerMetadataProcessor: MetadataProcessor {
    val CUSTOM_METADATA_KEY: FieldKey = FieldKey.ALBUM_ARTIST

    @Serializable
    data class CustomMetadata(
        val song_id: String?, val artist_id: String?, val album_id: String?
    )

    init {
        Mp4TagReader.logger.level = Level.OFF
    }

    override suspend fun addMetadataToLocalSong(
        song: Song,
        file: PlatformFile,
        file_extension: String,
        context: AppContext
    ) = withContext(Dispatchers.IO) {
        val tag: Tag = Mp4Tag().apply {
            fun set(key: FieldKey, value: String?) {
                if (value == null) {
                    deleteField(key)
                }
                else {
                    setField(key, value)
                }
            }

            val artist: ArtistRef? = song.Artist.get(context.database)
            val album: RemotePlaylist? = song.Album.get(context.database)

            set(FieldKey.TITLE, song.getActiveTitle(context.database))
            set(FieldKey.ARTIST, artist?.getActiveTitle(context.database))
            set(FieldKey.ALBUM, album?.getActiveTitle(context.database))
            set(FieldKey.URL_OFFICIAL_ARTIST_SITE, song.Album.get(context.database)?.getURL(context))
            set(FieldKey.URL_LYRICS_SITE, song.Lyrics.get(context.database)?.getUrl())

            val custom_metadata: CustomMetadata =
                CustomMetadata(
                    song.id,
                    artist?.id,
                    album?.id
                )
            set(CUSTOM_METADATA_KEY, Json.encodeToString(custom_metadata))
        }

        val audio_file: AudioFile = AudioFileIO.readAs(File(file.absolute_path), file_extension)
        audio_file.tag = tag

        try {
            audio_file.commit()
        }
        catch (e: CannotWriteException) {
            // No idea why it throws this considering it seems to write the metadata just fine
            if (e.message?.startsWith("Unable to make changes to Mp4 file, incorrect offsets written difference was ") == true) {
                return@withContext
            }

            throw e
        }
    }

    override suspend fun readLocalSongMetadata(file: PlatformFile, context: AppContext, match_id: String?, load_data: Boolean): SongData? =
        withContext(Dispatchers.IO) {
            val tag: Tag =
                try {
                    AudioFileIO.read(File(file.absolute_path)).tag
                }
                catch (e: Throwable) {
                    RuntimeException("Ignoring exception while reading ${file.absolute_path}", e).printStackTrace()
                    return@withContext null
                }

            val custom_metadata: CustomMetadata? =
                try {
                    Json.decodeFromString(tag.getFirst(CUSTOM_METADATA_KEY))
                }
                catch (_: Throwable) { null }

            if (custom_metadata?.song_id == null || (match_id != null && custom_metadata.song_id != match_id)) {
                return@withContext null
            }

            return@withContext SongData(custom_metadata.song_id).apply {
                if (!load_data) {
                    return@apply
                }
                title = tag.getFirst(FieldKey.TITLE)
                artist = getItemWithOrForTitle(custom_metadata.artist_id, tag.getFirst(FieldKey.ARTIST)) { ArtistData(it) }
                album = getItemWithOrForTitle(custom_metadata.album_id, tag.getFirst(FieldKey.ALBUM)) { RemotePlaylistData(it) }
            }
        }
}
