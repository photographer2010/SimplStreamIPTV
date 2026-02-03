package com.simplstudios.simplstream

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.simplstudios.simplstream.data.repository.StreamRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SimplStream Application
 * By SimplStudios
 */
@HiltAndroidApp
class SimplStreamApp : Application(), ImageLoaderFactory {

    @Inject
    lateinit var streamRepository: StreamRepository
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        
        // Pre-warm the SimplStream API on Render to avoid cold start delays
        // This runs in background and doesn't block app startup
        applicationScope.launch {
            try {
                streamRepository.healthCheck()
            } catch (e: Exception) {
                // Ignore errors - this is just a pre-warm
            }
        }
    }

    // Configure Coil image loader for optimal TV performance
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // 25% of app memory
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05) // 5% of disk space
                    .build()
            }
            .crossfade(true)
            .crossfade(300)
            .build()
    }
}
