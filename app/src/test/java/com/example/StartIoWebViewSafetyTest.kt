package com.example

import android.content.Context
import android.os.Looper
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.disposeStartIoBannerContainer
import com.example.ui.components.wrapDescendantWebViews
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StartIoWebViewSafetyTest {

    @Test
    fun wrappedWebViewClientHandlesRenderProcessGoneWithoutCrashingApp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = FrameLayout(context)
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean = false
        }
        container.addView(webView)

        var recovered = false
        wrapDescendantWebViews(container) {
            recovered = true
        }

        val handled = webView.webViewClient.onRenderProcessGone(webView, null)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertTrue(handled)
        assertTrue(recovered)
        assertEquals(0, container.childCount)
    }

    @Test
    fun disposeStartIoBannerContainerRemovesAllChildViews() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = FrameLayout(context)
        container.addView(WebView(context))
        container.addView(FrameLayout(context).apply { addView(WebView(context)) })

        disposeStartIoBannerContainer(container)

        assertEquals(0, container.childCount)
    }
}
