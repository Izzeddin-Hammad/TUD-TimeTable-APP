package com.example.timetablescraper.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Manages APK download via Android's [DownloadManager] and the install intent.
 *
 * ## Flow
 * 1. [startDownload] enqueues the APK download with [DownloadManager].
 * 2. [downloadReceiver] is registered to listen for [DownloadManager.ACTION_DOWNLOAD_COMPLETE].
 * 3. [installDownloadedApk] exposes the file via [FileProvider] and fires an
 *    [Intent.ACTION_VIEW] with `application/vnd.android.package-archive` MIME type
 *    to trigger the system package installer.
 *
 * The receiver is self-contained — it obtains a reference to the Context
 * via the broadcast [Context].
 */
object UpdateManager {

    private const val TAG = "UpdateManager"

    /** The APK filename used for downloads. */
    const val APK_FILENAME = "TimeTable-update.apk"

    /** SharedPreferences key to remember the download ID across process death. */
    private const val PREFS_NAME = "update_prefs"
    private const val KEY_DOWNLOAD_ID = "last_download_id"

    /**
     * Start downloading the APK from [downloadUrl].
     *
     * Saves to the app's external files directory under `Downloads/`.
     * Stores the download ID so the [downloadReceiver] can retrieve it.
     *
     * @return `true` if the download was successfully enqueued.
     */
    fun startDownload(context: Context, downloadUrl: String): Boolean {
        return try {
            // Clean up any previous download file — DownloadManager will fail
            // to overwrite an existing file on some Android versions.
            val existing = getApkFile(context)
            if (existing.exists()) {
                existing.delete()
                Log.d(TAG, "Deleted previous APK file")
            }

            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("TimeTable Update")
                setDescription("Downloading latest version...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    APK_FILENAME
                )
            }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = dm.enqueue(request)

            // Persist the download ID so the receiver can retrieve it after process death
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_DOWNLOAD_ID, downloadId)
                .apply()

            Log.d(TAG, "Download enqueued with ID $downloadId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue download", e)
            false
        }
    }

    /**
     * Install the downloaded APK by firing a [Intent.ACTION_VIEW] intent.
     *
     * Uses [FileProvider] to securely expose the APK file URI to the system
     * package installer without requiring `READ_EXTERNAL_STORAGE` permission.
     *
     * @param context  Android context.
     * @param apkFile  The downloaded APK file.
     * @return `true` if the install intent was successfully launched.
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        return try {
            val authority = "${context.packageName}.fileprovider"
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                authority,
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(installIntent)
            Log.d(TAG, "Install intent launched for $apkUri")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch install intent", e)
            false
        }
    }

    /**
     * Resolve the downloaded APK [File] from the app's external files directory.
     *
     * @return The APK file, or `null` if it doesn't exist.
     */
    fun getApkFile(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        return File(dir, APK_FILENAME)
    }

    /**
     * Retrieve the last persisted download ID.
     */
    fun getLastDownloadId(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_DOWNLOAD_ID, -1L)
    }

    /**
     * Register the [downloadReceiver] for [DownloadManager.ACTION_DOWNLOAD_COMPLETE].
     */
    fun registerReceiver(context: Context, receiver: BroadcastReceiver) {
        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_NOT_EXPORTED
        )
    }
}
