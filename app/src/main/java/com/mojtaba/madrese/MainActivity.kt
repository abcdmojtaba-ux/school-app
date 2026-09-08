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
 * نسخه پایدار سامانه نیکان
 * - باز شدن از فایل داخل اپ (assets)
 * - گالری + دوربین
 * - دیالوگ‌های JS
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

        askCameraPermission()

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
                p: FileChooserParams?
            ): Boolean {
                try {
                    fileCallback?.onReceiveValue(null)
                } catch (e: Exception) {
                }
                fileCallback = cb

                val gallery = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }

                // دوربین: بدون FileProvider (نتیجه thumbnail در extras)
                val camera = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

                val chooser = Intent(Intent.ACTION_CHOOSER).apply {
                    putExtra(Intent.EXTRA_INTENT, gallery)
                    putExtra(Intent.EXTRA_TITLE, "انتخاب عکس")
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(camera))
                }

                return try {
                    startActivityForResult(chooser, FILE_REQ)
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
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

        // باز شدن از فایل داخل اپ — نه از اینترنت
        val asset = if (BuildConfig.FLAVOR == "mother") "mother-2.html" else "school-app-33.html"
        webView.loadUrl("file:///android_asset/$asset")
    }

    private fun askCameraPermission() {
        if (Build.VERSION.SDK_INT < 23) return
        val need = ArrayList<String>()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.READ_MEDIA_IMAGES)
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
            // گالری / فایل
            if (data?.data != null) {
                cb.onReceiveValue(arrayOf(data.data!!))
                return
            }
            if (data?.clipData != null) {
                val clip = data.clipData!!
                cb.onReceiveValue(Array(clip.itemCount) { i -> clip.getItemAt(i).uri })
                return
            }
            // دوربین: عکس کوچک در extras
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
