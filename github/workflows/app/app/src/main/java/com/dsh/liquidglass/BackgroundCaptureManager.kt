package com.dsh.liquidglass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import java.util.Collections
import java.util.WeakHashMap

/**
 * 全局共享背景快照。
 * - 同一个 root，在 50ms 内只 root.draw() 一次。
 * - 捕获时隐藏所有已知磁贴，避免把磁贴自身画进背景。
 * - 每个磁贴只从共享快照裁一个小 Bitmap。
 */
object BackgroundCaptureManager {

    private const val TTL_MS = 50L
    private val lock = Any()

    private var cachedRoot: View? = null
    private var cachedBitmap: Bitmap? = null
    private var cachedAt = 0L
    private var cachedWidth = 0
    private var cachedHeight = 0

    private val tileClassNames = HashSet<String>()
    private val knownTiles = Collections.newSetFromMap(WeakHashMap<View, Boolean>())

    private val cropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        isDither = true
    }

    fun registerTileClasses(classes: Collection<String>) {
        synchronized(lock) { tileClassNames.addAll(classes) }
    }

    fun registerTile(tile: View) {
        synchronized(lock) { knownTiles.add(tile) }
    }

    fun captureFor(tile: View, background: View, root: View): Bitmap? {
        // 强制在主线程操作 View 属性
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            return null 
        }

        synchronized(lock) {
            if (root.width <= 0 || root.height <= 0) return null
            if (background.width <= 0 || background.height <= 0) return null

            knownTiles.add(tile)

            val bgLoc = IntArray(2)
            val rootLoc = IntArray(2)
            // 使用 getLocationOnScreen 确保跨窗口坐标对齐
            background.getLocationOnScreen(bgLoc)
            root.getLocationOnScreen(rootLoc)

            val left = bgLoc[0] - rootLoc[0]
            val top = bgLoc[1] - rootLoc[1]
            val width = minOf(background.width, root.width - left)
            val height = minOf(background.height, root.height - top)
            
            if (left < 0 || top < 0 || width <= 0 || height <= 0) return null

            val full = obtainFullSnapshotLocked(root) ?: return null

            return try {
                val crop = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                Canvas(crop).drawBitmap(
                    full,
                    Rect(left, top, left + width, top + height),
                    Rect(0, 0, width, height),
                    cropPaint
                )
                crop
            } catch (t: Throwable) {
                null
            }
        }
    }

    private fun obtainFullSnapshotLocked(root: View): Bitmap? {
        val now = SystemClock.uptimeMillis()
        val current = cachedBitmap
        
        if (current != null && !current.isRecycled &&
            cachedRoot === root && cachedWidth == root.width &&
            cachedHeight == root.height && now - cachedAt <= TTL_MS
        ) {
            return current
        }

        val width = root.width
        val height = root.height
        if (width <= 0 || height <= 0 || !root.isAttachedToWindow) return null

        val full = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (t: Throwable) {
            return null
        }

        val hidden = ArrayList<Pair<View, Int>>(knownTiles.size + 8)
        hideTilesLocked(root, hidden)

        try {
            root.draw(Canvas(full))
        } catch (t: Throwable) {
            full.recycle()
            return null
        } finally {
            for ((view, visibility) in hidden) {
                view.visibility = visibility
            }
        }

        current?.recycle()

        cachedRoot = root
        cachedBitmap = full
        cachedAt = now
        cachedWidth = width
        cachedHeight = height
        return full
    }

    private fun hideTilesLocked(root: View, out: MutableList<Pair<View, Int>>) {
        fun walk(view: View) {
            val isTile = tileClassNames.contains(view.javaClass.name) || knownTiles.contains(view)
            if (isTile) {
                if (view.visibility == View.VISIBLE) {
                    out.add(view to view.visibility)
                    view.visibility = View.INVISIBLE
                }
                return
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    walk(view.getChildAt(i))
                }
            }
        }
        walk(root)
    }
}
