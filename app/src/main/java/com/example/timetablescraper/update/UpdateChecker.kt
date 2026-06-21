package com.example.timetablescraper.update

import com.example.timetablescraper.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Queries the GitLab Releases API for the latest release tag and APK download URL.
 *
 * ## Endpoint
 * `GET /api/v4/projects/Izzeddin-Hammad%2FTUD-TimeTable-APP/releases`
 *
 * ## Response parsing
 * Extracts the first (latest) release's `tag_name` (e.g. "v1.4") and the
 * direct APK download URL from `assets.links[].url`.
 *
 * ## Version comparison
 * The remote `tag_name` is compared against the local [BuildConfig.VERSION_NAME]
 * using semantic versioning rules (see [isNewerThan]).
 */
object UpdateChecker {

    /** GitLab project path, URL-encoded. */
    private const val GITLAB_PROJECT_PATH = "Izzeddin-Hammad%2FTUD-TimeTable-APP"

    /** GitLab API v4 base URL. */
    private const val GITLAB_API_BASE = "https://gitlab.com/api/v4"

    /** Full releases endpoint URL. */
    private val RELEASES_URL =
        "$GITLAB_API_BASE/projects/$GITLAB_PROJECT_PATH/releases"

    /** Lightweight OkHttp client dedicated to update checks (no rate-limiting needed). */
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Result of an update check.
     *
     * @param updateAvailable `true` if the remote version is strictly newer.
     * @param remoteVersion   The remote tag name (e.g. "v1.4"), or `null` on error.
     * @param downloadUrl     The APK download URL from the release assets, or `null`.
     * @param errorMessage    Human-readable error description, or `null` on success.
     */
    data class UpdateResult(
        val updateAvailable: Boolean,
        val remoteVersion: String? = null,
        val downloadUrl: String? = null,
        val errorMessage: String? = null
    )

    /**
     * Query the GitLab API for the latest release.
     *
     * Called from a coroutine on [Dispatchers.IO].
     *
     * @return [UpdateResult] with the comparison outcome.
     */
    suspend fun checkForUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateResult(
                        updateAvailable = false,
                        errorMessage = "GitLab API returned HTTP ${response.code}"
                    )
                }

                val body = response.body?.string()
                    ?: return@withContext UpdateResult(
                        updateAvailable = false,
                        errorMessage = "Empty response from GitLab API"
                    )

                val releases = JSONArray(body)
                if (releases.length() == 0) {
                    return@withContext UpdateResult(
                        updateAvailable = false,
                        errorMessage = "No releases found on GitLab"
                    )
                }

                // The first element is the latest release
                val latest = releases.getJSONObject(0)
                val tagName = latest.getString("tag_name")

                // Extract the APK download URL from assets.links
                val downloadUrl = extractDownloadUrl(latest)

                val localVersion = BuildConfig.VERSION_NAME
                val updateAvailable = isNewerThan(tagName, localVersion)

                UpdateResult(
                    updateAvailable = updateAvailable,
                    remoteVersion = tagName,
                    downloadUrl = downloadUrl
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
     * Extract the APK download URL from a release JSON object.
     *
     * Looks through `assets.links` for a link whose `name` or `url` ends with `.apk`.
     * Falls back to the first link URL if no `.apk` extension is found.
     */
    private fun extractDownloadUrl(release: JSONObject): String? {
        val assets = release.optJSONObject("assets")
        val links = assets?.optJSONArray("links") ?: return null

        for (i in 0 until links.length()) {
            val link = links.getJSONObject(i)
            val url = link.optString("url", null) ?: continue
            val name = link.optString("name", "")
            // Prefer the APK link
            if (name.endsWith(".apk") || url.endsWith(".apk")) {
                return url
            }
        }
        // Fallback: return the first link's URL
        return if (links.length() > 0) {
            links.getJSONObject(0).optString("url", null)
        } else null
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
