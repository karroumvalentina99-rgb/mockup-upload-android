package org.legalbet.mockupupload

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShareActivity : AppCompatActivity() {

    private lateinit var uris: List<Uri>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        uris = extractUris(intent)
        if (uris.isEmpty()) {
            Toast.makeText(this, "No image received", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContentView(R.layout.activity_share)

        val thumb = findViewById<ImageView>(R.id.thumb)
        val filename = findViewById<TextInputEditText>(R.id.filename)
        val sourceUrl = findViewById<TextInputEditText>(R.id.sourceUrl)
        val status = findViewById<TextView>(R.id.shareStatus)
        val progress = findViewById<ProgressBar>(R.id.shareProgress)
        val title = findViewById<TextView>(R.id.title)
        val btnUpload = findViewById<MaterialButton>(R.id.btnDoUpload)
        val btnCancel = findViewById<MaterialButton>(R.id.btnCancel)

        filename.setText(defaultName())
        if (uris.size > 1) {
            title.text = getString(R.string.share_label) + "  (${uris.size} images)"
        }

        // Prefill source URL if the share included a page link.
        intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.let { text ->
            if (text.startsWith("http://") || text.startsWith("https://")) {
                sourceUrl.setText(text)
            }
        }

        // Load a downsampled preview off the UI thread.
        Thread {
            val bmp = try {
                contentResolver.openInputStream(uris[0]).use { input ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeStream(input, null, opts)
                }
            } catch (e: Exception) {
                null
            }
            if (bmp != null) runOnUiThread { thumb.setImageBitmap(bmp) }
        }.start()

        btnCancel.setOnClickListener { finish() }

        btnUpload.setOnClickListener {
            val base = filename.text?.toString()?.trim().orEmpty()
            val src = sourceUrl.text?.toString()?.trim().orEmpty()
            setBusy(true, btnUpload, btnCancel, progress, status)
            uploadAll(base, src, status, progress, btnUpload, btnCancel)
        }
    }

    private fun uploadAll(
        baseName: String,
        source: String,
        status: TextView,
        progress: ProgressBar,
        btnUpload: MaterialButton,
        btnCancel: MaterialButton
    ) {
        Thread {
            val results = ArrayList<UploadResult>()
            for ((i, uri) in uris.withIndex()) {
                val name = if (uris.size == 1) ensureExt(baseName)
                else ensureExt("$baseName-${i + 1}")

                runOnUiThread { status.text = "Uploading ${i + 1}/${uris.size}…" }

                val bytes = try {
                    contentResolver.openInputStream(uri).use { it!!.readBytes() }
                } catch (e: Exception) {
                    null
                }

                if (bytes == null) {
                    results.add(UploadResult(false, null, "Could not read image"))
                } else {
                    results.add(Uploader.upload(this, bytes, name, source))
                }
            }

            runOnUiThread {
                setBusy(false, btnUpload, btnCancel, progress, status)
                val ok = results.count { it.success }
                if (ok == uris.size) {
                    val url = results.firstOrNull { it.success }?.url
                    if (url != null) copyToClipboard(url)
                    Toast.makeText(
                        this,
                        "Uploaded $ok/${uris.size} ✓" + if (url != null) " (URL copied)" else "",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                } else {
                    val firstErr = results.firstOrNull { !it.success }?.message ?: "Upload failed"
                    status.text = "Uploaded $ok/${uris.size}. Error: $firstErr"
                }
            }
        }.start()
    }

    private fun setBusy(
        busy: Boolean,
        btnUpload: MaterialButton,
        btnCancel: MaterialButton,
        progress: ProgressBar,
        status: TextView
    ) {
        btnUpload.isEnabled = !busy
        btnCancel.isEnabled = !busy
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        if (busy) status.text = "Uploading…"
    }

    private fun ensureExt(name: String): String {
        val t = name.trim().ifEmpty { defaultName() }
        return if (t.contains('.')) t else "$t.png"
    }

    private fun defaultName(): String {
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "screenshot-$ts.png"
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Mockup URL", text))
    }

    @Suppress("DEPRECATION")
    private fun extractUris(intent: Intent): List<Uri> {
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val u = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                listOfNotNull(u)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                list ?: emptyList()
            }
            else -> emptyList()
        }
    }
}
