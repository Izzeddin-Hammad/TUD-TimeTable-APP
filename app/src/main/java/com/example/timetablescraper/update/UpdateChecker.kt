package com.example.timetablescraper.update

import com.example.timetablescraper.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Queries the GitHub Contents API for APK files in the `releases/` directory
 * and returns the latest version's download URL.
 *
 * ## Endpoint
 * `GET /repos/Izzeddin-Hammad/TUD-TimeTable-APP/contents/releases`
 *
 * ## Response parsing
 * The Contents API returns a JSON array of file entries. Each entry has a
 * `name` (e.g. "TimeTable-v1.17-debug.apk") and a `download_url`.
 * We scan all entries for APK files, parse the version from the filename,
 * and pick the newest one.
 *
 * ## Version comparison
 * The remote version is compared against the local [BuildConfig.VERSION_NAME]
 * using semantic versioning rules (see [isNewerThan]).
 */
object UpdateChecker {

    /** GitHub owner/repo. */
    private const val GITHUB_REPO = "Izzeddin-Hammad/TUD-TimeTable-APP"

    /** GitHub API base URL. */
    private const val GITHUB_API_BASE = "https://api.github.com"

    /** Contents API URL for the releases directory. */
    private val CONTENTS_URL =
        "$GITHUB_API_BASE/repos/$GITHUB_REPO/contents/releases"

    /** Regex to parse version from APK filename: "TimeTable-v1.17-debug.apk" → "v1.17" */
    private val APK_VERSION_REGEX = Regex("TimeTable-v([\\d.]+)-debug\\.apk")

    /** Lightweight OkHttp client dedicated to update checks (no rate-limiting needed). */
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Result of an update check.
     *
     * @param updateAvailable `true` if the remote version is strictly newer.
     * @param remoteVersion   The remote version string (e.g. "1.17"), or `null` on error.
     * @param downloadUrl     The APK download URL, or `null`.
     * @param errorMessage    Human-readable error description, or `null` on success.
     */
    data class UpdateResult(
        val updateAvailable: Boolean,
        val remoteVersion: String? = null,
        val downloadUrl: String? = null,
        val errorMessage: String? = null
    )

    /**
     * Check for a newer version of the app by listing the `releases/` directory
     * on GitHub via the Contents API.
     *
     * Called from a coroutine on [Dispatchers.IO].
     *
     * @return [UpdateResult] with the comparison outcome.
     */
    suspend fun checkForUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(CONTENTS_URL)
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateResult(
                        updateAvailable = false,
                        errorMessage = "GitHub API returned HTTP ${response.code}"
                    )
                }

                val body = response.body?.string()
                    ?: return@withContext UpdateResult(
                        updateAvailable = false,
                        errorMessage = "Empty response from GitHub API"
                    )

                val entries = JSONArray(body)
                if (entries.length() == 0) {
                    return@withContext UpdateResult(
                        updateAvailable = false,
                        errorMessage = "No APK files found in releases/"
                    )
                }

                // Scan all files for APK entries, extract version and URL
                var bestVersion: String? = null
                var bestDownloadUrl: String? = null

                for (i in 0 until entries.length()) {
                    val entry = entries.getJSONObject(i)
                    val name = entry.optString("name", "")
                    val match = APK_VERSION_REGEX.find(name) ?: continue

                    val version = match.groupValues[1] // e.g. "1.17"
                    val downloadUrl = entry.optString("download_url", "")

                    if (downloadUrl.isNotEmpty()) {
                        if (bestVersion == null || isNewerThan("v$version", "v$bestVersion")) {
                            bestVersion = version
                            bestDownloadUrl = downloadUrl
                        }
                    }
                }

                if (bestVersion == null) {
                    return@withContext UpdateResult(
                        updateAvailable = false,
                        errorMessage = "No valid APK files found"
                    )
                }

                val localVersion = BuildConfig.VERSION_NAME
                val updateAvailable = isNewerThan("v$bestVersion", localVersion)

                UpdateResult(
                    updateAvailable = updateAvailable,
                    remoteVersion = bestVersion,
                    downloadUrl = bestDownloadUrl
                )
            }
        } catch (e: Exception) {
            UpdateResult(
                updateAvailable = false,
                errorMessage = e.message ?: "Unknown error checking for updates"
            )
        }
    }

    /**
     * Compare two version strings using semantic versioning.
     *
     * Strips a leading "v" prefix (e.g. "v1.4" → "1.4") before comparison.
     * Returns `true` if [remote] is strictly greater than [local].
     *
     * Supports dotted numeric versions (e.g. "1.2", "1.2.1", "2.0").
     * Non-numeric segments are compared lexicographically as a fallback.
     */
    private fun isNewerThan(remote: String, local: String): Boolean {
        val r = remote.trimStart('v', 'V')
        val l = local.trimStart('v', 'V')

        val rParts = r.split(".")
        val lParts = l.split(".")

        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val rp = rParts.getOrElse(i) { "0" }
            val lp = lParts.getOrElse(i) { "0" }

            val rn = rp.toIntOrNull()
            val ln = lp.toIntOrNull()

            if (rn != null && ln != null) {
                if (rn != ln) return rn > ln
            } else {
                // Fallback to lexicographic comparison
                val cmp = rp.compareTo(lp)
                if (cmp != 0) return cmp > 0
            }
        }
        return false // versions are equal
    }
}
