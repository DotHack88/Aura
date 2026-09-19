package com.muse.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ComposeCapture {

    /**
     * Measures and draws a Composable into a Bitmap.
     * Note: This works best for synchronous content. If the Composable contains AsyncImage,
     * it might draw the placeholder instead if it hasn't loaded. 
     * For best results, ensure images are cached or use a custom image loader.
     */
    fun captureToBitmap(
        context: Context,
        width: Int,
        height: Int,
        content: @androidx.compose.runtime.Composable () -> Unit
    ): Bitmap {
        val composeView = ComposeView(context).apply {
            setContent(content)
        }

        // Measure and layout the view
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        composeView.draw(canvas)

        return bitmap
    }

    /**
     * Saves a bitmap to the cache directory and returns a secure FileProvider URI.
     */
    suspend fun saveBitmapAndGetUri(context: Context, bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        try {
            val imagesFolder = File(context.cacheDir, "images")
            if (!imagesFolder.exists()) {
                imagesFolder.mkdirs()
            }

            val file = File(imagesFolder, "share_image_${System.currentTimeMillis()}.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.flush()
            stream.close()

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
