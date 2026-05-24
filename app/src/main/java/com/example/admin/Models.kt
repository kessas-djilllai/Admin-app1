package com.example.admin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

data class Device(
    val id: String,
    val name: String,
    val battery: Int,
    val lastActive: Long,
    val storageUsed: Long, // in Bytes
    val storageTotal: Long, // in Bytes
    val isLocked: Boolean,
    val networkType: String? = null,
    val isCharging: Boolean = false
) {
    val isOnline: Boolean
        get() = lastActive == 0L || (System.currentTimeMillis() - lastActive) < 15 * 60 * 1000 // 15 mins threshold or 0L fallback
}

data class SmsLog(
    val id: String,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val type: String // "incoming" or "outgoing"
)

data class SecurityAlert(
    val id: String,
    val title: String, // "DEVICE_BOOTED", "BATTERY_LOW", etc.
    val message: String,
    val timestamp: Long
)

data class InstalledApp(
    val name: String,
    val packageName: String,
    val isSystem: Boolean
)

data class FileItem(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val date: String
)

data class MediaItem(
    val id: String,
    val base64: String,
    val timestamp: Long,
    val type: String, // "screenshot", "camera_front", "camera_back"
    val cameraType: String? = null
) {
    fun toBitmap(): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    fun toImageBitmap(): ImageBitmap? {
        return toBitmap()?.asImageBitmap()
    }
}

data class LiveStreamState(
    val isActive: Boolean = false,
    val image: String? = null,
    val timestamp: Long = 0L,
    val error: String? = null
) {
    fun toBitmap(): Bitmap? {
        val imgStr = image ?: return null
        if (imgStr.isBlank()) return null
        return try {
            val decodedBytes = Base64.decode(imgStr, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }
}

data class CameraStreamState(
    val isActive: Boolean = false,
    val image: String? = null,
    val cameraType: String = "back", // "front" or "back"
    val timestamp: Long = 0L,
    val error: String? = null
) {
    fun toBitmap(): Bitmap? {
        val imgStr = image ?: return null
        if (imgStr.isBlank()) return null
        return try {
            val decodedBytes = Base64.decode(imgStr, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }
}

data class Contact(
    val id: String,
    val name: String,
    val number: String
)

