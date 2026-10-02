package com.novacamera.security

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Privacy: strip / obfuscate EXIF (GPS, device tags) on export/share.
 * Operates on a stream copy so the vault original is untouched.
 */
@Singleton
class ExifStripper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun strippedCopy(source: Uri): Uri = withContext(Dispatchers.IO) {
        val tmp = java.io.File(context.cacheDir, "nova_strip_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(source)?.use { ins ->
            tmp.outputStream().use { out -> ins.copyTo(out) }
        }
        runCatching {
            val exif = ExifInterface(tmp.absolutePath)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null)
            exif.setAttribute(ExifInterface.TAG_MAKE, null)
            exif.setAttribute(ExifInterface.TAG_MODEL, null)
            exif.setAttribute(ExifInterface.TAG_DEVICE_SERIAL_NUMBER, null)
            exif.saveAttributes()
        }
        androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", tmp,
        )
    }

    suspend fun hasLocation(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                val e = ExifInterface(it)
                e.latLong != null
            } ?: false
        }.getOrDefault(false)
    }
}
