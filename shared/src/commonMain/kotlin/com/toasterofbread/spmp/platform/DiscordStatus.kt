package com.toasterofbread.spmp.platform

import com.toasterofbread.spmp.model.mediaitem.MediaItem
import com.toasterofbread.spmp.model.mediaitem.MediaItemThumbnailProvider

expect class DiscordStatus(
    context: AppContext,
    application_id: String,
    account_token: String? = null
) {

    companion object {
        fun isSupported(): Boolean
        fun isAccountTokenRequired(): Boolean

        fun getWarningText(): String?
    }

    enum class Status { ONLINE, IDLE, DO_NOT_DISTURB }
    enum class Type { PLAYING, STREAMING, LISTENING, WATCHING, COMPETING }

    fun close()

    suspend fun shouldUpdateStatus(): Boolean

    fun setActivity(
        name: String,
        type: Type,
        status: Status = Status.ONLINE,
        state: String? = null,
        details: String? = null,
        timestamps: Pair<Long?, Long?>? = null,
        large_image: String? = null,
        small_image: String? = null,
        large_text: String? = null,
        small_text: String? = null,
        buttons: List<Pair<String, String>>? = null
    )

    suspend fun getCustomImages(image_items: List<MediaItem>, target_quality: MediaItemThumbnailProvider.Quality): Result<List<String?>>
}

private const val DISCORD_DEFAULT_AVATAR = "https://discord.com/assets/1f0bfc0865d324c2587920a7d80c609b.png"

data class DiscordMeResponse(
    val id: String? = null,
    val username: String? = null,
    val avatar: String? = null,
    val discriminator: String? = null,
    val banner_color: String? = null,
    val bio: String? = null
) {
    var token: String? = null

    fun isEmpty(): Boolean = this == EMPTY
    fun getAvatarUrl(): String {
        check(!isEmpty())
        check(id != null)

        return if (avatar != null) "https://cdn.discordapp.com/avatars/$id/$avatar.webp"
        else DISCORD_DEFAULT_AVATAR
    }

    companion object {
        val EMPTY = DiscordMeResponse()
    }
}

expect suspend fun getDiscordAccountInfo(account_token: String?): Result<DiscordMeResponse>
