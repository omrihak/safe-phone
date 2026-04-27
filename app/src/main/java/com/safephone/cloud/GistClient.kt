package com.safephone.cloud

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal client for the GitHub Gist REST API.
 *
 * Uses [HttpURLConnection] (matching the existing pattern in [com.safephone.update.InternalUpdateWorker])
 * to avoid pulling in OkHttp just for one feature. The API surface is intentionally narrow:
 *  - [readGist] downloads a gist's first matching file content,
 *  - [createGist] uploads a brand-new private gist and returns its id,
 *  - [updateGist] overwrites an existing gist's file in place.
 */
class GistClient(
    private val token: String,
    private val userAgent: String = "SafePhone-Android",
) {
    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val createReqAdapter = moshi.adapter(CreateGistRequest::class.java)
    private val updateReqAdapter = moshi.adapter(UpdateGistRequest::class.java)
    private val createRespAdapter = moshi.adapter(CreateGistResponse::class.java)
    private val readRespAdapter = moshi.adapter(ReadGistResponse::class.java)

    suspend fun readGist(gistId: String, fileName: String = DEFAULT_FILE): GistResult<ReadGist> =
        withContext(Dispatchers.IO) {
            val url = URL("$API_BASE/gists/${gistId.trim()}")
            val (code, body) = httpGet(url)
            if (code !in 200..299) {
                return@withContext GistResult.Failure(code, body.shortDescription())
            }
            val parsed = try {
                readRespAdapter.fromJson(body)
            } catch (_: Exception) {
                null
            }
            val files = parsed?.files
            if (parsed == null || files == null || files.isEmpty()) {
                return@withContext GistResult.Failure(code, "Gist has no readable files")
            }
            val file = files[fileName]
                ?: files.values.firstOrNull()
                ?: return@withContext GistResult.Failure(code, "Gist file not found")
            val content = file.content
                ?: return@withContext GistResult.Failure(code, "Gist file content was empty")
            GistResult.Success(ReadGist(content = content, updatedAt = parsed.updated_at.orEmpty()))
        }

    suspend fun createGist(
        content: String,
        description: String = "SafePhone policy",
        fileName: String = DEFAULT_FILE,
        public: Boolean = false,
    ): GistResult<String> = withContext(Dispatchers.IO) {
        val payload = CreateGistRequest(
            description = description,
            public = public,
            files = mapOf(fileName to GistFilePayload(content = content)),
        )
        val (code, body) = httpJson("POST", URL("$API_BASE/gists"), createReqAdapter.toJson(payload))
        if (code !in 200..299) {
            return@withContext GistResult.Failure(code, body.shortDescription())
        }
        val id = try {
            createRespAdapter.fromJson(body)?.id
        } catch (_: Exception) {
            null
        }
        if (id.isNullOrBlank()) {
            GistResult.Failure(code, "Gist created but response missing id")
        } else {
            GistResult.Success(id)
        }
    }

    suspend fun updateGist(
        gistId: String,
        content: String,
        fileName: String = DEFAULT_FILE,
    ): GistResult<Unit> = withContext(Dispatchers.IO) {
        val payload = UpdateGistRequest(
            files = mapOf(fileName to GistFilePayload(content = content)),
        )
        val (code, body) = httpJson(
            "PATCH",
            URL("$API_BASE/gists/${gistId.trim()}"),
            updateReqAdapter.toJson(payload),
        )
        if (code in 200..299) GistResult.Success(Unit)
        else GistResult.Failure(code, body.shortDescription())
    }

    private fun httpGet(url: URL): Pair<Int, String> {
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.applyCommonHeaders()
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            val code = conn.responseCode
            val body = conn.readBodySafely(code)
            code to body
        } catch (e: IOException) {
            -1 to (e.message ?: "I/O error")
        } finally {
            conn.disconnect()
        }
    }

    private fun httpJson(method: String, url: URL, json: String): Pair<Int, String> {
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.applyCommonHeaders()
            // PATCH isn't always honored by HttpURLConnection; fall back to POST + override header.
            if (method == "PATCH") {
                conn.requestMethod = "POST"
                conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else {
                conn.requestMethod = method
            }
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.outputStream.use { os -> os.write(json.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val body = conn.readBodySafely(code)
            code to body
        } catch (e: IOException) {
            -1 to (e.message ?: "I/O error")
        } finally {
            conn.disconnect()
        }
    }

    private fun HttpURLConnection.applyCommonHeaders() {
        setRequestProperty("Authorization", "Bearer $token")
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        setRequestProperty("User-Agent", userAgent)
    }

    private fun HttpURLConnection.readBodySafely(code: Int): String {
        val stream = if (code in 200..299) inputStream else errorStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    private fun String.shortDescription(): String {
        if (isBlank()) return "(no response body)"
        return if (length <= 240) this else take(240) + "…"
    }

    sealed class GistResult<out T> {
        data class Success<T>(val value: T) : GistResult<T>()
        data class Failure(val httpCode: Int, val message: String) : GistResult<Nothing>()
    }

    data class ReadGist(val content: String, val updatedAt: String)

    internal data class CreateGistRequest(
        val description: String,
        val public: Boolean,
        val files: Map<String, GistFilePayload>,
    )

    internal data class UpdateGistRequest(
        val files: Map<String, GistFilePayload>,
    )

    internal data class GistFilePayload(val content: String)

    internal data class CreateGistResponse(val id: String?)

    internal data class ReadGistResponse(
        val files: Map<String, GistFileBody>?,
        @Suppress("PropertyName") val updated_at: String?,
    )

    internal data class GistFileBody(val content: String?)

    companion object {
        const val DEFAULT_FILE: String = "safephone-policy.json"
        private const val API_BASE = "https://api.github.com"
        private const val TIMEOUT_MS = 15_000
    }
}
