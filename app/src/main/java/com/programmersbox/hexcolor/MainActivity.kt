package com.programmersbox.hexcolor

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.view.longClicks
import com.programmersbox.dragswipe.Direction
import com.programmersbox.dragswipe.DragSwipeActions
import com.programmersbox.dragswipe.DragSwipeAdapter
import com.programmersbox.dragswipe.DragSwipeUtils
import com.programmersbox.gsonutils.sharedPrefObjectDelegate
import com.programmersbox.helpfulutils.*
import com.programmersbox.rxutils.invoke
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.favorite_item.view.*
import kotlinx.android.synthetic.main.favorite_layout.view.*
import java.util.*
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private val disposables = CompositeDisposable()
    private val backgroundUpdate = PublishSubject.create<Int>()
    private val uiShow = PublishSubject.create<String>()
    private val colorApiShow = BehaviorSubject.create<ColorApi>()
    private val imageGet = PublishSubject.create<Int>()
    private val favoriteSubject = PublishSubject.create<Boolean>()

    private val buttonList by lazy { listOf(randomColor, addFavorites, viewFavorites, swatchHistory, moreInfo, pickImage) }

    private val currentApiColor get() = colorApiShow.value ?: colorApiBlack

    private var favoriteList: List<ColorApi>? by sharedPrefObjectDelegate(emptyList(), key = "favorites")
    private var history: List<ColorApi>? by sharedPrefObjectDelegate(emptyList(), key = "history")

    private val favoriteCheck: (ColorApi) -> Boolean =
        { it.name?.value ?: "" == currentApiColor.name?.value ?: "" && it.hex?.value == currentApiColor.hex?.value }

    private val photoManager = PhotoManager(imageGet, this, disposables)
    private lateinit var rxArea: RxArea

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissions(Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE) {
            if (!it.isGranted) toast("Please accept the permissions: ${it.deniedPermissions}")
        }

        val digits = listOf(zero, one, two, three, four, five, six, seven, eight, nine, A, B, C, D, E, F)
            .map { digit -> digit.clicks().map { digit.text.toString() } }
        val digitStream = Observable.merge(digits)
        rxArea = RxArea(defaultSharedPref, back.clicks(), clear.clicks(), digitStream, disposables, backgroundUpdate, uiShow, colorApiShow)

        var constraintRange = ConstraintRange(
            layout,
            ConstraintSet().apply { clone(layout) },
            ConstraintSet().apply { clone(this@MainActivity, R.layout.activity_main_two) }
        )

        menuOptions
            .clicks()
            .subscribe { constraintRange++ }
            .addTo(disposables)

        back
            .longClicks()
            .subscribe { clear.performClick() }
            .addTo(disposables)

        color_name
            .longClicks()
            .subscribe { favImage.performClick() }
            .addTo(disposables)

        color_name
            .clicks()
            .map { currentApiColor }
            .subscribe(this::moreColorInfo)
            .addTo(disposables)

        hex
            .longClicks()
            .subscribe { randomColor() }
            .addTo(disposables)

        rgb
            .clicks()
            .map { currentApiColor }
            .subscribe(this::moreColorInfo)
            .addTo(disposables)

        favImage
            .clicks()
            .map { favoriteList!!.any(favoriteCheck) }
            .subscribe { if (it) removeFromFavorites(currentApiColor) else addToFavorites(currentApiColor) }
            .addTo(disposables)

        backgroundUpdate
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(this::animateColorChange)
            .addTo(disposables)

        uiShow
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(AndroidSchedulers.mainThread())
            .subscribe { hex.text = it }
            .addTo(disposables)

        uiShow
            .map { true }
            .subscribe(favoriteSubject::invoke)
            .addTo(disposables)

        colorApiShow
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(AndroidSchedulers.mainThread())
            .subscribe { c -> rgb.text = c.rgb?.let { "(${it.r}, ${it.g}, ${it.b})" } }
            .addTo(disposables)

        colorApiShow
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(AndroidSchedulers.mainThread())
            .subscribe { c -> color_name.text = c.name?.value ?: "---" }
            .addTo(disposables)

        colorApiShow
            .filter { it != colorApiBlack }
            .distinct(ColorApi::hex)
            .subscribe(this::addToHistory)
            .addTo(disposables)

        colorApiShow
            .map { true }
            .subscribe(favoriteSubject::invoke)
            .addTo(disposables)

        favoriteSubject
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(AndroidSchedulers.mainThread())
            .map { favoriteList!!.any(favoriteCheck) && rxArea.getCurrentHex().length == 7 && it }
            .map { if (it) 1f else 0f }
            .map { ValueAnimator.ofFloat(favImage.progress, it) }
            .subscribe {
                it.addUpdateListener { animation: ValueAnimator ->
                    favImage.progress = animation.animatedValue as Float
                    favImageShadow.progress = animation.animatedValue as Float
                }
                it.start()
            }
            .addTo(disposables)

        imageGet
            .subscribe(this::randomColorInt)
            .addTo(disposables)

        randomColor
            .clicks()
            .subscribe { randomColor() }
            .addTo(disposables)

        addFavorites
            .clicks()
            .map { favoriteList!!.any(favoriteCheck) }
            .subscribe { if (it) removeFromFavorites(currentApiColor) else addToFavorites(currentApiColor) }
            .addTo(disposables)

        viewFavorites
            .clicks()
            .subscribe { showFavoritesOrHistory(favoriteList!!, true) }
            .addTo(disposables)

        swatchHistory
            .clicks()
            .subscribe { showFavoritesOrHistory(history!!, false) }
            .addTo(disposables)

        moreInfo
            .clicks()
            .subscribe { moreColorInfo(currentApiColor) }
            .addTo(disposables)

        pickImage
            .clicks()
            .subscribe { photoManager.selectImage() }
            .addTo(disposables)

    }

    private fun <T> MutableList<T>.addMax(item: T) {
        add(0, item)
        if (size > 50) removeAt(lastIndex)
    }

    private fun addToHistory(colorApi: ColorApi) {
        val hist = history!!.toMutableList()
        hist.addMax(colorApi)
        history = hist
    }

    private fun LottieAnimationView.changeTint(newColor: Int) =
        addValueCallback(KeyPath("**"), LottieProperty.COLOR_FILTER) { PorterDuffColorFilter(newColor, PorterDuff.Mode.SRC_ATOP) }

    private fun animateColorChange(newColor: Int) = colorAnimator((layout.background as ColorDrawable).color, newColor)
        .subscribe { color ->
            layout.setBackgroundColor(color)
                .also { window.statusBarColor = color }
                .let { ColorUtils.tintColor(color) to ColorUtils.tintColor(color, true) }
                .also {
                    menuOptions.setColorFilter(it.first)
                    menuOptionsShadow.setColorFilter(it.second)
                }
                .also {
                    favImage.changeTint(it.first)
                    favImageShadow.changeTint(it.second)
                }
                .also { pair ->
                    buttonList.forEach {
                        it.setTextColor(pair.first)
                        it.strokeColor = ColorStateList.valueOf(pair.second)
                    }
                }
                .also { pair ->
                    listOf(zero, one, two, three, four, five, six, seven, eight, nine, A, B, C, D, E, F, hex, rgb, back, clear, color_name).forEach {
                        it.setTextColor(pair.first)
                        it.setShadowLayer(1.6f, 1.5f, 1.3f, pair.second)
                    }
                }
        }
        .addTo(disposables)
        .unit()

    private fun colorAnimator(fromColor: Int, toColor: Int): Observable<Int> {
        val valueAnimator = ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor)
        valueAnimator.duration = 250
        val observable = Observable.create<Int> { emitter -> valueAnimator.addUpdateListener { emitter.onNext(it.animatedValue as Int) } }
        return observable.doOnSubscribe { valueAnimator.start() }
    }

    private fun moreColorInfo(colorApi: ColorApi) = MaterialAlertDialogBuilder(this)
        .setTitle(colorApi.name?.value)
        .setMessage(
            "Hex: ${colorApi.hex?.value}\n" +
                    "RGB: ${colorApi.rgb?.value}\n" +
                    "CMYK: ${colorApi.cmyk?.value}\n" +
                    "HSL: ${colorApi.hsl?.value}\n" +
                    "HSV: ${colorApi.hsv?.value}\n" +
                    "XYZ: ${colorApi.XYZ?.value}"
        )
        .show().unit()

    @Suppress("unused")
    private fun Any?.unit() = Unit

    private fun addToFavorites(colorApi: ColorApi) {
        val favorites = favoriteList!!.toMutableList()
        if (favorites.all { it.name?.value != colorApi.name?.value || it.hex?.value != colorApi.hex?.value }) favorites.add(colorApi)
            .also { toast("Added to Favorites") }
        favoriteList = favorites
        favoriteSubject(true)
    }

    private fun removeFromFavorites(colorApi: ColorApi) {
        val favorites = favoriteList!!.toMutableList()
        if (favorites.removeIf { it.name?.value == colorApi.name?.value && it.hex?.value == colorApi.hex?.value }) toast("Removed from Favorites")
        favoriteList = favorites
        favoriteSubject(true)
    }

    private fun newColor(colorString: String, drop: Int) {
        clear.performClick()
        colorString.toUpperCase(Locale.getDefault()).drop(drop).forEach { rxArea.digitClicked("$it") }
    }

    private fun randomColor(colorString: String? = null) = newColor(colorString ?: Random.nextColor().toHexString(), 1)
    private fun randomColorInt(colorString: Int? = null) = newColor((colorString ?: Random.nextColor()).toHexString(), 3)

    @SuppressLint("SetTextI18n")
    private fun showFavoritesOrHistory(list: List<ColorApi>, isFavorite: Boolean) {
        val view = layoutInflater.inflate(R.layout.favorite_layout, null)
        val adapter = FavoriteAdapter(list.toMutableList())
        view.favTitle.text = "${if (isFavorite) "Favorites" else "History"}: ${list.size}"
        view.favoriteRV.adapter = adapter
        DragSwipeUtils.setDragSwipeUp(
            adapter, view.favoriteRV, listOf(Direction.UP, Direction.DOWN), listOf(Direction.START, Direction.END),
            object : DragSwipeActions<ColorApi> {
                override fun onSwiped(
                    viewHolder: RecyclerView.ViewHolder, direction: Direction, dragSwipeAdapter: DragSwipeAdapter<ColorApi, *>
                ) {
                    super.onSwiped(viewHolder, direction, dragSwipeAdapter)
                    view.favTitle.text = "${if (isFavorite) "Favorites" else "History"}: ${adapter.dataList.size}"
                    if (isFavorite) favoriteSubject(adapter.dataList.any(favoriteCheck))
                }
            }
        )
        MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton("Done") { d, _ -> d.dismiss() }
            .setOnDismissListener {
                if (isFavorite) favoriteList = adapter.dataList
                else history = adapter.dataList
            }
            .show()
    }

    inner class FavoriteAdapter(dataList: MutableList<ColorApi>) : DragSwipeAdapter<ColorApi, FavHolder>(dataList) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavHolder =
            FavHolder(layoutInflater.inflate(R.layout.favorite_item, parent, false))

        @SuppressLint("SetTextI18n")
        override fun FavHolder.onBind(item: ColorApi, position: Int) {
            itemView.setBackgroundColor(item.hex?.value?.let(Color::parseColor) ?: Color.BLACK)
            name.setTextColor(ColorUtils.tintColor(item.hex?.value?.let(Color::parseColor) ?: Color.BLACK))
            name.text = "${position + 1}. ${item.name?.value ?: item.hex?.value}"
            itemView
                .clicks()
                .map { item.hex?.value }
                .subscribe(this@MainActivity::randomColor)
                .addTo(disposables)
        }
    }

    class FavHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.colorName
    }
}