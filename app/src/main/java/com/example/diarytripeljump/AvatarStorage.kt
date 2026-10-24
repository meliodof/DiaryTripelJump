package com.example.diarytripeljump

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File

/**
 * Копирует выбранное фото во внутреннюю память приложения и возвращает file://-адрес.
 *
 * Адрес из системного выбора фото (content://…) временный: после перезапуска приложения
 * доступ к нему пропадает, поэтому аватар «исчезал». Свой файл доступен всегда.
 */
object AvatarStorage {
    private const val MAX_SIDE = 768

    fun saveFromUri(context: Context, source: Uri): String? = try {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > MAX_SIDE * 2 || bounds.outHeight / sample > MAX_SIDE * 2) sample *= 2

        val decoded = resolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val rotation = resolver.openInputStream(source)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            if (rotation != 0f) postRotate(rotation)
            if (scale < 1f) postScale(scale, scale)
        }
        val finalBitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        val dir = File(context.filesDir, "avatars").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // старые аватары не копим
        val file = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        Uri.fromFile(file).toString()
    } catch (e: Exception) {
        null
    }
}
