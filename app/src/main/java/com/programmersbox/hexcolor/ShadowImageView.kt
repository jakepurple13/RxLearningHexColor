package com.programmersbox.hexcolor

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.annotation.ColorInt
import androidx.annotation.StyleableRes
import androidx.constraintlayout.widget.ConstraintLayout
import kotlinx.android.synthetic.main.shadow_image_view_layout.view.*

class ShadowImageView : ConstraintLayout {
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        init(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context?) : super(context) {
        init(null)
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.shadow_image_view_layout, this, true)
    }

    private fun init(attrs: AttributeSet?) {
        attrs?.withTypedArray(context, R.styleable.ShadowImageView) {
            setImage(getDrawable(R.styleable.ShadowImageView_shadowDrawable))
            setShadowXY(
                getFloat(R.styleable.ShadowImageView_shadowDx, 5f),
                getFloat(R.styleable.ShadowImageView_shadowDy, 5f)
            )
            setImageColor(getColor(R.styleable.ShadowImageView_shadowColor, Color.WHITE))
            setShadowColor(getColor(R.styleable.ShadowImageView_shadowShadowColor, Color.BLACK))
        }
        //setImage(context.getDrawable(R.drawable.baseline_menu_white_18dp))
    }

    fun setImage(drawable: Drawable?) {
        mainImageView.setImageDrawable(drawable)
        shadowImageView.setImageDrawable(drawable)
    }

    fun setImageColor(@ColorInt color: Int) = mainImageView.setColorFilter(color)
    fun setShadowColor(@ColorInt color: Int) = shadowImageView.setColorFilter(color)

    fun setShadowLayer(dx: Float, dy: Float, @ColorInt color: Int) {
        setShadowXY(dx, dy)
        setShadowColor(color)
    }

    fun setShadowXY(dx: Float, dy: Float) {
        val params = shadowImageView.layoutParams as LayoutParams
        //params.updateMargins(left = dx.toInt(), top = dy.toInt())
        //params.marginStart = dx.toInt()
        //params.topMargin = dy.toInt()
        //shadowImageView.layoutParams = params
    }

    private inline fun AttributeSet.withTypedArray(
        context: Context, @StyleableRes attrs: IntArray,
        block: TypedArray.() -> Unit
    ) = with(context.obtainStyledAttributes(this, attrs, 0, 0)) {
        block()
        recycle()
    }

}