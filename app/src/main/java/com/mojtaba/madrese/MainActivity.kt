package com.mojtaba.madrese
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    // آدرس پنل مدرسه — اگر آدرس دیگری داری همین را عوض کن
    private val startUrl = "https://panel.nikan-school.top/"

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val cb = filePathCallback
            filePathCallback = null
            if (cb == null) return@registerForActivityResult

            if (result.resultCode != Activity.RESULT_OK) {
                cb.onReceiveValue(null)
                return@registerForActivityResult
            }

            val data = result.data
            val uris: Array<Uri>? = when {
                data?.clipData != null -> {
                    val clip = data.clipData!!
                    Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                }
                data?.data != null -> arrayOf(data.data!!)
                cameraImageUri != null -> arrayOf(cameraImageUri!!)
                else -> null
            }
            cb.onReceiveValue(uris)
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* ادامه می‌دهد */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        requestNeededPermissions()

        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.cacheMode = WebSettings.LOAD_DEFAULT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            s.mediaPlaybackRequiresUserGesture = false
        }

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {

            // انتخاب فایل / دوربین از input type=file در سایت
            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback

                // Intent دوربین
                val takePicture = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                try {
                    val photoFile = File(cacheDir, "nikan_cam_${System.currentTimeMillis()}.jpg")
                    cameraImageUri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "${packageName}.fileprovider",
                        photoFile
                    )
                    takePicture.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
                    takePicture.addFlags(
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    cameraImageUri = null
                }

                // Intent گالری
                val gallery = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }

                val chooser = Intent(Intent.ACTION_CHOOSER).apply {
                    putExtra(Intent.EXTRA_INTENT, gallery)
                    putExtra(Intent.EXTRA_TITLE, "انتخاب عکس")
                    if (cameraImageUri != null) {
                        putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(takePicture))
                    }
                }

                return try {
                    fileChooserLauncher.launch(chooser)
                    true
                } catch (e: Exception) {
                    filePathCallback = null
                    false
                }
            }

            // دوربین زنده (getUserMedia) داخل صفحه
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }

        webView.loadUrl(startUrl)
    }

    private fun requestNeededPermissions() {
        val need = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            need.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                need.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        }
        if (need.isNotEmpty()) {
            permissionLauncher.launch(need.toTypedArray())
        }
    }

    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}

