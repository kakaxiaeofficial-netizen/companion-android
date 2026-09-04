package com.example.companion.manager

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class FileTransferManager(private val context: Context) {

    private var currentFileOut: FileOutputStream? = null

    fun listDownloads(): String {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val files = downloadsDir?.listFiles() ?: arrayOf()
        val jsonArray = JSONArray()

        files.take(25).forEach { file ->
            val obj = JSONObject().apply {
                put("name", file.name)
                put("size", file.length())
                put("lastModified", file.lastModified())
            }
            jsonArray.put(obj)
        }
        return JSONObject().apply {
            put("type", "file_list")
            put("files", jsonArray)
        }.toString()
    }

    fun handleIncomingChunk(bytes: ByteArray, fileName: String) {
        if (currentFileOut == null) {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null && !downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val file = File(downloadsDir, fileName)
            currentFileOut = FileOutputStream(file)
        }
        currentFileOut?.write(bytes)
    }

    fun finalizeTransfer(fileName: String) {
        try {
            currentFileOut?.flush()
            currentFileOut?.close()
            currentFileOut = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
