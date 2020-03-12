package com.programmersbox.hexcolor

import com.programmersbox.gsonutils.getJsonApi

fun getColorApi(color: String) = getJsonApi<ColorApi>("http://thecolorapi.com/id?hex=$color")

fun Int.valueOf(): Triple<Int, Int, Int> {
    val r = (this shr 16 and 0xff)// / 255.0f
    val g = (this shr 8 and 0xff)// / 255.0f
    val b = (this and 0xff)// / 255.0f
    return Triple(r, g, b)
}