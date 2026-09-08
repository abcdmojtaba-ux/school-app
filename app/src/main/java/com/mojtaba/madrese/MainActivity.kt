package com.mojtaba.madreese

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    private val startUrl = "https://panel.nikan-school.top/"
    private val REQ_FILE = 1001
    private val REQ_PERM = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        // درخواست مجوز دوربین
        if (Build.VERSION.SDK_INT >= 23) {
            val need = mutableListOf<String>()
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.CAMERA)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                    need.add(Manifest.permission.READ_MEDIA_IMAGES)
                }
            }
            if (need.isNotEmpty()) {
                requestPermissions(need.toTypedArray(), REQ_PERM)
            }
        }

        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.mediaPlaybackRequiresUserGesture = false
        if (Build.VERSION.SDK_INT >= 21) {
            s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback

                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                try {
                    val photo = File(cacheDir, "cam_${System.currentTimeMillis()}.jpg")
                    cameraImageUri = FileProvider.getUriForFile(
                        this@MainActivity,
                        packageName + ".fileprovider",
                        photo
                    )
                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
                    cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    cameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    cameraImageUri = null
                }

                val galleryIntent = Intent(Intent.ACTION_GET_CONTENT)
                galleryIntent.addCategory(Intent.CATEGORY_OPENABLE)
                galleryIntent.type = "image/*"

                val chooser = Intent(Intent.ACTION_CHOOSER)
                chooser.putExtra(Intent.EXTRA_INTENT, galleryIntent)
                chooser.putExtra(Intent.EXTRA_TITLE, "عکس")
                if (cameraImageUri != null) {
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
                }

                try {
                    startActivityForResult(chooser, REQ_FILE)
                } catch (e: Exception) {
                    filePathCallback = null
                    return false
                }
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (Build.VERSION.SDK_INT >= 21) {
                    request?.grant(request.resources)
                }
            }
        }

        webView.loadUrl(startUrl)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_FILE) return
        val cb = filePathCallback
        filePathCallback = null
        if (cb == null) return

        if (resultCode != RESULT_OK) {
            cb.onReceiveValue(null)
            return
        }

        val uris: Array<Uri>? = when {
            data?.clipData != null -> {
                val c = data.clipData!!
                Array(c.itemCount) { i -> c.getItemAt(i).uri }
            }
            data?.data != null -> arrayOf(data.data!!)
            cameraImageUri != null -> arrayOf(cameraImageUri!!)
            else -> null
        }
        cb.onReceiveValue(uris)
    }

    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
