package com.programmersbox.hexcolor

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.view.longClicks
import com.programmersbox.dragswipe.Direction
import com.programmersbox.dragswipe.DragSwipeActions
import com.programmersbox.dragswipe.DragSwipeAdapter
import com.programmersbox.dragswipe.DragSwipeUtils
import com.programmersbox.gsonutils.getObject
import com.programmersbox.gsonutils.putObject
import com.programmersbox.helpfulutils.defaultSharedPref
import com.programmersbox.helpfulutils.nextColor
import com.programmersbox.helpfulutils.requestPermissions
import com.programmersbox.helpfulutils.toHexString
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

    private lateinit var rxArea: RxArea

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissions(Manifest.permission.INTERNET) {
            if (!it.isGranted) Toast.makeText(this, "Please accept the permissions: ${it.deniedPermissions}", Toast.LENGTH_SHORT).show()
        }

        val digits = listOf(zero, one, two, three, four, five, six, seven, eight, nine, A, B, C, D, E, F)
            .map { digit -> digit.clicks().map { digit.text.toString() } }
        val digitStreams = Observable.merge(digits)

        rxArea = RxArea(defaultSharedPref, back.clicks(), clear.clicks(), digitStreams, disposables, backgroundUpdate, uiShow, colorApiShow)

        back
            .longClicks()
            .subscribe { clear.performClick() }
            .addTo(disposables)

        color_name
            .longClicks()
            .subscribe { colorApiShow.value?.let(this::addToFavorites) }
            .addTo(disposables)

        color_name
            .clicks()
            .map { colorApiShow.value ?: colorApiBlack }
            .subscribe(this::moreColorInfo)
            .addTo(disposables)

        hex
            .clicks()
            .map { colorApiShow.value ?: colorApiBlack }
            .subscribe(this::showMenu)
            .addTo(disposables)

        hex
            .longClicks()
            .subscribe { randomColor() }
            .addTo(disposables)

        rgb
            .clicks()
            .map { colorApiShow.value ?: colorApiBlack }
            .subscribe(this::moreColorInfo)
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

        colorApiShow
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(AndroidSchedulers.mainThread())
            .subscribe { c -> rgb.text = c.rgb?.let { "(${it.r}, ${it.g}, ${it.b})" } }
            .addTo(disposables)

        colorApiShow
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(AndroidSchedulers.mainThread())
            .subscribe { c -> color_name.text = c.name?.value ?: "" }
            .addTo(disposables)

    }

    private fun animateColorChange(newColor: Int) {
        colorAnimator((layout.background as ColorDrawable).color, newColor)
            .subscribe { color -> layout.setBackgroundColor(color).also { window.statusBarColor = color } }
            .addTo(disposables)
    }

    private fun colorAnimator(fromColor: Int, toColor: Int): Observable<Int> {
        val valueAnimator = ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor)
        valueAnimator.duration = 250 // milliseconds
        val observable = Observable.create<Int> { emitter -> valueAnimator.addUpdateListener { emitter.onNext(it.animatedValue as Int) } }
        return observable.doOnSubscribe { valueAnimator.start() }
    }

    enum class MenuOptions(private val info: String) {
        ADD("Add to favorites"), RANDOM("Random Color"), VIEW("View Favorites"), MORE_INFO("More Info");

        fun data(c: ColorApi) = when (this) {
            ADD -> "Add ${c.name?.value} to favorites"
            else -> info
        }
    }

    private fun moreColorInfo(colorApi: ColorApi) {
        MaterialAlertDialogBuilder(this)
            .setTitle(colorApi.name?.value)
            .setMessage(
                "Hex: ${colorApi.hex?.value}\n" +
                        "RGB: ${colorApi.rgb?.value}\n" +
                        "CMYK: ${colorApi.cmyk?.value}\n" +
                        "HSL: ${colorApi.hsl?.value}\n" +
                        "HSV: ${colorApi.hsv?.value}\n" +
                        "XYZ: ${colorApi.XYZ?.value}"
            )
            .show()
    }

    private fun addToFavorites(colorApi: ColorApi) {
        val favorites = (defaultSharedPref.getObject("favorites", emptyList<ColorApi>()) ?: emptyList()).toMutableList()
        if (favorites.all { it.name?.value != colorApi.name?.value }) favorites.add(colorApi)
            .also { Toast.makeText(this, "Added to Favorites", Toast.LENGTH_SHORT).show() }
        defaultSharedPref.edit().putObject("favorites", favorites).apply()
    }

    private fun randomColor(colorString: String? = null) {
        clear.performClick()
        (colorString ?: Random.nextColor().toHexString()).toUpperCase(Locale.getDefault()).drop(1).forEach { rxArea.digitClicked("$it") }
    }

    private fun showMenu(colorApi: ColorApi) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Options")
            .setItems(MenuOptions.values().map { it.data(colorApi) }.toTypedArray()) { _, i ->
                when (MenuOptions.values()[i]) {
                    MenuOptions.ADD -> addToFavorites(colorApi)
                    MenuOptions.RANDOM -> randomColor()
                    MenuOptions.VIEW -> showFavorites()
                    MenuOptions.MORE_INFO -> moreColorInfo(colorApi)
                }
            }
            .show()
    }

    @SuppressLint("SetTextI18n")
    private fun showFavorites() {
        val favorites = (defaultSharedPref.getObject<List<ColorApi>>("favorites", null) ?: emptyList()).toMutableList()
        val view = layoutInflater.inflate(R.layout.favorite_layout, null)
        val adapter = FavoriteAdapter(this, favorites)
        view.favTitle.text = "Favorites: ${favorites.size}"
        view.favoriteRV.adapter = adapter
        DragSwipeUtils.setDragSwipeUp(
            adapter, view.favoriteRV, Direction.UP + Direction.DOWN, Direction.START + Direction.END,
            object : DragSwipeActions<ColorApi, FavHolder> {
                override fun onSwiped(
                    viewHolder: RecyclerView.ViewHolder, direction: Direction,
                    dragSwipeAdapter: DragSwipeAdapter<ColorApi, FavHolder>
                ) {
                    super.onSwiped(viewHolder, direction, dragSwipeAdapter)
                    view.favTitle.text = "Favorites: ${adapter.dataList.size}"
                }
            }
        )
        MaterialAlertDialogBuilder(this)
            .setView(view)
            .setOnDismissListener { defaultSharedPref.edit().putObject("favorites", adapter.dataList).apply() }
            .show()
    }

    inner class FavoriteAdapter(private val context: Context, dataList: MutableList<ColorApi>) : DragSwipeAdapter<ColorApi, FavHolder>(dataList) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavHolder =
            FavHolder(LayoutInflater.from(context).inflate(R.layout.favorite_item, parent, false))

        @SuppressLint("SetTextI18n")
        override fun FavHolder.onBind(item: ColorApi, position: Int) {
            itemView.setBackgroundColor(item.hex?.value?.let(Color::parseColor) ?: Color.BLACK)
            name.text = "${position + 1}. ${item.name?.value ?: item.hex?.value}"
            itemView.setOnClickListener { randomColor(item.hex?.value) }
        }
    }

    class FavHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.colorName
    }
}