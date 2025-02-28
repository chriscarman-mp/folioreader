package com.folioreader.ui.view

import android.content.Context
import android.os.Handler
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.webkit.JavascriptInterface
import androidx.core.view.GestureDetectorCompat
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.folioreader.R

class WebViewPager : ViewPager {

    companion object {
        @JvmField
        val LOG_TAG: String = WebViewPager::class.java.simpleName
    }

    // Reference to the outer (chapter) pager
    var outerPager: DirectionalViewpager? = null

    internal var horizontalPageCount: Int = 0
    private var folioWebView: FolioWebView? = null
    var isScrolling: Boolean = false
        private set
    private var uiHandler: Handler? = null
    private var gestureDetector: GestureDetectorCompat? = null
    private var lastGestureType: LastGestureType? = null
    private var xDown = 0f

    private enum class LastGestureType {
        OnSingleTapUp, OnLongPress, OnFling, OnScroll
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    private fun init() {
        uiHandler = Handler()
        gestureDetector = GestureDetectorCompat(context, GestureListener())

        // Listen for page scroll events
        addOnPageChangeListener(object : OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                isScrolling = true
                folioWebView?.let { webView ->
                    val precisePosition = position + positionOffset
                    val pageWidth = webView.getScrollXPixelsForPage(1) ?: 0
                    val scrollX = (precisePosition * pageWidth).toInt()
                    webView.scrollTo(scrollX, 0)

                    Log.d(LOG_TAG, "[WebViewPager][onPageScrolled] position: $position")
                    Log.d(LOG_TAG, "[WebViewPager][onPageScrolled] positionOffset: $positionOffset")
                    Log.d(LOG_TAG, "[WebViewPager][onPageScrolled] precisePosition: $precisePosition")
                    Log.d(LOG_TAG, "[WebViewPager][onPageScrolled] pageWidth: $pageWidth")
                    Log.d(LOG_TAG, "[WebViewPager][onPageScrolled] scrollX: $scrollX")
                }
                if (positionOffsetPixels == 0) {
                    isScrolling = false
                }
            }

            override fun onPageSelected(position: Int) {
                Log.v(LOG_TAG, "-> onPageSelected -> $position")
            }

            override fun onPageScrollStateChanged(state: Int) {
                // No extra handling needed here.
            }
        })
    }

    fun setHorizontalPageCount(horizontalPageCount: Int) {
        this.horizontalPageCount = horizontalPageCount
        if (adapter == null) {
            adapter = WebViewPagerAdapter()
        } else {
            (adapter as PagerAdapter).notifyDataSetChanged()
        }

        if (folioWebView == null) {
            folioWebView = (parent as View).findViewById(R.id.folioWebView)
        }
    }

    @JavascriptInterface
    fun scrollToPrecisePosition(position: Double) {
        folioWebView?.let { webView ->
            // This is the logic you mentioned
            // Convert the floating position to an int offset
            val pageWidth = webView.getScrollXPixelsForPage(1) // or some logic
            val scrollX = (position * pageWidth).toInt()

            webView.scrollTo(scrollX, 0)

            Log.d(LOG_TAG, "[WebViewPager][scrollToPrecisePosition] position: $position")
            Log.d(LOG_TAG, "[WebViewPager][scrollToPrecisePosition] pageWidth: $pageWidth")
            Log.d(LOG_TAG, "[WebViewPager][scrollToPrecisePosition] scrollX: $scrollX")
        }
    }

    @JavascriptInterface
    fun setCurrentPage(pageIndex: Int) {
        Log.v(LOG_TAG, "-> setCurrentPage -> pageIndex = $pageIndex")
        uiHandler?.post { setCurrentItem(pageIndex, false) }
    }

    @JavascriptInterface
    fun setPageToLast() {
        uiHandler?.post { currentItem = horizontalPageCount - 1 }
    }

    @JavascriptInterface
    fun setPageToFirst() {
        uiHandler?.post { currentItem = 0 }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            // Always return true so subsequent events are received.
            super@WebViewPager.onTouchEvent(e)
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            lastGestureType = LastGestureType.OnSingleTapUp
            return false
        }

        override fun onLongPress(e: MotionEvent) {
            super.onLongPress(e)
            lastGestureType = LastGestureType.OnLongPress
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            lastGestureType = LastGestureType.OnScroll
            return false
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            Log.d(LOG_TAG, ">>> onFling")
            Log.d(LOG_TAG, ">>> onFling currentItem: $currentItem")
            Log.d(LOG_TAG, ">>> onFling horizontalPageCount: $horizontalPageCount")
            val MIN_FLING_VELOCITY = 1000

            // Edge fling: if on the last page and swiping left
            if (currentItem == horizontalPageCount - 1 && velocityX < -MIN_FLING_VELOCITY) {
                outerPager?.let { pager ->
                    pager.setCurrentItem(pager.currentItem + 1, true)
                    Log.v(LOG_TAG, ">>> onFling at last page: switching to next chapter")
                    return true
                } ?: Log.v(LOG_TAG, ">>> onFling - outerPager is null!")
            }
            // Edge fling: if on the first page and swiping right
            if (currentItem == 0 && velocityX > MIN_FLING_VELOCITY) {
                outerPager?.let { pager ->
                    pager.setCurrentItem(pager.currentItem - 1, true)
                    Log.v(LOG_TAG, ">>> onFling at first page: switching to previous chapter")
                    return true
                } ?: Log.v(LOG_TAG, ">>> onFling - outerPager is null!")
            }
            return false
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event ?: return false

        // Let the gesture detector handle the event.
        gestureDetector?.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                xDown = event.x
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - xDown
                // If on the very first or last page, allow the parent to intercept swipe events.
                if (currentItem == horizontalPageCount - 1 && dx < 0) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                } else if (currentItem == 0 && dx > 0) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                } else {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
        }
        // Let the default handling continue.
        return super.onTouchEvent(event)
    }

    private inner class WebViewPagerAdapter : PagerAdapter() {
        override fun getCount(): Int = horizontalPageCount

        override fun isViewFromObject(view: View, `object`: Any): Boolean = view === `object`

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val view = LayoutInflater.from(container.context)
                .inflate(R.layout.view_webview_pager, container, false)
            container.addView(view)
            return view
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            container.removeView(`object` as View)
        }
    }
}