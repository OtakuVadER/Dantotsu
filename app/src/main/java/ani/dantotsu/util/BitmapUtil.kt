package ani.dantotsu.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class BitmapUtil {
    companion object {
        fun roundCorners(bitmap: Bitmap, cornerRadius: Float = 20f): Bitmap {
            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint()
            paint.isAntiAlias = true
            paint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val rect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

            return output
        }

        fun convertDrawableToBitmap(drawable: Drawable, width: Int, height: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        }

        fun downloadImageAsBitmap(imageUrl: String): Bitmap? {
            var bitmap: Bitmap? = null

            runBlocking(Dispatchers.IO) {
                var inputStream: InputStream? = null
                var urlConnection: HttpURLConnection? = null
                try {
                    val url = URL(imageUrl)
                    urlConnection = url.openConnection() as HttpURLConnection
                    urlConnection.requestMethod = "GET"
                    urlConnection.connect()

                    if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                        inputStream = urlConnection.inputStream
                        bitmap = BitmapFactory.decodeStream(inputStream)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    inputStream?.close()
                    urlConnection?.disconnect()
                }
            }
            return bitmap?.let { roundCorners(it) }
        }
    }
}