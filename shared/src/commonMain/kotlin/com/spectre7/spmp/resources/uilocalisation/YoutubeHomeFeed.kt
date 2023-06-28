package com.spectre7.spmp.resources.uilocalisation

fun getYoutubeHomeFeedLocalisations(getLanguage: (String) -> Int): YoutubeUILocalisation.LocalisationSet {
    val en = getLanguage("en")
    val ja = getLanguage("ja")

    return YoutubeUILocalisation.LocalisationSet().apply {
        add(
            en to "Listen again",
            ja to "もう一度聴く"
        )
        add(
            en to "Quick picks",
            ja to "おすすめ"
        )
        add(
            en to "START RADIO BASED ON A SONG",
            ja to "曲を選んでラジオを再生"
        )
        add(
            en to "Covers and remixes",
            ja to "カバーとリミックス"
        )
        add(
            en to "Recommended albums",
            ja to "おすすめのアルバム"
        )
        add(
            en to "Forgotten favourites",
            ja to "最近聞いていないお気に入り"
        )
        add(
            en to "TODO",
            ja to "ライブラリから"
        )
        add(
            en to "コミュニティから",
            ja to "From the community"
        )
        add(
            en to "Recommended music videos",
            ja to "おすすめのミュージック ビデオ"
        )
        add(
            en to "Live performances",
            ja to "ライブ"
        )
        add(
            en to "Recommended radios",
            ja to "おすすめのラジオ"
        )
        add(
            en to "FOR YOU",
            ja to "あなたへのおすすめ"
        )
        add(
            en to "Trending songs",
            ja to "急上昇曲"
        )
        add(
            en to "Rock Artists",
            ja to "ロック アーティスト"
        )
        add(
            en to "Hits by decade",
            ja to "Hits by decade",
            ja to "TODO"
        )
        add(
            en to "JUST UPDATED",
            ja to "JUST UPDATED",
            ja to "TODO"
        )
        add(
            en to "Today's hits",
            ja to "Today's hits",
            ja to "TODO"
        )
        add(
            en to "Long listening",
            ja to "長編ミュージック ビデオ"
        )
        add(
            en to "Celebrating Africa Month",
            ja to "Celebrating Africa Month",
            ja to "TODO"
        )
        add(
            en to "Feel good",
            ja to "Feel good",
            ja to "TODO"
        )
        add(
            en to "Fresh new music",
            ja to "Fresh new music",
            ja to "TODO"
        )
        add(
            en to "#TBT",
            ja to "#TBT"
        )
        add(
            en to "From your library",
            ja to "ライブラリから"
        )
    }
}
