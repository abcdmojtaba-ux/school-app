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
 * انتخاب فایل:
 *  - capture  => فقط دوربین
 *  - image/*  => فقط گالری/عکس
 *  - بقیه     => انتخاب‌گر فایل سیستم (PDF / Word / PowerPoint / همه فرمت‌ها)
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
        if (Build.VERSION.SDK_INT >= 21) {
            st.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http") || url.startsWith("file")) return false
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
                    ?.filter { it.isNotEmpty() } ?: emptyList()

                val wantsCamera = params?.isCaptureEnabled == true
                val imageOnly = accepts.isNotEmpty() && accepts.all { it.startsWith("image/") }
                val multiple = params?.mode == FileChooserParams.MODE_OPEN_MULTIPLE

                val intent: Intent = when {
                    // فقط دوربین
                    wantsCamera -> Intent(MediaStore.ACTION_IMAGE_CAPTURE)

                    // فقط عکس از گالری
                    imageOnly -> Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
                    }

                    // همه فرمت‌ها: PDF / Word / ...
                    else -> Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        // فقط اگر همه موارد MIME معتبر بودند فیلتر کن؛
                        // پسوندهایی مثل .csv باعث خراب شدن فیلتر می‌شوند
                        val mimes = accepts.filter { it.contains("/") && it != "*/*" }
                        if (mimes.isNotEmpty() && mimes.size == accepts.size) {
                            putExtra(Intent.EXTRA_MIME_TYPES, mimes.toTypedArray())
                        }
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
                    }
                }

                return try {
                    val finalIntent = if (wantsCamera) {
                        intent
                    } else {
                        Intent.createChooser(
                            intent,
                            if (imageOnly) "انتخاب عکس" else "انتخاب فایل (PDF / Word / عکس / ...)"
                        )
                    }
                    startActivityForResult(finalIntent, FILE_REQ)
                    true
                } catch (e: Exception) {
                    // fallback ساده
                    try {
                        startActivityForResult(
                            Intent.createChooser(
                                Intent(Intent.ACTION_GET_CONTENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                },
                                "انتخاب فایل"
                            ),
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
            // انتخاب چندتایی
            val clip = data?.clipData
            if (clip != null && clip.itemCount > 0) {
                cb.onReceiveValue(Array(clip.itemCount) { i -> clip.getItemAt(i).uri })
                return
            }
            // انتخاب تکی
            val single = data?.data
            if (single != null) {
                cb.onReceiveValue(arrayOf(single))
                return
            }
            // دوربین: thumbnail
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

    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
