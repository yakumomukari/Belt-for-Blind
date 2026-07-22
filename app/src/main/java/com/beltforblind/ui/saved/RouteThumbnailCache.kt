package com.beltforblind.ui.saved

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.beltforblind.route.model.RouteRecord
import java.io.File
import java.security.MessageDigest

internal class RouteThumbnailCache(
    context: Context,
) {
    private val cacheRoot = File(context.cacheDir, CACHE_DIRECTORY)

    fun contains(route: RouteRecord): Boolean = thumbnailFile(route).isFile

    fun load(route: RouteRecord): Bitmap? {
        val file = thumbnailFile(route)
        if (!file.isFile) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    fun save(route: RouteRecord, bitmap: Bitmap): Boolean {
        val routeDirectory = routeDirectory(route)
        if (!routeDirectory.exists() && !routeDirectory.mkdirs()) return false

        routeDirectory
            .listFiles()
            .orEmpty()
            .filterNot { it.name == thumbnailFile(route).name }
            .forEach(File::delete)

        return runCatching {
            thumbnailFile(route).outputStream().buffered().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)
            }
        }.getOrDefault(false)
    }

    fun delete(route: RouteRecord) {
        val directory = routeDirectory(route)
        if (!directory.exists()) return
        directory.listFiles().orEmpty().forEach(File::delete)
        directory.delete()
    }

    private fun thumbnailFile(route: RouteRecord): File {
        return File(routeDirectory(route), "${route.fingerprint()}.png")
    }

    private fun routeDirectory(route: RouteRecord): File {
        return File(cacheRoot, route.id.sha256())
    }

    private fun RouteRecord.fingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(id.toByteArray(Charsets.UTF_8))
        digest.update(name.toByteArray(Charsets.UTF_8))
        digest.update(createdAt.toString().toByteArray(Charsets.UTF_8))
        points.forEach { point ->
            digest.update(point.latitude.toString().toByteArray(Charsets.UTF_8))
            digest.update(point.longitude.toString().toByteArray(Charsets.UTF_8))
            digest.update(point.timestamp.toString().toByteArray(Charsets.UTF_8))
            digest.update(point.accuracy.toString().toByteArray(Charsets.UTF_8))
        }
        return digest.digest().toHex()
    }

    private fun String.sha256(): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
            .toHex()
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte)
    }

    private companion object {
        const val CACHE_DIRECTORY = "route-map-thumbnails"
        const val PNG_QUALITY = 100
    }
}
