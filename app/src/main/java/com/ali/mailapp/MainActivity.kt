package com.ali.mailapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.webkit.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorLayout: LinearLayout
    private lateinit var rootLayout: FrameLayout

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    companion object {
        const val GMAIL_URL =
            "https://accounts.google.com/signup/v2/webcreateaccount" +
            "?flowName=GlifWebSignIn&flowEntry=SignUp&hl=en"
    }

    // ── Camera launcher ──
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null)
            fileUploadCallback?.onReceiveValue(arrayOf(cameraImageUri!!))
        else
            fileUploadCallback?.onReceiveValue(null)
        fileUploadCallback = null
        cameraImageUri = null
    }

    // ── File picker launcher ──
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = if (result.resultCode == Activity.RESULT_OK)
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        else null
        fileUploadCallback?.onReceiveValue(uris)
        fileUploadCallback = null
    }

    // ── Permission launchers ──
    private val cameraPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = null
        }
    }

    private val storagePermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) launchFilePicker()
        else {
            Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show()
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full screen — white status bar
        setupStatusBar()

        // Build UI programmatically — no XML layout needed
        buildUI()

        // Setup WebView
        setupWebView()

        // Load Gmail
        if (isNetworkAvailable()) {
            webView.loadUrl(GMAIL_URL)
        } else {
            showError()
        }
    }

    // ─────────────────────────────────────────
    // UI BUILD
    // ─────────────────────────────────────────
    private fun buildUI() {
        // Root
        rootLayout = FrameLayout(this)
        rootLayout.setBackgroundColor(Color.WHITE)

        // Main vertical container
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Top bar
        val topBar = buildTopBar()

        // Progress bar
        progressBar = ProgressBar(this, null,
            android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 6)
            max = 100
            progress = 0
            progressDrawable = ContextCompat.getDrawable(
                this@MainActivity,
                android.R.drawable.progress_horizontal
            )
            visibility = View.GONE
        }

        // WebView
        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        mainLayout.addView(topBar)
        mainLayout.addView(progressBar)
        mainLayout.addView(webView)

        // Error overlay
        errorLayout = buildErrorLayout()

        rootLayout.addView(mainLayout)
        rootLayout.addView(errorLayout)

        setContentView(rootLayout)
    }

    private fun buildTopBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#4285F4"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(56)
            )
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dpToPx(4), 0, dpToPx(8), 0)
        }

        // Back button
        val backBtn = buildIconButton("←") { onBackPressedCustom() }

        // Title area
        val titleLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setPadding(dpToPx(8), 0, 0, 0)
        }
        val title = TextView(this).apply {
            text = "Create Gmail Account"
            setTextColor(Color.WHITE)
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        titleLayout.addView(title)

        // Reload button
        val reloadBtn = buildIconButton("↻") {
            if (isNetworkAvailable()) {
                webView.reload()
            } else {
                showError()
            }
        }

        bar.addView(backBtn)
        bar.addView(titleLayout)
        bar.addView(reloadBtn)

        return bar
    }

    private fun buildIconButton(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(56))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                foreground = with(android.util.TypedValue()) {
                    setTo(android.util.TypedValue())
                    context.obtainStyledAttributes(
                        intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
                    ).getDrawable(0)
                }
            }
        }
    }

    private fun buildErrorLayout(): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(dpToPx(40), 0, dpToPx(40), 0)
            visibility = View.GONE
        }

        val icon = TextView(this).apply {
            text = "📵"
            textSize = 56f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dpToPx(20) }
        }

        val title = TextView(this).apply {
            text = "No Internet Connection"
            textSize = 21f
            setTextColor(Color.parseColor("#202124"))
            gravity = android.view.Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dpToPx(10) }
        }

        val desc = TextView(this).apply {
            text = "Please check your network connection and try again."
            textSize = 14f
            setTextColor(Color.parseColor("#5F6368"))
            gravity = android.view.Gravity.CENTER
            lineHeight = dpToPx(22)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dpToPx(32) }
        }

        val retryBtn = TextView(this).apply {
            text = "Try Again"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#4285F4"))
            setPadding(dpToPx(32), dpToPx(14), dpToPx(32), dpToPx(14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also {
                it.gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (isNetworkAvailable()) {
                    hideError()
                    webView.loadUrl(GMAIL_URL)
                } else {
                    Toast.makeText(this@MainActivity,
                        "Still no internet. Please check your connection.",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }

        layout.addView(icon)
        layout.addView(title)
        layout.addView(desc)
        layout.addView(retryBtn)

        return layout
    }

    // ─────────────────────────────────────────
    // WEBVIEW SETUP
    // ─────────────────────────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            // Real Chrome Mobile user-agent — critical for Google to accept the request
            userAgentString =
                "Mozilla/5.0 (Linux; Android 10; K) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
        }

        // Accept all cookies
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
                progressBar.progress = 10
                hideError()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progressBar.progress = 100
                progressBar.visibility = View.GONE
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    progressBar.visibility = View.GONE
                    showError()
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                return if (url.startsWith("http") || url.startsWith("https")) {
                    false // WebView handles it
                } else {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        true
                    } catch (e: ActivityNotFoundException) {
                        false
                    }
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress == 100) progressBar.visibility = View.GONE
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback
                showFileChooser()
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                callback.invoke(origin, true, false)
            }
        }
    }

    // ─────────────────────────────────────────
    // FILE / CAMERA
    // ─────────────────────────────────────────
    private fun showFileChooser() {
        AlertDialog.Builder(this)
            .setTitle("Select Attachment")
            .setItems(arrayOf("Take Photo", "Choose from Files")) { _, which ->
                when (which) {
                    0 -> checkCamera()
                    1 -> checkStorage()
                }
            }
            .setOnCancelListener {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = null
            }
            .show()
    }

    private fun checkCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) launchCamera()
        else cameraPermLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun checkStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launchFilePicker()
        } else {
            val perms = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            if (perms.all {
                ContextCompat.checkSelfPermission(this, it) ==
                    PackageManager.PERMISSION_GRANTED }) {
                launchFilePicker()
            } else {
                storagePermLauncher.launch(perms)
            }
        }
    }

    private fun launchCamera() {
        val file = createImageFile() ?: run {
            Toast.makeText(this, "Cannot create image file", Toast.LENGTH_SHORT).show()
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = null
            return
        }
        cameraImageUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        cameraLauncher.launch(cameraImageUri!!)
    }

    private fun launchFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        filePickerLauncher.launch(Intent.createChooser(intent, "Select File"))
    }

    private fun createImageFile(): File? {
        return try {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: cacheDir
            File.createTempFile("MAIL_IMG_${ts}_", ".jpg", dir)
        } catch (e: Exception) { null }
    }

    // ─────────────────────────────────────────
    // ERROR SCREEN
    // ─────────────────────────────────────────
    private fun showError() { errorLayout.visibility = View.VISIBLE }
    private fun hideError() { errorLayout.visibility = View.GONE }

    // ─────────────────────────────────────────
    // BACK PRESS
    // ─────────────────────────────────────────
    private fun onBackPressedCustom() {
        if (webView.canGoBack()) webView.goBack()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    // ─────────────────────────────────────────
    // UTILITIES
    // ─────────────────────────────────────────
    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val cap = cm.getNetworkCapabilities(net) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun setupStatusBar() {
        window.statusBarColor = Color.parseColor("#4285F4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = 0
        }
    }
}
