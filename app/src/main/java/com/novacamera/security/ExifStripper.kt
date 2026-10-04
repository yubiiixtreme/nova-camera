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
    /** Identifying tags cleared on export: GPS, timestamps, and device/owner identity. */
    private val stripTags = listOf(
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_AREA_INFORMATION,
        ExifInterface.TAG_GPS_DOP,
        ExifInterface.TAG_GPS_SATELLITES,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_IMAGE_UNIQUE_ID,
        ExifInterface.TAG_BODY_SERIAL_NUMBER,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_LENS_SERIAL_NUMBER,
        ExifInterface.TAG_CAMERA_OWNER_NAME,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
    )

    suspend fun strippedCopy(source: Uri): Uri = withContext(Dispatchers.IO) {
        val tmp = java.io.File(context.cacheDir, "nova_strip_${System.currentTimeMillis()}.jpg")
        val copied = context.contentResolver.openInputStream(source)?.use { ins ->
            tmp.outputStream().use { out -> ins.copyTo(out) }
            true
        } ?: false
        check(copied) { "Could not open source for EXIF stripping: $source" }
        runCatching {
            val exif = ExifInterface(tmp.absolutePath)
            stripTags.forEach { exif.setAttribute(it, null) }
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
