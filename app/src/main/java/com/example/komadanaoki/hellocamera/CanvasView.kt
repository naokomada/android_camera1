package com.example.komadanaoki.hellocamera

import android.R.attr.y
import android.R.attr.x
import android.content.Context
import android.graphics.*
import android.opengl.ETC1.getWidth
import android.util.AttributeSet
import android.view.View
import org.opencv.core.Point


class CanvasView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private var mBitmap: Bitmap? = null
    internal var mPoints: List<List<Point>>? = null

    init {

        mBitmap = null
        mPoints = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvasDraw(canvas)
    }

    private fun canvasDraw(canvas: Canvas) {
        val paint = Paint()
        paint.setStrokeWidth(100F)

        /* ⑤：背景色を設定 */
        canvas.drawColor(Color.BLACK)

        /* ⑥：原画像（Bitmap）を描画 */
        if (mBitmap != null) {
            val scale = getWidth().toFloat() / mBitmap!!.width
            canvas.scale(scale, scale)
            canvas.drawBitmap(mBitmap, 0F, 0F, paint)
        }

        /* ⑦：輪郭を描画 */
        if (mPoints != null) {
            val path = Path()
            paint.setAntiAlias(true)
            paint.setStyle(Paint.Style.STROKE)
            paint.setColor(Color.argb(128, 255, 0, 0))

            for (i in mPoints!!.indices) {
                val point = mPoints!![i]

                path.moveTo(point[0].x.toFloat(), point[0].y.toFloat())
                for (j in point.indices) {
                    path.lineTo(point[j].x.toFloat(), point[j].y.toFloat())
                }
                path.lineTo(point[0].x.toFloat(), point[0].y.toFloat())
                for (j in point.indices) {
                    path.lineTo(point[j].x.toFloat(), point[j].y.toFloat())
                }
            }

            canvas.drawPath(path, paint)
        }
    }

    fun setmBitmap(bmp: Bitmap) {
        mBitmap = bmp
    }

    fun setmPoints(points: List<List<Point>>) {
        mPoints = points
    }
}