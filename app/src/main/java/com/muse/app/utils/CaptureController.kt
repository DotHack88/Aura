package com.muse.app.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Picture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first

class CaptureController {
    val captureRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    
    fun capture() {
        captureRequests.tryEmit(Unit)
    }
}

@Composable
fun rememberCaptureController(): CaptureController {
    return remember { CaptureController() }
}

fun Modifier.capturable(
    controller: CaptureController,
    onBitmapCaptured: (Bitmap) -> Unit
): Modifier = this.drawWithCache {
    val width = this.size.width.toInt()
    val height = this.size.height.toInt()
    
    onDrawWithContent {
        val picture = Picture()
        val canvas = picture.beginRecording(width, height)
        drawIntoCanvas { composeCanvas ->
            val nativeCanvas = composeCanvas.nativeCanvas
            val saveCount = nativeCanvas.save()
            
            // Re-route drawing to our Picture canvas
            composeCanvas.nativeCanvas.concat(android.graphics.Matrix())
            
            // We can't trivially reroute the draw call to a different canvas in Compose cleanly 
            // without wrapping it. This approach with Picture is complex.
        }
    }
}
