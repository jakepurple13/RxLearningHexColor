package com.programmersbox.hexcolor

import android.graphics.Color
import com.programmersbox.gsonutils.getJsonApi


fun getColorApi(color: String) = getJsonApi<ColorApi>("http://thecolorapi.com/id?hex=$color")

fun Int.valueOf(): Triple<Int, Int, Int> {
    val r = (this shr 16 and 0xff)// / 255.0f
    val g = (this shr 8 and 0xff)// / 255.0f
    val b = (this and 0xff)// / 255.0f
    return Triple(r, g, b)
}

object ColorUtils {

    private fun colorIsLight(r: Int, g: Int, b: Int): Boolean {
        val a = 1 - (0.299 * r + 0.587 * g + 0.114 * b) / 255
        return a < 0.5
    }

    private fun colorIsLight(color: Int): Boolean {
        val (r: Int, g: Int, b: Int) = color.valueOf()
        val a = 1 - (0.299 * r + 0.587 * g + 0.114 * b) / 255
        return a < 0.5
    }

    fun tintColor(color: Int, inverse: Boolean = false) = if (colorIsLight(color) && !inverse) Color.BLACK else Color.WHITE

}
