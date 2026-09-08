package com.mojtaba.madreese

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val REQ = 2001
    private val startUrl = "https://panel.nikan-school.top/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this)
        setContentView(webView)

        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = true
        s.allowContentAccess = true
        if (Build.VERSION.SDK_INT >= 21) {
            s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        webView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                return try {
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        false
                    } else {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        true
                    }
                } catch (e: Exception) {
                    true
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                try {
                    fileCallback?.onReceiveValue(null)
                } catch (e: Exception) {
                }
                fileCallback = callback
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "image/*"
                return try {
                    startActivityForResult(Intent.createChooser(intent, "عکس"), REQ)
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }
        }

        webView.loadUrl(startUrl)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ) return
        val cb = fileCallback
        fileCallback = null
        if (cb == null) return
        if (resultCode != RESULT_OK || data == null) {
            cb.onReceiveValue(null)
            return
        }
        val uri = data.data
        if (uri != null) {
            cb.onReceiveValue(arrayOf(uri))
        } else {
            cb.onReceiveValue(null)
        }
    }
}
