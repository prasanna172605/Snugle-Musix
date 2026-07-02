package com.music.innertube.utils

interface ShareProvider {
    fun getSongShareUrl(id: String?): String
    fun getPlaylistShareUrl(id: String?): String
    fun getArtistShareUrl(id: String?): String
    fun getAlbumShareUrl(playlistId: String?): String
}

object DefaultShareProvider : ShareProvider {
    override fun getSongShareUrl(id: String?): String = 
        if (id != null) "https://music.youtube.com/watch?v=$id" else ""
        
    override fun getPlaylistShareUrl(id: String?): String = 
        if (id != null) "https://music.youtube.com/playlist?list=$id" else ""
        
    override fun getArtistShareUrl(id: String?): String = 
        if (id != null) "https://music.youtube.com/channel/$id" else ""
        
    override fun getAlbumShareUrl(playlistId: String?): String = 
        if (playlistId != null) "https://music.youtube.com/playlist?list=$playlistId" else ""
}

object ShareConfiguration {
    var provider: ShareProvider = DefaultShareProvider
}
