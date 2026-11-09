package com.dopavin.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.dopavin.data.ArtworkLoader
import com.dopavin.data.ArtworkSize
import java.io.FileNotFoundException
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Lets the media notification and lock screen show artwork for local audio files.
 * The MediaItem's artworkUri is the audio content URI itself; this loader resolves it
 * with the same code the UI uses.
 */
@OptIn(UnstableApi::class)
class ArtworkBitmapLoader(context: Context) : BitmapLoader {

    private val appContext = context.applicationContext
    private val executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        executor.submit(
            Callable<Bitmap> {
                BitmapFactory.decodeByteArray(data, 0, data.size)
                    ?: throw IllegalArgumentException("Artwork bytes could not be decoded")
            },
        )

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        executor.submit(
            Callable<Bitmap> {
                ArtworkLoader.loadBlocking(appContext, uri, ArtworkSize.Large)
                    ?: throw FileNotFoundException("No artwork for $uri")
            },
        )
}
