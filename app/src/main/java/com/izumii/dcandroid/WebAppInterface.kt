package com.izumii.dcandroid

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class WebAppInterface(
    private val context: Context,
    private val onZoomStateChanged: ((Boolean) -> Unit)? = null
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val bgExecutor = Executors.newSingleThreadExecutor()

    @JavascriptInterface
    fun copyText(text: String) {
        mainHandler.post {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("Discord Text", text)
                clipboard?.setPrimaryClip(clip)
                Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            } catch (ignored: Exception) {}
        }
    }

    @JavascriptInterface
    fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun getAppVersion(): String {
        return BuildConfig.VERSION_NAME
    }

    @JavascriptInterface
    fun setZoomEnabled(enabled: Boolean) {
        mainHandler.post {
            onZoomStateChanged?.invoke(enabled)
        }
    }

    @JavascriptInterface
    fun postNotification(title: String, message: String) {
        mainHandler.post {
            com.izumii.dcandroid.service.ConnectionKeeperService.showChatNotification(context, title, message)
        }
    }

    @JavascriptInterface
    fun openExternalUrl(url: String) {
        mainHandler.post {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(intent, context.getString(R.string.open_with)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Saves base64 / data-URI downloads (blobs and Discord attachments)
     * to the public Downloads folder.
     */
    @JavascriptInterface
    fun saveBase64File(dataUri: String, suggestedFileName: String, mimeTypeHint: String) {
        bgExecutor.execute {
            try {
                var cleanName = suggestedFileName.trim()
                if (cleanName.isEmpty()) {
                    cleanName = "Discord_${System.currentTimeMillis()}"
                }
                cleanName = cleanName.replace("/", "_").replace("\\", "_").replace("..", "_")

                var mime = mimeTypeHint.trim()
                val commaIndex = dataUri.indexOf(',')
                val header = if (commaIndex != -1) dataUri.substring(0, commaIndex) else ""
                val payload = if (commaIndex != -1) dataUri.substring(commaIndex + 1) else dataUri

                if (mime.isEmpty() && header.startsWith("data:")) {
                    val semicolonIndex = header.indexOf(';')
                    val endIdx = if (semicolonIndex != -1) semicolonIndex else header.length
                    if (endIdx > 5) mime = header.substring(5, endIdx)
                }
                if (mime.isEmpty()) mime = "application/octet-stream"

                val extFromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: when {
                    mime.contains("json") -> "json"
                    mime.contains("text/plain") -> "txt"
                    mime.contains("png") -> "png"
                    mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
                    mime.contains("webp") -> "webp"
                    mime.contains("gif") -> "gif"
                    mime.contains("mp4") -> "mp4"
                    mime.contains("ogg") -> "ogg"
                    mime.contains("mp3") -> "mp3"
                    else -> "bin"
                }

                val lastDot = cleanName.lastIndexOf('.')
                val hasValidExt = lastDot > 0 && (cleanName.length - lastDot - 1) in 2..5
                if (!hasValidExt) {
                    cleanName = "$cleanName.$extFromMime"
                }

                val bytes = if (header.contains(";base64", ignoreCase = true)) {
                    Base64.decode(payload, Base64.DEFAULT)
                } else {
                    (Uri.decode(payload) ?: payload).toByteArray(Charsets.UTF_8)
                }

                val savedUri = saveBytesToDownloads(context, cleanName, mime, bytes)
                mainHandler.post {
                    if (savedUri != null) {
                        Toast.makeText(context, "${context.getString(R.string.download_completed)}: $cleanName", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.download_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    Toast.makeText(context, "${context.getString(R.string.download_failed)}: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Standard HTTP/HTTPS file downloads via Android DownloadManager with session cookies.
     */
    @JavascriptInterface
    fun downloadHttpFile(url: String, suggestedFileName: String, mimeTypeHint: String) {
        mainHandler.post {
            try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    val mime = if (mimeTypeHint.isNotEmpty()) mimeTypeHint else MimeTypeMap.getFileExtensionFromUrl(url)?.let {
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(it)
                    } ?: "application/octet-stream"
                    setMimeType(mime)
                    val cookies = CookieManager.getInstance().getCookie(url)
                    if (!cookies.isNullOrEmpty()) {
                        addRequestHeader("cookie", cookies)
                    }
                    val fileName = if (suggestedFileName.isNotEmpty()) suggestedFileName else URLUtil.guessFileName(url, null, mime)
                    setTitle(fileName)
                    setDescription("Downloading...")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                }
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                dm?.enqueue(request)
                Toast.makeText(context, R.string.download_started, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "${context.getString(R.string.download_failed)}: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveBytesToDownloads(context: Context, fileName: String, mimeType: String, bytes: ByteArray): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { stream ->
                    stream.write(bytes)
                }
            }
            uri
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            var targetFile = File(downloadsDir, fileName)
            if (targetFile.exists()) {
                val dotIndex = fileName.lastIndexOf('.')
                val name = if (dotIndex != -1) fileName.substring(0, dotIndex) else fileName
                val ext = if (dotIndex != -1) fileName.substring(dotIndex) else ""
                targetFile = File(downloadsDir, "${name}_${System.currentTimeMillis()}$ext")
            }
            FileOutputStream(targetFile).use { stream ->
                stream.write(bytes)
            }
            MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf(mimeType), null)
            Uri.fromFile(targetFile)
        }
    }
}
