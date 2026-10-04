package com.demo.faircrash

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true   // needed for localStorage (balance/history)
        webView.webViewClient = WebViewClient()      // keep navigation inside the app
        webView.loadUrl("file:///android_asset/index.html")

        setContentView(webView)
    }

    override fun onBackPressed() {
        // optional: let back button close app instead of nothing happening
        super.onBackPressed()
    }
}