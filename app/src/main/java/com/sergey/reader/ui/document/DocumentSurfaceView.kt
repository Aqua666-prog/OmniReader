package com.sergey.reader.ui.document

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.OverScroller
import com.sergey.reader.document.*
import kotlinx.coroutines.*
import kotlin.math.*

/** A single viewport. There are no page Views, cards, giant bitmaps or EPUB paragraphs. */
class DocumentSurfaceView(context: Context, private val session: DocumentSession) : View(context) {
    var onPosition: (DocumentAnchor) -> Unit = {}
    var onSingleTap: () -> Unit = {}
    var onSelection: (DocumentSelection?) -> Unit = {}
    var onMessage: (String) -> Unit = {}
    var onBusy: (Boolean) -> Unit = {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val renderer = DocumentTileRenderer(session,{ invalidate() },{ onMessage(it) })
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val overlay = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scroller = OverScroller(context)
    private var zoomAnimation: ValueAnimator? = null
    private var layout: DocumentLayout? = null
    private var transform = DocumentTransform()
    private var options = DocumentOptions()
    private var activePage = 0
    private var pendingAnchor: DocumentAnchor? = null
    private var pendingOpening = true
    private var selection: DocumentSelection? = null
    private var searchHit: DocumentSearchHit? = null
    private var marks: Map<Int,List<Pair<DRect,Int>>> = emptyMap()
    private var handle = 0
    private var multiTouch = false
    private var startedZoomed = false
    private var previousFocus = DPoint(0.0,0.0)
    private var selectionJob: Job? = null
    private var selectionGeneration = 0
    private var dragX = 0f
    private var dragY = 0f
    private var disposed = false
    private val density = resources.displayMetrics.density
    private val settle = Runnable { requestTiles(true) }
    private val positionReport = Runnable { anchor()?.let(onPosition) }
    private val edgeScroll = object : Runnable {
        override fun run() {
            if (handle == 0 || disposed) return
            val edge = 48 * density
            val delta = when {
                dragY < edge -> -18 * density
                dragY > height - edge -> 18 * density
                else -> 0f
            }
            if (delta != 0f) layout?.let { currentLayout ->
                transform = transform.copy(scrollY = transform.scrollY + delta).constrained(currentLayout.size, viewport)
                changed()
                dragHandle(dragX, dragY)
            }
            postDelayed(this, 32)
        }
    }
    private val viewport get() = DSize(width.coerceAtLeast(1).toDouble(),height.coerceAtLeast(1).toDouble())
    private val minimumZoom get() = if (options.openingScale == DocumentOpeningScale.PAGE) min(1.0,viewport.height / (layout?.pages?.firstOrNull()?.bounds?.height ?: viewport.height)) else 1.0

    init { isFocusable = true; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES; contentDescription = "Документ" }

    fun configure(value: DocumentOptions) {
        if (value == options) return
        val saved = anchor()
        val modeChanged = value.mode != options.mode
        options = value
        if (modeChanged && width > 0) { pendingAnchor = saved; pendingOpening = false; rebuild() }
        else {
            transform = transform.copy(zoom = transform.zoom.coerceIn(minimumZoom,options.maxZoom)).let { t -> layout?.let { t.constrained(it.size,viewport) } ?: t }
            changed()
        }
    }
    fun restore(value: DocumentAnchor) { pendingAnchor = value; pendingOpening = true; if (width > 0) rebuild() }
    fun jump(page: Int, region: DRect? = null) {
        clearSelection()
        activePage = page.coerceIn(session.sizes.indices)
        val size = session.sizes[activePage]
        val point = region?.center ?: DPoint(size.width/2,0.0)
        pendingAnchor = DocumentAnchor(activePage,point.x/size.width,point.y/size.height,transform.zoom)
        pendingOpening = false
        rebuild()
    }
    fun highlight(hit: DocumentSearchHit) { searchHit = hit; jump(hit.page,hit.bounds.reduceOrNull(DRect::union)); invalidate() }
    fun setMarks(value: Map<Int,List<Pair<DRect,Int>>>) { marks = value; invalidate() }
    private fun cancelSelectionWork() {
        selectionGeneration++
        selectionJob?.cancel()
        selectionJob = null
        onBusy(false)
    }
    fun clearSelection() {
        cancelSelectionWork()
        removeCallbacks(edgeScroll)
        selection = null
        handle = 0
        onSelection(null)
        invalidate()
    }
    fun selectAllOnPage() {
        selection?.page?.let { page ->
            cancelSelectionWork()
            selection = DocumentSelection(page,0,page.text.length)
            onSelection(selection)
            invalidate()
        }
    }
    fun next(delta: Int) {
        val step = if (options.mode == DocumentMode.SPREAD) 2 else 1
        jump((activePage + delta*step).coerceIn(session.sizes.indices))
    }
    fun resetZoom() { animateZoom(DPoint(width/2.0,height/2.0),1.0) }

    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        if (w <= 0 || h <= 0) return
        if (layout != null && pendingAnchor == null && oldw > 0 && oldh > 0) {
            pendingAnchor = anchor(DPoint(oldw/2.0,oldh/2.0)); pendingOpening = false
        }
        rebuild()
    }
    private fun rebuild() {
        if (width <= 0 || height <= 0 || disposed) return
        scroller.forceFinished(true)
        val saved = DocumentTransform.sanitize(pendingAnchor ?: DocumentAnchor(activePage),session.sizes.size,options.maxZoom)
        activePage = saved.page
        val next = DocumentLayout.create(session.sizes,width.toDouble(),4.0*density,options.mode,activePage)
        layout = next
        val p = next.pages.first { it.index == activePage }
        val requestedZoom = if (!pendingOpening) saved.zoom else when (options.openingScale) {
            DocumentOpeningScale.WIDTH -> 1.0
            DocumentOpeningScale.PAGE -> min(1.0,viewport.height/p.bounds.height)
            DocumentOpeningScale.LAST -> if (options.saveZoom) saved.zoom else 1.0
        }
        val z = requestedZoom.coerceIn(minimumZoom,options.maxZoom)
        val target = p.toLayout(DPoint(saved.x*p.pageSize.width,saved.y*p.pageSize.height))
        transform = DocumentTransform(z,target.x*z-width/2,target.y*z-height/2).constrained(next.size,viewport)
        pendingAnchor = null; pendingOpening = false
        changed()
    }
    fun anchor(screen: DPoint = DPoint(width/2.0,height/2.0)): DocumentAnchor? {
        val l = layout ?: return null
        val p = l.nearest(transform.toDocument(screen))
        val local = p.toPage(transform.toDocument(screen))
        return DocumentAnchor(p.index,(local.x/p.pageSize.width).coerceIn(0.0,1.0),(local.y/p.pageSize.height).coerceIn(0.0,1.0),transform.zoom)
    }
    private fun visiblePages(): List<PagePlacement> {
        val l = layout ?: return emptyList()
        val a = transform.toDocument(DPoint(0.0,0.0)); val b = transform.toDocument(DPoint(width.toDouble(),height.toDouble()))
        val clip = DRect(a.x,a.y,b.x,b.y)
        return l.pages.filter { it.bounds.intersects(clip) }
    }
    private fun requestTiles(quality: Boolean) {
        if (disposed) return
        val pages = visiblePages()
        val previews = pages.map(DocumentTiles::preview)
        val tiles = if (quality && options.highQuality) pages.flatMap { DocumentTiles.visible(it,transform,viewport) } else emptyList()
        val adjacent = if(quality && pages.isNotEmpty()) layout?.pages.orEmpty().filter {
            it.index == pages.first().index-1 || it.index == pages.last().index+1
        }.map(DocumentTiles::preview) else emptyList()
        renderer.request(previews + tiles + adjacent)
    }
    private fun changed() {
        anchor()?.let { activePage = it.page }
        invalidate()
        removeCallbacks(settle); removeCallbacks(positionReport)
        renderer.cancelPending()
        requestTiles(false)
        postDelayed(settle,160)
        postDelayed(positionReport,250)
    }
    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.rgb(45,47,49))
        for (p in visiblePages()) {
            val screen = transform.rectToScreen(p.bounds)
            paint.color = Color.WHITE
            canvas.drawRect(screen.rectF(),paint)
            // Low resolution fallback survives a gesture. Exact-scale tiles replace it in place.
            val cached = renderer.cachedFor(p.index)
            val preview = DocumentTiles.preview(p)
            cached[preview]?.let { canvas.drawBitmap(it,null,screen.rectF(),paint) }
            cached.entries.filter { !it.key.preview }.sortedBy { it.key.fullWidth }.forEach { (key,bitmap) ->
                val n = key.normalizedBounds
                val dst = DRect(screen.left+n.left*screen.width,screen.top+n.top*screen.height,screen.left+n.right*screen.width,screen.top+n.bottom*screen.height)
                if (dst.intersects(DRect(0.0,0.0,width.toDouble(),height.toDouble()))) canvas.drawBitmap(bitmap,null,dst.rectF(),paint)
            }
            marks[p.index].orEmpty().forEach { (rect,color) -> drawOverlay(canvas,p,rect,color) }
            searchHit?.takeIf { it.page == p.index }?.bounds?.forEach { drawOverlay(canvas,p,it,0x88FFB300.toInt()) }
            selection?.parts?.firstOrNull { it.page.pageIndex == p.index }?.let { selected ->
                selected.bounds.forEach { drawOverlay(canvas,p,it,0x663399FF) }
            }
        }
        handles().forEach { point ->
            if (point.x > -1e8 && point.y > -1e8) {
                overlay.color = 0xFF1565C0.toInt()
                canvas.drawCircle(point.x.toFloat(),point.y.toFloat(),9*density,overlay)
            }
        }
    }
    private fun drawOverlay(canvas: Canvas,page: PagePlacement,r: DRect,color: Int) {
        val a = transform.toScreen(page.toLayout(DPoint(r.left,r.top))); val b = transform.toScreen(page.toLayout(DPoint(r.right,r.bottom)))
        overlay.color = color; canvas.drawRect(a.x.toFloat(),a.y.toFloat(),b.x.toFloat(),b.y.toFloat(),overlay)
    }
    private fun handles(): List<DPoint> {
        val parts = selection?.parts?.filter { it.bounds.isNotEmpty() } ?: return emptyList()
        if (parts.isEmpty()) return emptyList()
        return listOf(parts.first() to parts.first().bounds.first(), parts.last() to parts.last().bounds.last()).map { (part, box) ->
            val placement = layout?.pages?.firstOrNull { it.index == part.page.pageIndex }
            if (placement == null) DPoint(-1e9,-1e9)
            else transform.toScreen(placement.toLayout(DPoint(box.center.x,box.bottom)))
        }
    }

    private fun selectAt(x: Float,y: Float) {
        val placement = layout?.at(transform.toDocument(DPoint(x.toDouble(),y.toDouble()))) ?: return
        val point = placement.toPage(transform.toDocument(DPoint(x.toDouble(),y.toDouble())))
        cancelSelectionWork()
        val token = ++selectionGeneration
        selectionJob = scope.launch {
            onBusy(true)
            try {
                val text = session.text(placement.index)
                if (text.text.isBlank()) { onMessage("На этой странице не удалось найти или распознать текст"); return@launch }
                val word = text.wordAt(point) ?: return@launch
                if (token != selectionGeneration) return@launch
                selection = DocumentSelection(text,word.start,word.end)
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onSelection(selection)
                invalidate()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: LinkageError) {
                onMessage(DocumentErrors.message(error))
            } catch (error: OutOfMemoryError) {
                onMessage(DocumentErrors.message(error))
            } catch (error: Exception) {
                onMessage(DocumentErrors.message(error))
            } finally {
                if (token == selectionGeneration) {
                    selectionJob = null
                    onBusy(false)
                }
            }
        }
    }

    private fun dragHandle(x: Float,y: Float) {
        dragX=x; dragY=y
        if (selectionJob?.isActive == true) return
        val selected = selection ?: return
        val placement = layout?.nearest(transform.toDocument(DPoint(x.toDouble(),y.toDouble()))) ?: return
        val point = placement.toPage(transform.toDocument(DPoint(x.toDouble(),y.toDouble())))
        val dragging = handle
        if (dragging == 0) return
        val token = ++selectionGeneration
        selectionJob = scope.launch {
            onBusy(true)
            try {
                val page = session.text(placement.index)
                val word = page.nearestWord(point) ?: return@launch
                val start = DocumentEndpoint(selected.page.pageIndex,selected.start)
                val last = selected.parts.last()
                val end = DocumentEndpoint(last.page.pageIndex,last.end)
                val moving = DocumentEndpoint(placement.index,if (dragging == 1) word.start else word.end)
                if ((dragging == 1 && moving >= end) || (dragging == 2 && moving <= start)) return@launch
                val next = DocumentSelectionRange.load(
                    if (dragging == 1) moving else start,
                    if (dragging == 2) moving else end,
                    session::text,
                )
                if (token != selectionGeneration) return@launch
                selection = next
                onSelection(selection)
                invalidate()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: LinkageError) {
                onMessage(DocumentErrors.message(error))
            } catch (error: OutOfMemoryError) {
                onMessage(DocumentErrors.message(error))
            } catch (error: Exception) {
                onMessage(DocumentErrors.message(error))
            } finally {
                if (token == selectionGeneration) {
                    selectionJob = null
                    onBusy(false)
                }
            }
        }
    }

    private val scaleDetector = ScaleGestureDetector(context,object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean { multiTouch = true; previousFocus = DPoint(detector.focusX.toDouble(),detector.focusY.toDouble()); cancelSelectionWork(); scroller.forceFinished(true); zoomAnimation?.cancel(); return true }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val l = layout ?: return false
            val focus = DPoint(detector.focusX.toDouble(),detector.focusY.toDouble())
            val zoomed = transform.zoomAt(previousFocus,transform.zoom*detector.scaleFactor,minimumZoom,options.maxZoom)
            transform = zoomed.copy(scrollX=zoomed.scrollX-(focus.x-previousFocus.x),scrollY=zoomed.scrollY-(focus.y-previousFocus.y)).constrained(l.size,viewport)
            previousFocus = focus
            changed(); return true
        }
    }).apply { isQuickScaleEnabled = false }
    private val gestureDetector = GestureDetector(context,object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: android.view.MotionEvent): Boolean { scroller.forceFinished(true); zoomAnimation?.cancel(); startedZoomed = transform.zoom > 1.01; return true }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (!multiTouch && selection == null && options.tapControls && e.x > width*.2 && e.x < width*.8) performClick()
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (options.doubleTapZoom && selection == null) animateZoom(DPoint(e.x.toDouble(),e.y.toDouble()),if (transform.zoom > 1.05) 1.0 else min(3.0,options.maxZoom))
            return true
        }
        override fun onLongPress(e: MotionEvent) { if (!multiTouch && !scaleDetector.isInProgress) selectAt(e.x,e.y) }
        override fun onScroll(e1: MotionEvent?,e2: MotionEvent,dx: Float,dy: Float): Boolean {
            if (scaleDetector.isInProgress || handle != 0) return true
            val l = layout ?: return false
            cancelSelectionWork()
            transform = transform.copy(scrollX = transform.scrollX+dx,scrollY = transform.scrollY+dy).constrained(l.size,viewport)
            changed(); return true
        }
        override fun onFling(e1: MotionEvent?,e2: MotionEvent,vx: Float,vy: Float): Boolean {
            if (multiTouch || handle != 0 || selection != null) return true
            if (!startedZoomed && transform.zoom <= 1.01 && options.mode != DocumentMode.CONTINUOUS && abs(vx) > abs(vy)*1.5 && abs(vx)>600*density) {
                next(if (vx<0) 1 else -1); return true
            }
            val l = layout ?: return false
            scroller.fling(transform.scrollX.toInt(),transform.scrollY.toInt(),-vx.toInt(),-vy.toInt(),
                min(0,(l.size.width*transform.zoom-width).toInt()/2),max(0,(l.size.width*transform.zoom-width).toInt()),
                min(0,(l.size.height*transform.zoom-height).toInt()/2),max(0,(l.size.height*transform.zoom-height).toInt()))
            postInvalidateOnAnimation(); return true
        }
    })
    override fun performClick(): Boolean { super.performClick(); onSingleTap(); return true }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (disposed) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            multiTouch = false
            dragX=event.x; dragY=event.y
            handle = handles().indexOfFirst { hypot(it.x-event.x,it.y-event.y) < 26*density }.let { if (it<0) 0 else it+1 }
            if (handle != 0) { removeCallbacks(edgeScroll); post(edgeScroll) }
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        if (event.pointerCount>1) { multiTouch = true; handle=0 }
        if (handle != 0) {
            when(event.actionMasked) {
                MotionEvent.ACTION_MOVE -> dragHandle(event.x,event.y)
                MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(edgeScroll)
                    handle=0
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            return true
        }
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
            if (multiTouch) { removeCallbacks(settle); post(settle) }
        }
        return true
    }
    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            layout?.let { transform = transform.copy(scrollX=scroller.currX.toDouble(),scrollY=scroller.currY.toDouble()).constrained(it.size,viewport) }
            changed(); postInvalidateOnAnimation()
        }
    }
    private fun animateZoom(focus: DPoint,target: Double) {
        val initial = transform
        zoomAnimation?.cancel()
        zoomAnimation = ValueAnimator.ofFloat(initial.zoom.toFloat(),target.toFloat()).apply {
            duration=220
            addUpdateListener { animator -> layout?.let { transform = initial.zoomAt(focus,(animator.animatedValue as Float).toDouble(),minimumZoom,options.maxZoom).constrained(it.size,viewport); changed() } }
            start()
        }
    }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.ScrollView"
        info.isScrollable = true
        info.contentDescription = "Страница ${activePage+1} из ${session.sizes.size}"
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }
    override fun performAccessibilityAction(action: Int,arguments: android.os.Bundle?): Boolean = when(action) {
        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> { next(1); true }
        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> { next(-1); true }
        else -> super.performAccessibilityAction(action,arguments)
    }
    fun release() {
        if (disposed) return
        anchor()?.let(onPosition)
        disposed=true; removeCallbacks(settle); removeCallbacks(positionReport); removeCallbacks(edgeScroll)
        cancelSelectionWork(); zoomAnimation?.cancel(); scroller.forceFinished(true); scope.cancel(); renderer.release()
    }
    private fun DRect.rectF() = RectF(left.toFloat(),top.toFloat(),right.toFloat(),bottom.toFloat())
}
