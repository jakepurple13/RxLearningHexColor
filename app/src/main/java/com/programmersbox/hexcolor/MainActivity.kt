package com.programmersbox.hexcolor

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.davemorrissey.labs.subscaleview.ImageSource
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.view.longClicks
import com.programmersbox.dragswipe.Direction
import com.programmersbox.dragswipe.DragSwipeActions
import com.programmersbox.dragswipe.DragSwipeAdapter
import com.programmersbox.dragswipe.DragSwipeUtils
import com.programmersbox.gsonutils.getObject
import com.programmersbox.gsonutils.putObject
import com.programmersbox.helpfulutils.*
import com.programmersbox.rxutils.invoke
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.favorite_item.view.*
import kotlinx.android.synthetic.main.favorite_layout.view.*
import kotlinx.android.synthetic.main.zoom_custom_title.view.*
import kotlinx.android.synthetic.main.zoom_layout.view.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private val takePhotoRequestID = 5
    private val pickPhotoRequestID = takePhotoRequestID + 1

    private val disposables = CompositeDisposable()
    private val backgroundUpdate = PublishSubject.create<Int>()
    private val uiShow = PublishSubject.create<String>()
    private val colorApiShow = BehaviorSubject.create<ColorApi>()
    private val imageShow = PublishSubject.create<Bitmap>()
    private val imageGet = PublishSubject.create<Int>()
    private val favoriteSubject = PublishSubject.create<Unit>()
    private val currentApiColor get() = colorApiShow.value ?: colorApiBlack
    private val favoriteList get() = (defaultSharedPref.getObject("favorites", emptyList<ColorApi>()) ?: emptyList()).toMutableList()
    private val history get() = (defaultSharedPref.getObject("history", emptyList<ColorApi>()) ?: emptyList()).toMutableList()
    private val favoriteCheck: (ColorApi) -> Boolean =
        { it.name?.value ?: "" == currentApiColor.name?.value ?: "" && it.hex?.value == currentApiColor.hex?.value }

    @Suppress("DEPRECATION")
    private val folderPath = "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath}/HexColor"
    private val folderLocation get() = File(folderPath)
    private val pictureName get() = "JPEG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}_"

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

        menuOptions
            .clicks()
            .map { currentApiColor }
            .subscribe(this::showMenu)
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
            .map { favoriteList.any(favoriteCheck) }
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
            .map { Unit }
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
            .map { Unit }
            .subscribe(favoriteSubject::invoke)
            .addTo(disposables)

        favoriteSubject
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(AndroidSchedulers.mainThread())
            .map { favoriteList.any(favoriteCheck) && rxArea.getCurrentHex().length == 7 }
            .map { if (it) 0f to 1f else 1f to 0f }
            .map { ValueAnimator.ofFloat(it.first, it.second) }
            .subscribe {
                it.addUpdateListener { animation: ValueAnimator ->
                    favImage.progress = animation.animatedValue as Float
                    favImageShadow.progress = animation.animatedValue as Float
                }
                it.start()
            }
            .addTo(disposables)

        imageShow
            .subscribe(this::getImagePixel)
            .addTo(disposables)

        imageGet
            .subscribe(this::randomColorInt)
            .addTo(disposables)

    }

    private fun <T> MutableList<T>.addMax(item: T) {
        add(0, item)
        if (size > 50) removeAt(lastIndex)
    }

    private fun addToHistory(colorApi: ColorApi) {
        val hist = history
        hist.addMax(colorApi)
        defaultSharedPref.edit().putObject("history", hist).apply()
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams", "SimpleDateFormat")
    private fun getImagePixel(bitmap: Bitmap) {
        folderLocation.apply { if (!exists()) mkdirs() }
        val view = layoutInflater.inflate(R.layout.zoom_layout, null)
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                if (view.zoomImage.isReady) {
                    val sCoord: PointF = view.zoomImage.viewToSourceCoord(e.x, e.y)!!
                    val pixel = bitmap.getPixel(sCoord.x.toInt(), sCoord.y.toInt())
                    imageGet(pixel)
                }
            }
        })

        view.zoomImage.setImage(ImageSource.bitmap(bitmap))
        view.zoomImage.setOnTouchListener { _, motionEvent -> gestureDetector.onTouchEvent(motionEvent) }

        val titleView = layoutInflater.inflate(R.layout.zoom_custom_title, null)
        val palette = Palette.from(bitmap).generate()
        palette.dominantSwatch?.titleTextColor?.let { titleView.titleText.setTextColor(it) }
        palette.dominantSwatch?.rgb?.let { titleView.zoomTitleBackground.setBackgroundColor(it) }

        MaterialAlertDialogBuilder(this)
            .setCustomTitle(titleView)
            .setView(view)
            .setPositiveButton("Done") { _, _ -> }
            .whatIf(currentPhotoPath?.isNotEmpty()) {
                setNeutralButton("Save Photo") { _, _ -> bitmap.saveFile(File(folderPath, "$pictureName.jpg")) }
            }
            .setOnDismissListener { currentPhotoPath = null }
            .show()
    }

    private fun Bitmap.saveFile(f: File) {
        if (!f.exists()) f.createNewFile()
        val stream = FileOutputStream(f)
        compress(Bitmap.CompressFormat.JPEG, 100, stream)
        stream.flush()
        stream.close()
        toast("Photo Saved")
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
                    swatchHistory.setColorFilter(it.first)
                    swatchHistoryShadow.setColorFilter(it.second)
                }
                .also {
                    favImage.changeTint(it.first)
                    favImageShadow.changeTint(it.second)
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

    enum class MenuOptions(private val info: String) {
        ADD("Add to favorites"),
        RANDOM("Random Color"),
        VIEW("View Favorites"),
        MORE_INFO("More Info"),
        SELECT_IMAGE("Pick a color from a picture"),
        VIEW_HISTORY("View History");

        fun data(c: ColorApi) = when (this) {
            ADD -> "Add ${c.name?.value ?: c.hex?.value} to favorites"
            else -> info
        }
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
        val favorites = favoriteList
        if (favorites.all { it.name?.value != colorApi.name?.value || it.hex?.value != colorApi.hex?.value }) favorites.add(colorApi)
            .also { toast("Added to Favorites") }
        defaultSharedPref.edit().putObject("favorites", favorites).apply()
        favoriteSubject(Unit)
    }

    private fun removeFromFavorites(colorApi: ColorApi) {
        val favorites = favoriteList
        if (favorites.removeIf { it.name?.value == colorApi.name?.value && it.hex?.value == colorApi.hex?.value }) toast("Removed from Favorites")
        defaultSharedPref.edit().putObject("favorites", favorites).apply()
        favoriteSubject(Unit)
    }

    private fun newColor(colorString: String, drop: Int) {
        clear.performClick()
        colorString.toUpperCase(Locale.getDefault()).drop(drop).forEach { rxArea.digitClicked("$it") }
    }

    private fun randomColor(colorString: String? = null) = newColor(colorString ?: Random.nextColor().toHexString(), 1)
    private fun randomColorInt(colorString: Int? = null) = newColor((colorString ?: Random.nextColor()).toHexString(), 3)

    private fun showMenu(colorApi: ColorApi) = MaterialAlertDialogBuilder(this)
        .setTitle("Options")
        .setItems(MenuOptions.values().map { it.data(colorApi) }.toTypedArray()) { _, i ->
            when (MenuOptions.values()[i]) {
                MenuOptions.ADD -> addToFavorites(colorApi)
                MenuOptions.RANDOM -> randomColor()
                MenuOptions.VIEW -> showFavorites()
                MenuOptions.MORE_INFO -> moreColorInfo(colorApi)
                MenuOptions.SELECT_IMAGE -> selectImage()
                MenuOptions.VIEW_HISTORY -> showHistory()
            }
        }
        .show().unit()

    private fun selectImage() {
        val options = arrayOf<CharSequence>("Take Photo", "Choose from Gallery", "Cancel")
        MaterialAlertDialogBuilder(this)
            .setTitle("Take a picture from")
            .setItems(options) { dialog, item ->
                when (options[item]) {
                    "Take Photo" -> requestPermissions(
                        Manifest.permission.CAMERA,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) { if (it.isGranted) dispatchTakePictureIntent() else toast("Please accept the permissions in order to take a photo") }
                    "Choose from Gallery" -> requestPermissions(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) { if (it.isGranted) pickPhoto() else toast("Please accept the permissions in order to pick a photo") }
                    "Cancel" -> dialog.dismiss()
                }
            }
            .show()
    }

    private fun pickPhoto() {
        val photoPickerIntent = Intent(Intent.ACTION_PICK)
        photoPickerIntent.type = "image/*"
        startActivityForResult(photoPickerIntent, pickPhotoRequestID)
    }

    private var currentPhotoPath: String? = null

    @SuppressLint("SimpleDateFormat")
    @Throws(IOException::class)
    private fun createImageFile(): Single<File> = Single.create emitter@{ emitter ->
        try {
            emitter.onSuccess(File.createTempFile(pictureName, ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES)))
        } catch (e: IOException) {
            emitter.onError(e)
        }
    }

    private fun startPictureIntent(file: File, takePictureIntent: Intent) {
        currentPhotoPath = file.absolutePath
        val photoURI: Uri = FileProvider.getUriForFile(this, "com.example.android.fileprovider", file)
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
        startActivityForResult(takePictureIntent, takePhotoRequestID)
    }

    private fun dispatchTakePictureIntent() = Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
        takePictureIntent.resolveActivity(packageManager)?.also {
            createImageFile()
                .subscribeBy(
                    onError = { toast("Something went wrong, please try again") },
                    onSuccess = { startPictureIntent(it, takePictureIntent) }
                )
                .addTo(disposables)
        }
    }.unit()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_CANCELED) {
            when (requestCode) {
                takePhotoRequestID -> if (resultCode == Activity.RESULT_OK && data != null) imageShow(BitmapFactory.decodeFile(currentPhotoPath!!))
                pickPhotoRequestID -> if (resultCode == Activity.RESULT_OK && data?.data != null)
                    imageShow(BitmapFactory.decodeStream(contentResolver.openInputStream(data.data!!)))
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showHistory() {
        val view = layoutInflater.inflate(R.layout.favorite_layout, null)
        val adapter = FavoriteAdapter(history)
        view.favTitle.text = "History: ${history.size}"
        view.favoriteRV.adapter = adapter
        MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton("Done") { d, _ -> d.dismiss() }
            .show()
    }

    @SuppressLint("SetTextI18n")
    private fun showFavorites() {
        val favorites = favoriteList
        val view = layoutInflater.inflate(R.layout.favorite_layout, null)
        val adapter = FavoriteAdapter(favorites)
        view.favTitle.text = "Favorites: ${favorites.size}"
        view.favoriteRV.adapter = adapter
        DragSwipeUtils.setDragSwipeUp(
            adapter, view.favoriteRV, listOf(Direction.UP, Direction.DOWN), listOf(Direction.START, Direction.END),
            object : DragSwipeActions<ColorApi, FavHolder> {
                override fun onSwiped(
                    viewHolder: RecyclerView.ViewHolder, direction: Direction, dragSwipeAdapter: DragSwipeAdapter<ColorApi, FavHolder>
                ) {
                    super.onSwiped(viewHolder, direction, dragSwipeAdapter)
                    view.favTitle.text = "Favorites: ${adapter.dataList.size}"
                    favoriteSubject(Unit)
                }
            }
        )
        MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton("Done") { d, _ -> d.dismiss() }
            .setOnDismissListener { defaultSharedPref.edit().putObject("favorites", adapter.dataList).apply() }
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