package com.dopavin

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.dopavin.data.AccountStore
import com.dopavin.data.YouTubeAccount

/**
 * F-221: Google's own sign-in page in a WebView. Dopavin never sees the password; when the page lands on
 * YouTube Music and a session cookie exists, that cookie is kept ([AccountStore.youTubeCookie]) and the screen
 * closes. Unverified against the live page: Google may refuse embedded sign-in on some devices or at some times.
 */
class YouTubeSignInActivity : Activity() {

    private lateinit var web: WebView

    @Suppress("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply { fitsSystemWindows = true }
        web = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Google refuses sign-in in a WebView that says so in its user agent ("; wv").
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
        }
        root.addView(web)
        setContentView(root)
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (url == null || !url.startsWith(YouTubeAccount.ORIGIN)) return
                val cookie = CookieManager.getInstance().getCookie(YouTubeAccount.ORIGIN).orEmpty()
                if (YouTubeAccount.isSignedIn(cookie)) {
                    AccountStore(this@YouTubeSignInActivity).youTubeCookie = cookie
                    CookieManager.getInstance().flush()
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
        web.loadUrl(YouTubeAccount.SIGN_IN_URL)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        // The WebView must not outlive the activity: it holds the page and its script engine.
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }

    companion object {
        /** Forgets the web session too, so the next sign-in starts clean. */
        fun clearWebSession() {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }
}
