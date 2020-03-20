package com.programmersbox.hexcolor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.core.graphics.applyCanvas
import androidx.palette.graphics.Palette
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.programmersbox.gsonutils.getJsonApi


fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

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

private fun getPaletteFromColor(hex: String) = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888)
    .applyCanvas { drawColor(Color.parseColor(hex)) }
    .let { Palette.from(it).generate() }

private fun getPaletteFromColor(color: Int) = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888)
    .applyCanvas { drawColor(color) }
    .let { Palette.from(it).generate() }

fun MaterialAlertDialogBuilder.setView(@LayoutRes viewId: Int, block: View.() -> Unit) =
    setView(LayoutInflater.from(context).inflate(viewId, null).apply(block))

fun MaterialAlertDialogBuilder.setCustomTitle(@LayoutRes viewId: Int, block: View.() -> Unit) =
    setCustomTitle(LayoutInflater.from(context).inflate(viewId, null).apply(block))