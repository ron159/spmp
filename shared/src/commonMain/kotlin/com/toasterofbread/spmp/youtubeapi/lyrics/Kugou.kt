package com.toasterofbread.spmp.youtubeapi.lyrics

import androidx.compose.ui.graphics.Color
import com.toasterofbread.spmp.model.lyrics.SongLyrics
import com.toasterofbread.spmp.platform.AppContext
import com.toasterofbread.spmp.resources.getString
import com.toasterofbread.spmp.youtubeapi.impl.youtubemusic.cast
import com.toasterofbread.spmp.youtubeapi.lyrics.kugou.loadKugouLyrics
import com.toasterofbread.spmp.youtubeapi.lyrics.kugou.searchKugouLyrics

internal class KugouLyricsSource(source_idx: Int): LyricsSource(source_idx) {
    override fun getReadable(): String = getString("lyrics_source_kugou")
    override fun getColour(): Color = Color(0xFF50A6FB)
    override fun getUrlOfId(id: String): String? = null

    override suspend fun getLyrics(lyrics_id: String, context: AppContext, tokeniser: LyricsFuriganaTokeniser): Result<SongLyrics> {
        val load_result: Result<List<List<SongLyrics.Term>>> = loadKugouLyrics(lyrics_id, tokeniser)
        val lines: List<List<SongLyrics.Term>> = load_result.getOrNull() ?: return load_result.cast()

        return Result.success(
            SongLyrics(
                LyricsReference(source_index, lyrics_id),
                SongLyrics.SyncType.LINE_SYNC,
                lines
            )
        )
    }

    override suspend fun searchForLyrics(title: String, artist_name: String?): Result<List<SearchResult>> {
        return searchKugouLyrics(title, artist_name)
    }
}
