package com.toasterofbread.spmp.youtubeapi.lyrics

import androidx.compose.ui.graphics.Color
import com.atilika.kuromoji.ipadic.Tokenizer
import com.toasterofbread.composekit.platform.PlatformFile
import com.toasterofbread.db.Database
import com.toasterofbread.db.mediaitem.LyricsById
import com.toasterofbread.spmp.model.lyrics.SongLyrics
import com.toasterofbread.spmp.model.mediaitem.loader.SongLyricsLoader
import com.toasterofbread.spmp.model.mediaitem.song.Song
import com.toasterofbread.spmp.model.settings.category.LyricsSettings
import com.toasterofbread.spmp.platform.AppContext
import com.toasterofbread.spmp.resources.getStringTODO

data class LyricsReference(val source_index: Int, val id: String, val local_file: PlatformFile? = null) {
    fun isNone(): Boolean = source_index < 0
    fun getUrl(): String? = LyricsSource.fromIdx(source_index).getUrlOfId(id)

    companion object {
        val NONE: LyricsReference = LyricsReference(-1, "")
    }
}

fun LyricsById?.toLyricsReference(): LyricsReference? =
    if (this?.lyrics_source != null && lyrics_id != null) LyricsReference(lyrics_source.toInt(), lyrics_id)
    else null

sealed class LyricsSource(val source_index: Int) {
    data class SearchResult(
        var id: String,
        var name: String,
        var sync_type: SongLyrics.SyncType,
        var artist_name: String?,
        var album_name: String?
    )
    
    abstract fun getReadable(): String
    abstract fun getColour(): Color
    abstract fun getUrlOfId(id: String): String?

    open fun supportsLyricsBySong(): Boolean = false
    open suspend fun getReferenceBySong(song: Song, context: AppContext): Result<LyricsReference?> { throw NotImplementedError() }

    open fun supportsLyricsBySearching(): Boolean = true
    abstract suspend fun getLyrics(lyrics_id: String, context: AppContext, tokeniser: LyricsFuriganaTokeniser): Result<SongLyrics>
    abstract suspend fun searchForLyrics(title: String, artist_name: String? = null): Result<List<SearchResult>>

    fun referenceOfSource(id: String): LyricsReference =
        LyricsReference(source_index, id)

    companion object {
        private val lyrics_sources: List<(Int) -> LyricsSource> = listOf(
            { KugouLyricsSource(it) },
            { PetitLyricsSource(it) },
            { YoutubeMusicLyricsSource(it) }
        )
        val SOURCE_AMOUNT: Int get() = lyrics_sources.size

        fun fromIdx(source_idx: Int): LyricsSource {
            require(source_idx >= 0)

            val creator = lyrics_sources[source_idx]
            return creator.invoke(source_idx)
        }

        inline fun iterateByPriority(
            default: Int = LyricsSettings.Key.DEFAULT_SOURCE.get(),
            action: (LyricsSource) -> Unit
        ) {
            for (i in 0 until SOURCE_AMOUNT) {
                val source = fromIdx(if (i == 0) default else if (i > default) i - 1 else i)
                action(source)
            }
        }

        suspend fun searchSongLyricsByPriority(
            song: Song,
            context: AppContext,
            tokeniser: LyricsFuriganaTokeniser,
            default: Int = LyricsSettings.Key.DEFAULT_SOURCE.get()
        ): Result<SongLyrics> {
            val db: Database = context.database
            val (song_title, artist_title) = db.transactionWithResult {
                Pair(
                    song.getActiveTitle(db),
                    song.Artist.get(db)?.getActiveTitle(db)
                )
            }

            var fail_result: Result<SongLyrics>? = null
            iterateByPriority(default) { source ->
                var lyrics_reference: LyricsReference? = null

                if (source.supportsLyricsBySong()) {
                    lyrics_reference = source.getReferenceBySong(song, context).fold(
                        { it },
                        {
                            if (fail_result == null) {
                                fail_result = Result.failure(it)
                            }
                            return@iterateByPriority
                        }
                    )
                }

                if (lyrics_reference == null && source.supportsLyricsBySearching()) {
                    if (song_title == null) {
                        if (fail_result == null) {
                            fail_result = Result.failure(RuntimeException("Song has no title to search by"))
                        }
                        return@iterateByPriority
                    }

                    val result: SearchResult = source.searchForLyrics(song_title, artist_title).fold(
                        { results ->
                            if (results.isEmpty()) {
                                fail_result = null
                                null
                            }
                            else {
                                results.first()
                            }
                        },
                        {
                            if (fail_result == null) {
                                fail_result = Result.failure(it)
                            }
                            null
                        }
                    ) ?: return@iterateByPriority

                    lyrics_reference = LyricsReference(source.source_index, result.id)
                }

                if (lyrics_reference == null) {
                    throw NotImplementedError(source::class.toString())
                }

                val lyrics_result = SongLyricsLoader.loadByLyrics(lyrics_reference, context, tokeniser)
                if (lyrics_result.isSuccess) {
                    return lyrics_result
                }

                if (fail_result == null) {
                    fail_result = lyrics_result
                }
            }

            return fail_result ?: Result.failure(RuntimeException(getStringTODO("No lyrics found")))
        }
    }
}

suspend fun loadLyrics(reference: LyricsReference, context: AppContext, tokeniser: LyricsFuriganaTokeniser): Result<SongLyrics> {
    require(!reference.isNone())

    val source: LyricsSource = LyricsSource.fromIdx(reference.source_index)
    return source.getLyrics(reference.id, context, tokeniser)
}

internal fun parseStaticLyrics(lyrics_text: String, tokeniser: LyricsFuriganaTokeniser): List<List<SongLyrics.Term>> {
    return lyrics_text.split('\n').map { line ->
        val terms: MutableList<SongLyrics.Term> = mutableListOf()
        val split = line.split(' ')

        if (split.any { it.isNotBlank() }) {
            for (term in split.withIndex()) {
                val text = SongLyrics.Term.Text(
                    if (term.index + 1 != split.size) term.value + ' '
                    else term.value
                )
                terms.add(SongLyrics.Term(listOf(text), -1))
            }
        }

        tokeniser.mergeAndFuriganiseTerms(terms)
    }
}
