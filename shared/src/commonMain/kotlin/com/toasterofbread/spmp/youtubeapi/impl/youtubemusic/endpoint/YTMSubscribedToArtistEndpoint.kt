package com.toasterofbread.spmp.youtubeapi.impl.youtubemusic.endpoint

import com.toasterofbread.composekit.utils.common.lazyAssert
import com.toasterofbread.spmp.model.mediaitem.artist.Artist
import com.toasterofbread.spmp.youtubeapi.endpoint.SubscribedToArtistEndpoint
import com.toasterofbread.spmp.youtubeapi.impl.youtubemusic.YoutubeMusicAuthInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

private class ArtistBrowseResponse(val header: Header) {
    class Header(val musicImmersiveHeaderRenderer: MusicImmersiveHeaderRenderer?)
    class MusicImmersiveHeaderRenderer(val subscriptionButton: SubscriptionButton)
    class SubscriptionButton(val subscribeButtonRenderer: SubscribeButtonRenderer)
    class SubscribeButtonRenderer(val subscribed: Boolean)

    fun getSubscribed(): Boolean? = header.musicImmersiveHeaderRenderer?.subscriptionButton?.subscribeButtonRenderer?.subscribed
}

class YTMSubscribedToArtistEndpoint(override val auth: YoutubeMusicAuthInfo): SubscribedToArtistEndpoint() {
    override suspend fun isSubscribedToArtist(artist: Artist): Result<Boolean> = withContext(Dispatchers.IO) {
        lazyAssert(
            { artist.toString() }
        ) {
            !artist.isForItem()
        }

        val request: Request = Request.Builder()
            .endpointUrl("/youtubei/v1/browse")
            .addAuthApiHeaders()
            .postWithBody(
                mapOf("browseId" to artist.id)
            )
            .build()

        val result = api.performRequest(request)

        val parsed: ArtistBrowseResponse = result.parseJsonResponse {
            return@withContext Result.failure(it)
        }

        return@withContext Result.success(parsed.getSubscribed() == true)
    }
}
