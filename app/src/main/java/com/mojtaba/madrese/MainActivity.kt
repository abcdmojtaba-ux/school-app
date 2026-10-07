package com.mojtaba.madrese

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import java.io.File
import java.io.FileOutputStream

/**
 * سامانه نیکان — WebView
 *
 * انتخاب فایل (onShowFileChooser):
 *  - capture / دوربین     => فقط دوربین
 *  - فقط image/*          => فقط گالری
 *  - */* یا PDF/Word/...  => انتخاب‌گر فایل سیستم (DocumentsUI)
 *    تا PDF، Word، PowerPoint، Excel، متن و عکس همه دیده شوند
 */
class MainActivity : Activity() {

    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val FILE_REQ = 1001
    private val PERM_REQ = 1002

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        askRuntimePermissions()

        val st = webView.settings
        st.javaScriptEnabled = true
        st.domStorageEnabled = true
        st.databaseEnabled = true
        st.allowFileAccess = true
        st.allowContentAccess = true
        st.loadWithOverviewMode = true
        st.useWideViewPort = true
        if (Build.VERSION.SDK_INT >= 16) {
            @Suppress("DEPRECATION")
            st.allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            st.allowUniversalAccessFromFileURLs = true
        }
        if (Build.VERSION.SDK_INT >= 21) {
            st.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        webView.webViewClient = object : WebViewClient() {
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
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                wv: WebView?,
                cb: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                try {
                    fileCallback?.onReceiveValue(null)
                } catch (_: Exception) {
                }
                fileCallback = cb

                val accepts = params?.acceptTypes
                    ?.map { it.trim().lowercase() }
                    ?.filter { it.isNotEmpty() }
                    ?: emptyList()

                val wantsCamera = params?.isCaptureEnabled == true
                // فقط وقتی همه‌ی acceptها image/* باشند گالری باز شود
                // اگر خالی، */* یا هر MIME غیرعکس باشد → انتخاب‌گر فایل کامل
                val imageOnly = accepts.isNotEmpty() &&
                    accepts.all { it.startsWith("image/") || it == "image" }

                val multiple = params?.mode == FileChooserParams.MODE_OPEN_MULTIPLE

                val intent: Intent = when {
                    wantsCamera -> {
                        Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    }

                    imageOnly -> {
                        Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/*"
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
                        }
                    }

                    else -> {
                        // ACTION_OPEN_DOCUMENT روی اندروید جدید DocumentsUI را باز می‌کند
                        // و همه پسوندها (PDF و docx و ...) را نشان می‌دهد
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

                        // فقط MIMEهای معتبر (با /) را فیلتر کن؛ پسوند مثل .csv را نادیده بگیر
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
                    val finalIntent = if (wantsCamera) {
                        intent
                    } else {
                        val title = when {
                            imageOnly -> "انتخاب عکس"
                            else -> "انتخاب فایل (PDF / Word / عکس / ...)"
                        }
                        Intent.createChooser(intent, title)
                    }
                    startActivityForResult(finalIntent, FILE_REQ)
                    true
                } catch (e: Exception) {
                    try {
                        val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        startActivityForResult(
                            Intent.createChooser(fallback, "انتخاب فایل"),
                            FILE_REQ
                        )
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
                view: WebView?,
                url: String?,
                msg: String?,
                def: String?,
                r: JsPromptResult
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

        val asset = if (BuildConfig.FLAVOR == "mother") "mother-2.html" else "school-app-33.html"
        webView.loadUrl("file:///android_asset/$asset")
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
        }
        if (need.isNotEmpty()) {
            requestPermissions(need.toTypedArray(), PERM_REQ)
        }
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
                            single,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                } catch (_: Exception) {
                }
                cb.onReceiveValue(arrayOf(single))
                return
            }
            @Suppress("DEPRECATION")
            val bmp = data?.extras?.get("data") as? Bitmap
            if (bmp != null) {
                val file = File(cacheDir, "cam_" + System.currentTimeMillis() + ".jpg")
                FileOutputStream(file).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                cb.onReceiveValue(arrayOf(Uri.fromFile(file)))
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        cb.onReceiveValue(null)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
