package com.androidfung.departureboard.widget.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max

object WidgetBitmapUtils {

    /**
     * Returns the strict byte ceiling for all RemoteViews bitmaps in this widget.
     * It reads current window metrics on API 30+ to match the boot-time calculation
     * of 6 * width * height.
     */
    fun calculateWidgetMemoryCapBytes(context: Context): Long {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val wm = context.getSystemService(WindowManager::class.java)
                val bounds = wm?.currentWindowMetrics?.bounds
                if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                    return 6L * bounds.width().toLong() * bounds.height().toLong()
                }
            } catch (_: Exception) {
            }
        }
        val dm = context.resources.displayMetrics
        return 6L * dm.widthPixels.toLong() * dm.heightPixels.toLong()
    }

    /**
     * Decodes and downsamples a local resource or stream to fit within both
     * physical target bounds and the system's strict memory cap.
     */
    fun decodeDownsampledWidgetBitmap(
        context: Context,
        inputStreamProvider: () -> InputStream?,
        maxWidthDp: Int,
        maxHeightDp: Int,
        distinctImageCount: Int = 1,
        config: Bitmap.Config = Bitmap.Config.RGB_565
    ): Bitmap? {
        val probeStream = inputStreamProvider() ?: return null

        val density = context.resources.displayMetrics.density
        val targetWidthPx = max(1, (maxWidthDp * density).toInt())
        val targetHeightPx = max(1, (maxHeightDp * density).toInt())

        // 1. Probe original dimensions
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            inPreferredConfig = config
        }
        probeStream.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        // 2. Downsample Stage 1: Fit physical widget bounds
        var sampleSize = 1
        while (
            (options.outWidth / (sampleSize * 2) >= targetWidthPx &&
                    options.outHeight / (sampleSize * 2) >= targetHeightPx) ||
            (options.outWidth / sampleSize > targetWidthPx * 2) ||
            (options.outHeight / sampleSize > targetHeightPx * 2)
        ) {
            sampleSize *= 2
        }

        // 3. Downsample Stage 2: Enforce byte limit cap
        val memoryCapBytes = calculateWidgetMemoryCapBytes(context)
        val perImageCapBytes = memoryCapBytes / max(1, distinctImageCount)
        val bytesPerPixel = when {
            options.outConfig != null && options.outConfig != config -> 4L // E.g., RGBA webp forced to ARGB_8888
            config == Bitmap.Config.RGB_565 -> 2L
            config == Bitmap.Config.ALPHA_8 -> 1L
            else -> 4L
        }

        while (
            (max(1, options.outWidth / sampleSize).toLong() *
                    max(1, options.outHeight / sampleSize).toLong() *
                    bytesPerPixel) > perImageCapBytes
        ) {
            sampleSize *= 2
        }

        // 4. Decode final bitmap
        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize
        val decodeStream = inputStreamProvider() ?: return null
        return decodeStream.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }

    /**
     * Creates a shared FileProvider URI for a downsampled widget image, preventing IPC limit errors.
     */
    fun cacheWidgetImageUri(
        context: Context,
        bitmap: Bitmap,
        filename: String = "widget_shared_image.webp"
    ): Uri? {
        return try {
            val cacheDir = File(context.cacheDir, "widget_images")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val safeFilename = File(filename).name.ifBlank { "widget_shared_image.webp" }
            val outFile = File(cacheDir, safeFilename)
            if (!outFile.canonicalPath.startsWith(cacheDir.canonicalPath + File.separator)) {
                return null
            }
            FileOutputStream(outFile).use { fos ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, fos)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 85, fos)
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )

            // Grant launcher permission
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfos = context.packageManager.queryIntentActivities(
                homeIntent,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )
            for (resolveInfo in resolveInfos) {
                val pkg = resolveInfo.activityInfo.packageName
                context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            uri
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolves the appropriate Material Symbol drawable resource ID for a [com.androidfung.departureboard.data.model.TransitMode].
     * Delegates directly to [com.androidfung.departureboard.ui.designsystem.TransitIconHelper.getTransitModeIconRes]
     * to guarantee visual consistency between in-app cards and home screen widgets.
     */
    @androidx.annotation.DrawableRes
    fun getTransitModeIconRes(mode: com.androidfung.departureboard.data.model.TransitMode?): Int {
        return com.androidfung.departureboard.ui.designsystem.TransitIconHelper.getTransitModeIconRes(mode)
    }
}

