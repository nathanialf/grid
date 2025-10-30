package com.defnf.grid

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.SvgDecoder
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GridApplication : Application(), ImageLoaderFactory {
    
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(GifDecoder.Factory())
                add(SvgDecoder.Factory())
            }
            .build()
    }
}