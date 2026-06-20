package com.defnf.grid

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.SvgDecoder
import com.defnf.grid.data.image.AudioArtFetcher
import com.defnf.grid.data.image.AudioArtKeyer
import com.defnf.grid.data.image.AudioArtRequest
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GridApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(GifDecoder.Factory())
                add(SvgDecoder.Factory())
                // Render embedded album art for audio files (AudioArtRequest models).
                add(AudioArtKeyer(), AudioArtRequest::class.java)
                add(AudioArtFetcher.Factory(this@GridApplication), AudioArtRequest::class.java)
            }
            .build()
    }
}