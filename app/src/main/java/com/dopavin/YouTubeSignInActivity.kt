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
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }
    companion object {
        fun clearWebSession() {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }
}
