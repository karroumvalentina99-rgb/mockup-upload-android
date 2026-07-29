package org.legalbet.mockupupload

import android.content.Context
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UploadResult(val success: Boolean, val url: String?, val message: String)

/**
 * Posts a screenshot to the Mockup Studio ingestion endpoint as base64 JSON.
 * Blocking — call from a background thread.
 */
object Uploader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun upload(
        ctx: Context,
        imageBytes: ByteArray,
        filename: String,
        sourceUrl: String
    ): UploadResult {
        // Require the non-secret defaults to be overridden in Settings first, so
        // nothing uploads under an empty/borrowed author or without a token.
        if (Prefs.token(ctx).isEmpty() || Prefs.author(ctx).isEmpty()) {
            return UploadResult(
                false, null,
                "Set the API token and Author email in Settings first."
            )
        }

        val url = Prefs.baseUrl(ctx) + "/mockup-upload/upload"
        val b64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

        val payload = JSONObject().apply {
            put("filename", filename)
            put("image", b64)
            put("source_url", sourceUrl)
            put("author", Prefs.author(ctx))
            // Keep the full-resolution screenshot; false makes the server downscale
            // to a 750px palette-mode PNG, which defeats "straight to preview".
            put("keep_original", true)
        }.toString()

        val builder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + Prefs.token(ctx))
            .post(payload.toRequestBody(JSON))

        val cfId = Prefs.cfId(ctx)
        val cfSecret = Prefs.cfSecret(ctx)
        if (cfId.isNotEmpty() && cfSecret.isNotEmpty()) {
            builder.header("CF-Access-Client-Id", cfId)
            builder.header("CF-Access-Client-Secret", cfSecret)
        }

        return try {
            client.newCall(builder.build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()

                // Cloudflare Access serves its sign-in page (often HTTP 200) when the
                // edge blocks a non-browser client that lacks a service token.
                val looksLikeCfAccess =
                    body.contains("Cloudflare Access", ignoreCase = true) ||
                    body.contains("<html", ignoreCase = true)

                when {
                    looksLikeCfAccess -> UploadResult(
                        false, null,
                        "Blocked by Cloudflare Access. Add the service token " +
                            "(Client Id / Secret) in Settings."
                    )
                    resp.isSuccessful -> {
                        val u = runCatching { JSONObject(body).optString("url") }
                            .getOrNull()
                            ?.takeIf { it.isNotEmpty() }
                        UploadResult(true, u, "Uploaded")
                    }
                    else -> {
                        val hint = when (resp.code) {
                            401 -> "401 Unauthorized — check the API token in Settings."
                            403 -> "403 Forbidden — Cloudflare service token required."
                            else -> "HTTP ${resp.code}"
                        }
                        UploadResult(false, null, hint + "\n" + body.take(200))
                    }
                }
            }
        } catch (e: Exception) {
            UploadResult(false, null, "Network error: ${e.message}")
        }
    }

    /** Uploads a 1x1 PNG to verify server settings / auth. */
    fun testConnection(ctx: Context): UploadResult {
        val onePx = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
            Base64.DEFAULT
        )
        return upload(ctx, onePx, "android-connection-test.png", "")
    }
}
