package com.mojtaba.madreese

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val startUrl = "https://panel.nikan-school.top/"
    private val REQ_FILE = 1001
    private val REQ_PERM = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        askPermissions()

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false
        if (Build.VERSION.SDK_INT >= 21) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                try {
                    filePathCallback?.onReceiveValue(null)
                } catch (e: Exception) {
                }
                filePathCallback = callback

                val gallery = Intent(Intent.ACTION_GET_CONTENT)
                gallery.addCategory(Intent.CATEGORY_OPENABLE)
                gallery.type = "image/*"

                val camera = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

                val chooser = Intent(Intent.ACTION_CHOOSER)
                chooser.putExtra(Intent.EXTRA_INTENT, gallery)
                chooser.putExtra(Intent.EXTRA_TITLE, "عکس")
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(camera))

                return try {
                    startActivityForResult(chooser, REQ_FILE)
                    true
                } catch (e: Exception) {
                    filePathCallback = null
                    false
                }
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (Build.VERSION.SDK_INT >= 21 && request != null) {
                    request.grant(request.resources)
                }
            }
        }

        webView.loadUrl(startUrl)
    }

    private fun askPermissions() {
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
            requestPermissions(need.toTypedArray(), REQ_PERM)
        }
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

        try {
            if (data != null && data.data != null) {
                cb.onReceiveValue(arrayOf(data.data!!))
                return
            }
            if (data != null && data.clipData != null) {
                val clip = data.clipData!!
                val list = Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                cb.onReceiveValue(list)
                return
            }
            val bmp = data?.extras?.get("data") as? Bitmap
            if (bmp != null) {
                val file = File(cacheDir, "cam_" + System.currentTimeMillis() + ".jpg")
                val out = FileOutputStream(file)
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.flush()
                out.close()
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
