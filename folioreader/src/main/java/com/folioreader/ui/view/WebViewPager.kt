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

    var outerPager: DirectionalViewpager? = null

    internal var horizontalPageCount: Int = 0
    private var folioWebView: FolioWebView? = null
    private var takeOverScrolling: Boolean = false
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

        addOnPageChangeListener(object : OnPageChangeListener {
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
                isScrolling = true

                if (takeOverScrolling && folioWebView != null) {
                    val scrollX = folioWebView!!.getScrollXPixelsForPage(position) + positionOffsetPixels
                    folioWebView!!.scrollTo(scrollX, 0)
                }

                if (positionOffsetPixels == 0) {
                    takeOverScrolling = false
                    isScrolling = false
                }
            }

            override fun onPageSelected(position: Int) {
                Log.v(LOG_TAG, "-> onPageSelected -> $position")
            }

            override fun onPageScrollStateChanged(state: Int) {
                // No additional handling
            }
        })
    }

    fun setHorizontalPageCount(horizontalPageCount: Int) {
        this.horizontalPageCount = horizontalPageCount
        adapter = WebViewPagerAdapter()
        currentItem = 0

        if (folioWebView == null)
            folioWebView = (parent as View).findViewById(R.id.folioWebView)
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

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            lastGestureType = LastGestureType.OnScroll
            return false
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            Log.d(LOG_TAG, ">>> onFling")
            Log.d(LOG_TAG, ">>> onFling currentItem: $currentItem")
            Log.d(LOG_TAG, ">>> onFling horizontalPageCount: $horizontalPageCount")
            val MIN_FLING_VELOCITY = 1000 // adjust threshold as needed

            // If on the last page and fling left (velocityX negative)
            if (currentItem == horizontalPageCount - 1 && velocityX < -MIN_FLING_VELOCITY) {
                outerPager?.let { pager ->
                    pager.setCurrentItem(pager.currentItem + 1, true)
                    Log.v(LOG_TAG, ">>> onFling at last page: switching to next chapter")
                    return true
                } ?: Log.v(LOG_TAG, ">>> onFling - outerPager is null!")
            }
            // If on the first page and fling right (velocityX positive)
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

        // Let gestureDetector handle the event
        gestureDetector?.onTouchEvent(event)

        // Capture initial x coordinate on ACTION_DOWN
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                xDown = event.x
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - xDown
                if (currentItem == horizontalPageCount - 1 && dx < 0) {
                    // At last page and swiping left: allow parent to intercept
                    parent?.requestDisallowInterceptTouchEvent(false)
                } else if (currentItem == 0 && dx > 0) {
                    // At first page and swiping right: allow parent to intercept
                    parent?.requestDisallowInterceptTouchEvent(false)
                } else {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
        }

        val superReturn = super.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP) {
            takeOverScrolling = true
        }
        return superReturn
    }

    private inner class WebViewPagerAdapter : PagerAdapter() {
        override fun getCount(): Int {
            return horizontalPageCount
        }

        override fun isViewFromObject(view: View, `object`: Any): Boolean {
            return view === `object`
        }

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
