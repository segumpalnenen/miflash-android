package id.miflash.android.miunlock

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.core.view.WindowCompat
import id.miflash.android.R
import id.miflash.android.miunlock.auth.MiAccountCredentials
import id.miflash.android.ui.settings.LocaleHelper
import id.miflash.android.ui.settings.SettingsStore
import id.miflash.android.ui.settings.readSavedLanguageCode
import id.miflash.android.ui.theme.ThemeMode

class MiLoginActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PASS_TOKEN = "passToken"
        const val EXTRA_DEVICE_ID = "deviceId"
        const val EXTRA_USER_ID = "userId"
    }

    private val initialUrl = "https://account.xiaomi.com/pass/serviceLogin?sid=unlockApi&checkSafeAddress=true"
    private val endPattern = "{\"R\":\"\",\"S\":\"OK\"}"
    private var monitoringEnded = false
    private var passToken: String? = null
    private var deviceId: String? = null
    private var userId: String? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, readSavedLanguageCode(newBase)))
    }

    private fun isDarkTheme(): Boolean {
        return when (SettingsStore(this).settings.value.themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.miunlock_login)

        val dark = isDarkTheme()
        val root = findViewById<View>(android.R.id.content)
        val backgroundColor = getColor(if (dark) R.color.mi_dark_background else R.color.mi_light_background)
        root.setBackgroundColor(backgroundColor)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !dark

        val webView = findViewById<WebView>(R.id.webView)
        val continueButton = findViewById<Button>(R.id.continueButton)
        val logoutButton = findViewById<Button>(R.id.logoutButton)
        val loginMessage = findViewById<TextView>(R.id.loginMessage)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        webView.setBackgroundColor(backgroundColor)
        loginMessage.setTextColor(getColor(if (dark) R.color.mi_dark_on_surface else R.color.mi_light_on_surface))

        onBackPressedDispatcher.addCallback(this) {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        if (checkExistingCookies()) {
            webView.visibility = View.GONE
            progressBar.visibility = View.GONE
            continueButton.visibility = View.VISIBLE
            logoutButton.visibility = View.VISIBLE
            loginMessage.visibility = View.VISIBLE
            loginMessage.text = getString(R.string.login_already, userId)
        } else {
            webView.visibility = View.VISIBLE
            progressBar.visibility = View.VISIBLE
            continueButton.visibility = View.GONE
            logoutButton.visibility = View.GONE
            loginMessage.visibility = View.GONE
            setupWebView(webView, progressBar)
        }

        continueButton.setOnClickListener {
            returnResults()
        }

        logoutButton.setOnClickListener {
            CookieManager.getInstance().removeAllCookies {
                Toast.makeText(this, getString(R.string.logout_success), Toast.LENGTH_SHORT).show()
                passToken = null
                deviceId = null
                userId = null
                monitoringEnded = false
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            CookieManager.getInstance().flush()
        }
    }

    private fun checkExistingCookies(): Boolean {
        val cookieString = CookieManager.getInstance().getCookie("https://account.xiaomi.com")
        if (cookieString != null) {
            fun extractValue(key: String) = cookieString.split(";").map { it.trim() }
                .find { it.startsWith("$key=") }?.split("=")?.getOrNull(1)

            passToken = extractValue("passToken")
            deviceId = extractValue("deviceId")
            userId = extractValue("userId")

            return userId?.isNotEmpty() == true &&
                passToken?.isNotEmpty() == true &&
                deviceId?.isNotEmpty() == true
        }
        return false
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(webView: WebView, progressBar: ProgressBar) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "(Android) Mobile"
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!monitoringEnded) {
                    extractCookies()
                }
                return false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                progressBar.visibility = View.VISIBLE
                handler.removeCallbacksAndMessages(null)
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!monitoringEnded) {
                    progressBar.visibility = View.GONE
                    view.evaluateJavascript(
                        "var style = document.createElement('style'); style.innerHTML = '* { transition: none !important; animation: none !important; }'; document.head.appendChild(style);",
                        null
                    )
                    extractCookies()
                    checkForEndSignal(view)
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) {
                    return
                }
                progressBar.visibility = View.GONE
                monitoringEnded = true
                Toast.makeText(this@MiLoginActivity, getString(R.string.login_failed, error.description, error.errorCode), Toast.LENGTH_LONG).show()
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }

        CookieManager.getInstance().run {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.loadUrl(initialUrl)
        handler.postDelayed({
            if (progressBar.visibility == View.VISIBLE) {
                progressBar.visibility = View.GONE
                Toast.makeText(this, getString(R.string.login_slow), Toast.LENGTH_SHORT).show()
            }
        }, 5000)
    }

    private fun extractCookies() {
        CookieManager.getInstance().getCookie("https://account.xiaomi.com")?.let { cookieString ->
            fun extractValue(key: String) = cookieString.split(";").map { it.trim() }
                .find { it.startsWith("$key=") }?.split("=")?.getOrNull(1)

            passToken = passToken ?: extractValue("passToken")
            deviceId = deviceId ?: extractValue("deviceId")
            userId = userId ?: extractValue("userId")
        }
    }

    private fun checkForEndSignal(view: WebView) {
        view.evaluateJavascript("document.documentElement.outerHTML") { html ->
            val cleanedHtml = html.replace("\\u003C", "<")
                .replace("\\u003E", ">")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")

            if (cleanedHtml.contains(endPattern)) {
                if (passToken?.isNotEmpty() != true || deviceId?.isNotEmpty() != true || userId?.isNotEmpty() != true) {
                    view.loadUrl(initialUrl)
                } else {
                    monitoringEnded = true
                    val loginMessage = findViewById<TextView>(R.id.loginMessage)
                    loginMessage.text = getString(R.string.login_success, userId)
                    loginMessage.visibility = View.VISIBLE
                    handler.postDelayed({
                        returnResults()
                    }, 2000)
                }
            }
        }
    }

    private fun returnResults() {
        val hasAllValues = passToken?.isNotEmpty() == true && deviceId?.isNotEmpty() == true && userId?.isNotEmpty() == true

        if (hasAllValues) {
            setResult(Activity.RESULT_OK, Intent().apply {
                putExtra(EXTRA_PASS_TOKEN, passToken)
                putExtra(EXTRA_DEVICE_ID, deviceId)
                putExtra(EXTRA_USER_ID, userId)
            })
            finish()
        } else {
            findViewById<WebView>(R.id.webView).loadUrl(initialUrl)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
