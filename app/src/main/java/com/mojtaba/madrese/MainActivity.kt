package com.mojtaba.madrese

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val FILE_REQ = 1001

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        val st = webView.settings
        st.javaScriptEnabled = true
        st.domStorageEnabled = true
        st.databaseEnabled = true
        st.allowFileAccess = true
        st.loadWithOverviewMode = true
        st.useWideViewPort = true

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http") || url.startsWith("file")) return false
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) {}
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(wv: WebView?, cb: ValueCallback<Array<Uri>>?, p: FileChooserParams?): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = cb
                val i = Intent(Intent.ACTION_GET_CONTENT)
                i.addCategory(Intent.CATEGORY_OPENABLE)
                i.type = "*/*"
                try { startActivityForResult(Intent.createChooser(i, "انتخاب فایل"), FILE_REQ) }
                catch (e: Exception) { fileCallback?.onReceiveValue(null); fileCallback = null }
                return true
            }

            override fun onJsAlert(view: WebView?, url: String?, msg: String?, r: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity).setMessage(msg)
                    .setPositiveButton("باشه") { _, _ -> r.confirm() }
                    .setOnCancelListener { r.cancel() }.show()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, msg: String?, r: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity).setMessage(msg)
                    .setPositiveButton("باشه") { _, _ -> r.confirm() }
                    .setNegativeButton("لغو") { _, _ -> r.cancel() }.show()
                return true
            }

            override fun onJsPrompt(view: WebView?, url: String?, msg: String?, def: String?, r: JsPromptResult): Boolean {
                val input = EditText(this@MainActivity)
                input.setText(def ?: "")
                AlertDialog.Builder(this@MainActivity).setMessage(msg).setView(input)
                    .setPositiveButton("باشه") { _, _ -> r.confirm(input.text.toString()) }
                    .setNegativeButton("لغو") { _, _ -> r.cancel() }.show()
                return true
            }
        }

        val asset = if (BuildConfig.FLAVOR == "mother") "mother-2.html" else "school-app-33.html"
        webView.loadUrl("file:///android_asset/$asset")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_REQ) {
            val res = if (resultCode == RESULT_OK && data != null && data.data != null) arrayOf(data.data!!) else null
            fileCallback?.onReceiveValue(res)
            fileCallback = null
        }
    }
override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
