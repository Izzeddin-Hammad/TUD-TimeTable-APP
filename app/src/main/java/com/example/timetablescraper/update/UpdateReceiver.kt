package com.example.timetablescraper.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

/**
 * BroadcastReceiver that listens for [DownloadManager.ACTION_DOWNLOAD_COMPLETE].
 *
 * When the APK download finishes, it verifies the download status and
 * launches the system package installer via [UpdateManager.installApk].
 *
 * Registered dynamically by the Activity/Application so it doesn't need
 * to be declared in AndroidManifest.xml.
 */
class UpdateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "UpdateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return

        // Verify this is our download
        val lastId = UpdateManager.getLastDownloadId(context)
        if (downloadId != lastId) {
            Log.d(TAG, "Download ID $downloadId does not match last enqueued $lastId — ignoring")
            return
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = dm.query(query)

        try {
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                )

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        Log.d(TAG, "APK download successful — launching installer")
                        val apkFile = UpdateManager.getApkFile(context)
                        if (apkFile.exists()) {
                            UpdateManager.installApk(context, apkFile)
                        } else {
                            Log.e(TAG, "APK file not found at ${apkFile.absolutePath}")
                        }
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                        )
                        Log.e(TAG, "APK download failed with reason $reason")
                    }
                    else -> {
                        Log.d(TAG, "Download status: $status — waiting")
                    }
                }
            }
        } finally {
            cursor.close()
        }
    }
}
