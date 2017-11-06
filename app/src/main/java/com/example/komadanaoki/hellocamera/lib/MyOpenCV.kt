package com.example.komadanaoki.hellocamera.lib

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.core.MatOfPoint
import com.example.komadanaoki.hellocamera.CanvasView
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.utils.Converters


class MyOpenCV {
    fun onImageScan(bmp: Bitmap, canvasView: CanvasView) {
        /* ①：画像読み込み */
        var mat = Mat()
        Utils.bitmapToMat(bmp, mat, true)

        /* ②：画像を二値化 */
        mat = getThreshold(mat)

        // あたらしいBmpをつくってmatを変換する
//        val newBmp = Bitmap.createBitmap(mat.width(), mat.height(), Bitmap.Config.ARGB_8888)
//        Utils.matToBitmap(mat, newBmp)
//        canvasView.setmBitmap(newBmp)
//        canvasView.invalidate()

        /* 輪郭の座標を取得 */
        val contours = getCornerOfRects(mat)
        val points = contour2point(contours)

        val newBmp = keystoneCorrection(bmp, points)

        /* ②：VIEWに原画像（Bitmap）を登録 */
        canvasView.setmBitmap(newBmp)

        /* ③：VIEWに抽出した輪郭座標を登録 */
        //canvasView.setmPoints(points)
        /* ④：VIEWの描画更新要求 */
        //canvasView.invalidate()
    }

    // 台形補正する
    private fun keystoneCorrection(bmp: Bitmap, point: List<List<Point>>): Bitmap {
        var mat = Mat()
        Utils.bitmapToMat(bmp, mat, true)

        var dstMat = Mat()
        val points = point.first()

        val srcPointsMat = Converters.vector_Point_to_Mat(point.first(), CvType.CV_32F)
        val dstPoints = calcDestPoints(points)
        val dstPointsMat = Converters.vector_Point_to_Mat(dstPoints, CvType.CV_32F)
        val perspectiveMmat = Imgproc.getPerspectiveTransform(srcPointsMat, dstPointsMat)

        val srcRect = trimRect(mat, points)
        Imgproc.warpPerspective(srcRect, dstMat, perspectiveMmat, srcRect.size(), Imgproc.INTER_LINEAR)

        val newBmp = Bitmap.createBitmap(dstMat.width(), dstMat.height(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(dstMat, newBmp)
        return newBmp
    }

    private fun trimRect(mat: Mat, points: List<Point>): Mat {
        val maxX = (points.maxBy { elem -> elem.x })!!.x
        val minX = (points.minBy { elem -> elem.x })!!.x
        val maxY = (points.maxBy { elem -> elem.y })!!.y
        val minY = (points.minBy { elem -> elem.y })!!.y

        val rect = Rect(
                minX.toInt(),
                minY.toInt(),
                (maxX-minX).toInt(),
                (maxY-minY).toInt()
        )
        return Mat(mat,rect)
    }


    // 送り先の点を算出する
    private fun calcDestPoints(srcPoints: List<Point>): List<Point> {
        val maxX = (srcPoints.maxBy { elem -> elem.x })!!.x
        val minX = (srcPoints.minBy { elem -> elem.x })!!.x
        val maxY = (srcPoints.maxBy { elem -> elem.y })!!.y
        val minY = (srcPoints.minBy { elem -> elem.y })!!.y

        return listOf(
                Point(maxX, minY),
                Point(minX, minY),
                Point(minX, maxY),
                Point(maxX, maxY)
        )
    }

    // 取得したMatに対して、四角形を検出して、輪郭を付けたMatにして返す
    private fun getThreshold(mat: Mat): Mat {
        // 画像をそのまま二値化しようとすると、ノイズがのったような結果になるので
        // 色空間をチャネルに分離した後で、それぞれのチャネルに二値化をする
        //
        /* ②-1-1：RGB空間チャネルの取得 */
        val mat_rgb = mat.clone()
        val (contour_rgb0, contour_rgb1, contour_rgb2) = matToCornerOfRectsByRGB(mat_rgb)

        // YUVでもRGBでやったのと同じことをする。多くの色空間でやったほうが精度が上がるため
        /* ②-2-1：YUV空間チャネルの取得 */
        val mat_yuv = mat.clone()
        val (contour_yuv0, contour_yuv1, contour_yuv2) = matToCornerOfRectsByYUV(mat_yuv)


        /* ②-3：マスク画像に輪郭を合成 */
        val mat_mask = drawRectsContours(
                mat,
                contour_rgb0,
                contour_rgb1,
                contour_rgb2,
                contour_yuv0,
                contour_yuv1,
                contour_yuv2
        )

        Imgproc.cvtColor(mat_mask, mat_mask, Imgproc.COLOR_RGB2GRAY)
        Imgproc.threshold(mat_mask, mat_mask, 0.0, 255.0, Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU)

        return mat_mask
    }

    private fun drawRectsContours(
            mat: Mat,
            contour_rgb0: List<MatOfPoint>,
            contour_rgb1: List<MatOfPoint>,
            contour_rgb2: List<MatOfPoint>,
            contour_yuv0: List<MatOfPoint>,
            contour_yuv1: List<MatOfPoint>,
            contour_yuv2: List<MatOfPoint>
    ): Mat {
        val mat_mask = Mat(mat.size(), CvType.CV_8UC4, Scalar.all(255.0))
        val color = Scalar(0.0, 0.0, 0.0)
        Imgproc.drawContours(mat_mask, contour_rgb0, -1, color, -1)
        Imgproc.drawContours(mat_mask, contour_rgb1, -1, color, -1)
        Imgproc.drawContours(mat_mask, contour_rgb2, -1, color, -1)
        Imgproc.drawContours(mat_mask, contour_yuv0, -1, color, -1)
        Imgproc.drawContours(mat_mask, contour_yuv1, -1, color, -1)
        Imgproc.drawContours(mat_mask, contour_yuv2, -1, color, -1)
        return mat_mask
    }

    private fun matToCornerOfRectsByYUV(mat_yuv: Mat?): Triple<List<MatOfPoint>, List<MatOfPoint>, List<MatOfPoint>> {
        Imgproc.cvtColor(mat_yuv, mat_yuv, Imgproc.COLOR_BGR2YUV)
        val channels_yuv = ArrayList<Mat>()
        Core.split(mat_yuv, channels_yuv)

        /* ②-2-2：YUV空間 → グレースケール変換 → 二値化 */
        Imgproc.cvtColor(mat_yuv, mat_yuv, Imgproc.COLOR_RGB2GRAY)
        Core.subtract(channels_yuv[0], mat_yuv, channels_yuv[0])
        Core.subtract(channels_yuv[1], mat_yuv, channels_yuv[1])
        Core.subtract(channels_yuv[2], mat_yuv, channels_yuv[2])
        Imgproc.threshold(channels_yuv[0], channels_yuv[0], 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        Imgproc.threshold(channels_yuv[1], channels_yuv[1], 0.0, 255.0, Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU)
        Imgproc.threshold(channels_yuv[2], channels_yuv[2], 0.0, 255.0, Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU)

        /* ②-2-3：YUVの輪郭を取得 */
        val contour_yuv0 = getCornerOfRects(channels_yuv[0])
        val contour_yuv1 = getCornerOfRects(channels_yuv[1])
        val contour_yuv2 = getCornerOfRects(channels_yuv[2])
        return Triple(contour_yuv0, contour_yuv1, contour_yuv2)
    }

    private fun matToCornerOfRectsByRGB(mat_rgb: Mat?): Triple<List<MatOfPoint>, List<MatOfPoint>, List<MatOfPoint>> {
        val channels_rgb = ArrayList<Mat>()
        // 3チャネルに分けて配列に入れる
        Core.split(mat_rgb, channels_rgb)

        /* ②-1-2：RGB空間 → グレースケール変換 → 二値化 */
        // グレースケールへの変換
        Imgproc.cvtColor(mat_rgb, mat_rgb, Imgproc.COLOR_RGB2GRAY)
        // 二値化
        Core.subtract(channels_rgb[0], mat_rgb, channels_rgb[0])
        Core.subtract(channels_rgb[1], mat_rgb, channels_rgb[1])
        Core.subtract(channels_rgb[2], mat_rgb, channels_rgb[2])
        Imgproc.threshold(channels_rgb[0], channels_rgb[0], 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        Imgproc.threshold(channels_rgb[1], channels_rgb[1], 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        Imgproc.threshold(channels_rgb[2], channels_rgb[2], 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)

        /* ②-1-3：RGBの輪郭を取得 */
        // getContourは自作のやつでやっている
        val contour_rgb0 = getCornerOfRects(channels_rgb[0])
        val contour_rgb1 = getCornerOfRects(channels_rgb[1])
        val contour_rgb2 = getCornerOfRects(channels_rgb[2])
        return Triple(contour_rgb0, contour_rgb1, contour_rgb2)
    }

    // 二値画像を受け取って、輪郭検出、直線近似、四角形だけ選ぶ処理をして、四角形の角の座標を返す
    private fun getCornerOfRects(mat: Mat): List<MatOfPoint> {
        val contour = ArrayList<MatOfPoint>()

        /* 二値画像中の輪郭を検出 */
        val tmp_contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat.zeros(Size(5.0, 5.0), CvType.CV_8UC1)
        Imgproc.findContours(mat, tmp_contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_TC89_L1)
        for (i in tmp_contours.indices) {
            if (Imgproc.contourArea(tmp_contours[i]) < mat.size().area() / (100 * 1)) {
                /* サイズが小さいエリアは無視 */
                continue
            }

            val ptmat2 = MatOfPoint2f(*tmp_contours[i].toArray())
            val approx = MatOfPoint2f()
            val approxf1 = MatOfPoint()

            /* 輪郭線の周囲長を取得 */
            val arclen = Imgproc.arcLength(ptmat2, true)
            /* 直線近似 */
            Imgproc.approxPolyDP(ptmat2, approx, 0.02 * arclen, true)
            approx.convertTo(approxf1, CvType.CV_32S)
            if (approxf1.size().area() != 4.0) {
                /* 四角形以外は無視 */
                continue
            }

            /* 輪郭情報を登録 */
            contour.add(approxf1)
        }

        return contour
    }

    // MatOfPointからPointにする
    private fun contour2point(contour: List<MatOfPoint>): List<List<Point>> {
        val points = ArrayList<List<Point>>()
        for (i in contour.indices) {
            points.add(contour[i].toList())
        }
        return points
    }
}