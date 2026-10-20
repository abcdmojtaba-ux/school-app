package com.mojtaba.madrese

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

/**
 * سامانه نیکان — WebView
 * ذخیره پشتیبان: NikanAndroid.saveFile → Downloads
 */
class MainActivity : Activity() {

    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val FILE_REQ = 1001
    private val PERM_REQ = 1002

    inner class NikanBridge {
        @JavascriptInterface
        fun saveFile(dataUrlOrText: String?, fileName: String?, mime: String?): Boolean {
            if (dataUrlOrText.isNullOrEmpty()) return false
            val name = (fileName?.ifBlank { null }) ?: ("nikan-" + System.currentTimeMillis() + ".json")
            return try {
                val bytes: ByteArray
                val pureMime: String
                if (dataUrlOrText.startsWith("data:")) {
                    val comma = dataUrlOrText.indexOf(',')
                    if (comma < 0) return false
                    val meta = dataUrlOrText.substring(5, comma)
                    pureMime = meta.substringBefore(';').ifBlank { "application/octet-stream" }
                    bytes = Base64.decode(dataUrlOrText.substring(comma + 1), Base64.DEFAULT)
                } else {
                    pureMime = (mime?.substringBefore(';')) ?: "application/json"
                    bytes = dataUrlOrText.toByteArray(Charsets.UTF_8)
                }
                writeToDownloads(name, pureMime, bytes)
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "خطا در ذخیره فایل", Toast.LENGTH_SHORT).show()
                }
                false
            }
        }

        private fun writeToDownloads(name: String, mime: String, bytes: ByteArray): Boolean {
            return try {
                if (Build.VERSION.SDK_INT >= 29) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, mime)
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: return false
                    resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } else {
                    @Suppress("DEPRECATION")
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!dir.exists()) dir.mkdirs()
                    val outFile = File(dir, name)
                    FileOutputStream(outFile).use { it.write(bytes) }
                    @Suppress("DEPRECATION")
                    sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(outFile)))
                }
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "ذخیره شد در Downloads:\n$name", Toast.LENGTH_LONG).show()
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)
        askRuntimePermissions()

        // عملکرد WebView — ضد هنگ
        try { webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null) } catch (_: Exception) {}
        val st = webView.settings
        st.javaScriptEnabled = true
        st.domStorageEnabled = true
        st.databaseEnabled = true
        st.allowFileAccess = true
        st.allowContentAccess = true
        st.loadWithOverviewMode = true
        st.useWideViewPort = true
        st.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        st.mediaPlaybackRequiresUserGesture = true
        // سرعت رندر روی اندروید
        try { st.setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH) } catch (_: Exception) {}
        try { st.loadsImagesAutomatically = true } catch (_: Exception) {}
        try { st.blockNetworkImage = false } catch (_: Exception) {}
        // حافظه کمتر برای DOM خیلی بزرگ
        try {
            if (Build.VERSION.SDK_INT >= 19) {
                WebView.setWebContentsDebuggingEnabled(false)
            }
        } catch (_: Exception) {}
        if (Build.VERSION.SDK_INT >= 16) {
            @Suppress("DEPRECATION")
            st.allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            st.allowUniversalAccessFromFileURLs = true
        }
        if (Build.VERSION.SDK_INT >= 21) {
            st.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        // Offscreen pre-raster کمی کمک می‌کند
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                webView.setOffscreenPreRaster(true)
            }
        } catch (_: Exception) {}

        webView.addJavascriptInterface(NikanBridge(), "NikanAndroid")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                wv: WebView?,
                cb: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                try { fileCallback?.onReceiveValue(null) } catch (_: Exception) {}
                fileCallback = cb

                val accepts = params?.acceptTypes
                    ?.map { it.trim().lowercase() }
                    ?.filter { it.isNotEmpty() }
                    ?: emptyList()
                val wantsCamera = params?.isCaptureEnabled == true
                val imageOnly = accepts.isNotEmpty() &&
                    accepts.all { it.startsWith("image/") || it == "image" }
                val multiple = params?.mode == FileChooserParams.MODE_OPEN_MULTIPLE

                val intent: Intent = when {
                    wantsCamera -> Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    imageOnly -> Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
                    }
                    else -> {
                        val openDoc = if (Build.VERSION.SDK_INT >= 19) {
                            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
                            }
                        } else {
                            Intent(Intent.ACTION_GET_CONTENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
                            }
                        }
                        val mimes = accepts.filter {
                            it.contains("/") && it != "*/*" && !it.startsWith(".")
                        }
                        if (mimes.isNotEmpty() && mimes.size == accepts.size) {
                            openDoc.putExtra(Intent.EXTRA_MIME_TYPES, mimes.toTypedArray())
                        }
                        openDoc
                    }
                }

                return try {
                    val finalIntent = if (wantsCamera) intent
                    else Intent.createChooser(
                        intent,
                        if (imageOnly) "انتخاب عکس" else "انتخاب فایل (PDF / Word / عکس / ...)"
                    )
                    startActivityForResult(finalIntent, FILE_REQ)
                    true
                } catch (e: Exception) {
                    try {
                        val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        startActivityForResult(Intent.createChooser(fallback, "انتخاب فایل"), FILE_REQ)
                        true
                    } catch (e2: Exception) {
                        fileCallback = null
                        false
                    }
                }
            }

            override fun onJsAlert(view: WebView?, url: String?, msg: String?, r: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity).setMessage(msg)
                    .setPositiveButton("باشه") { _, _ -> r.confirm() }
                    .setOnCancelListener { r.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, msg: String?, r: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity).setMessage(msg)
                    .setPositiveButton("باشه") { _, _ -> r.confirm() }
                    .setNegativeButton("لغو") { _, _ -> r.cancel() }
                    .show()
                return true
            }

            override fun onJsPrompt(
                view: WebView?, url: String?, msg: String?, def: String?, r: JsPromptResult
            ): Boolean {
                val input = EditText(this@MainActivity)
                input.setText(def ?: "")
                AlertDialog.Builder(this@MainActivity).setMessage(msg).setView(input)
                    .setPositiveButton("باشه") { _, _ -> r.confirm(input.text.toString()) }
                    .setNegativeButton("لغو") { _, _ -> r.cancel() }
                    .show()
                return true
            }
        }

        // لود از سایت سرور (همیشه آخرین نسخه) — اگر قطع بود fallback به asset
        val isMother = try { BuildConfig.FLAVOR == "mother" } catch (_: Exception) { false }
        val serverUrl = if (isMother) {
            "https://panel.nikan-school.top/mother"
        } else {
            "https://panel.nikan-school.top/"
        }
        val assetUrl = if (isMother) {
            "file:///android_asset/mother-2.html"
        } else {
            "file:///android_asset/school-app-33.html"
        }
        try {
            // همیشه از شبکه بگیر تا با سرور یکی باشد
            webView.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        } catch (_: Exception) {}

        // اگر سرور بالا نبود، یک‌بار به فایل داخلی برگرد
        webView.webViewClient = object : WebViewClient() {
            private var fellBack = false
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http") || url.startsWith("file") || url.startsWith("data:")) return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    true
                } catch (e: Exception) {
                    true
                }
            }
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                if (!fellBack && failingUrl != null && failingUrl.startsWith("http")) {
                    fellBack = true
                    try {
                        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                    } catch (_: Exception) {}
                    view?.loadUrl(assetUrl)
                }
            }
            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: android.webkit.WebResourceError
            ) {
                if (Build.VERSION.SDK_INT >= 23) {
                    if (request.isForMainFrame && !fellBack) {
                        fellBack = true
                        try {
                            webView.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                        } catch (_: Exception) {}
                        view.loadUrl(assetUrl)
                    }
                }
            }
        }

        webView.loadUrl(serverUrl)
    }

    private fun askRuntimePermissions() {
        if (Build.VERSION.SDK_INT < 23) return
        val need = ArrayList<String>()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT < 29 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            ) {
                need.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), PERM_REQ)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != FILE_REQ) return
        val cb = fileCallback
        fileCallback = null
        if (cb == null) return
        if (resultCode != RESULT_OK) {
            cb.onReceiveValue(null)
            return
        }
        try {
            val clip = data?.clipData
            if (clip != null && clip.itemCount > 0) {
                cb.onReceiveValue(Array(clip.itemCount) { i -> clip.getItemAt(i).uri })
                return
            }
            val single = data?.data
            if (single != null) {
                try {
                    if (Build.VERSION.SDK_INT >= 19) {
                        contentResolver.takePersistableUriPermission(
                            single, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                } catch (_: Exception) {}
                cb.onReceiveValue(arrayOf(single))
                return
            }
            @Suppress("DEPRECATION")
            val bmp = data?.extras?.get("data") as? Bitmap
            if (bmp != null) {
                val file = File(cacheDir, "cam_" + System.currentTimeMillis() + ".jpg")
                FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                cb.onReceiveValue(arrayOf(Uri.fromFile(file)))
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        cb.onReceiveValue(null)
    }

    override fun onPause() {
        try {
            webView.onPause()
            webView.pauseTimers()
        } catch (_: Exception) {}
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        try {
            webView.onResume()
            webView.resumeTimers()
        } catch (_: Exception) {}
    }

        @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) webView.goBack()
        else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
