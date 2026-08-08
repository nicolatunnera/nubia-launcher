package com.nubia.launcher.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/** Salva e recupera un'immagine usata come sfondo personalizzato del launcher. */
object WallpaperStore {

    private const val FILE_NAME = "home_wallpaper.jpg"

    /** Copia l'immagine scelta dall'utente nello storage privato. */
    fun save(context: Context, uri: Uri): Boolean {
        return try {
            val resolver = context.contentResolver
            val target = File(context.filesDir, FILE_NAME)
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            } ?: return false
            true
        } catch (_: Exception) {
            false
        }
    }

    fun path(context: Context): String = File(context.filesDir, FILE_NAME).absolutePath

    fun exists(context: Context): Boolean = File(context.filesDir, FILE_NAME).exists()

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
        } catch (_: Exception) {
        }
    }
}
