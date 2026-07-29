package org.legalbet.mockupupload

import android.content.Context

/** Simple SharedPreferences wrapper for server + auth settings. */
object Prefs {
    private const val FILE = "mockup_prefs"

    private const val KEY_BASE_URL = "base_url"
    private const val KEY_TOKEN = "token"
    private const val KEY_AUTHOR = "author"
    private const val KEY_CF_ID = "cf_client_id"
    private const val KEY_CF_SECRET = "cf_client_secret"

    // Base URL is not a secret, so it can ship as a default.
    const val DEFAULT_BASE_URL = "https://img.lbtools.org"
    // No secrets baked into the app — token and author are entered in Settings and
    // live only in SharedPreferences on the device (any string in an APK is trivially
    // extractable with `strings`, so defaults here would leak).
    const val DEFAULT_TOKEN = ""
    const val DEFAULT_AUTHOR = ""

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun baseUrl(c: Context): String =
        sp(c).getString(KEY_BASE_URL, DEFAULT_BASE_URL)!!.trim().trimEnd('/')

    fun token(c: Context): String = sp(c).getString(KEY_TOKEN, DEFAULT_TOKEN)!!.trim()

    fun author(c: Context): String = sp(c).getString(KEY_AUTHOR, DEFAULT_AUTHOR)!!.trim()

    fun cfId(c: Context): String = sp(c).getString(KEY_CF_ID, "")!!.trim()

    fun cfSecret(c: Context): String = sp(c).getString(KEY_CF_SECRET, "")!!.trim()

    /** True once the required fields (token + author) are filled in. */
    fun isConfigured(c: Context): Boolean =
        token(c).isNotEmpty() && author(c).isNotEmpty()

    fun save(
        c: Context,
        baseUrl: String,
        token: String,
        author: String,
        cfId: String,
        cfSecret: String
    ) {
        sp(c).edit()
            .putString(KEY_BASE_URL, baseUrl.trim())
            .putString(KEY_TOKEN, token.trim())
            .putString(KEY_AUTHOR, author.trim())
            .putString(KEY_CF_ID, cfId.trim())
            .putString(KEY_CF_SECRET, cfSecret.trim())
            .apply()
    }
}
