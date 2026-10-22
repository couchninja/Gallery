package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.davemorrissey.labs.subscaleview.ImageDecoder
import org.fossify.commons.helpers.isQPlus

class MyGlideImageDecoder(val degrees: Int, val signature: ObjectKey, val exifOrientation: Int = ExifInterface.ORIENTATION_UNDEFINED) : ImageDecoder {

    override fun decode(context: Context, uri: Uri): Bitmap {
        val path = uri.toString().substringAfter("file://")
        if (isQPlus() && exifOrientation in PURE_ROTATIONS) {
            decodeToHardwareBitmap(context, path)?.let { return it }
        }

        val options = RequestOptions()
            .format(DecodeFormat.PREFER_ARGB_8888)
            .signature(signature)
            .fitCenter()

        val builder = Glide.with(context)
            .asBitmap()
            .load(path)
            .apply(options)
            .transform(RotateTransformation(-degrees))
            .submit(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)

        return builder.get()
    }

    // The full-size base layer is tens of MB. Glide's result is a software bitmap that it pre-uploads on the
    // RenderThread, stalling swipes for several frames; a hardware bitmap is uploaded on this background thread.
    // Pixels stay in stored (un-rotated) orientation, matching Glide + RotateTransformation(-degrees) for pure rotations.
    private fun decodeToHardwareBitmap(context: Context, path: String): Bitmap? {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.HARDWARE }
        return try {
            if (path.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(path))?.use { BitmapFactory.decodeStream(it, null, options) }
            } else {
                BitmapFactory.decodeFile(path, options)
            }
        } catch (ignored: Exception) {
            null
        } catch (ignored: OutOfMemoryError) {
            null
        }
    }

    companion object {
        private val PURE_ROTATIONS = setOf(
            -1,
            ExifInterface.ORIENTATION_UNDEFINED,
            ExifInterface.ORIENTATION_NORMAL,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_180,
            ExifInterface.ORIENTATION_ROTATE_270,
        )
    }
}
