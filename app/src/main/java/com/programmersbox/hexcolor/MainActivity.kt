package com.programmersbox.hexcolor

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.RecyclerView
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
import com.programmersbox.helpfulutils.defaultSharedPref
import com.programmersbox.helpfulutils.nextColor
import com.programmersbox.helpfulutils.requestPermissions
import com.programmersbox.helpfulutils.toHexString
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
import kotlinx.android.synthetic.main.zoom_custom_title.view.*
import kotlinx.android.synthetic.main.zoom_layout.view.*
import java.io.File
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
    private val imageShow = BehaviorSubject.create<Bitmap>()
    private val imageGet = BehaviorSubject.create<Int>()

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

        imageShow
            .subscribe(this::getImagePixel)
            .addTo(disposables)

        imageGet
            .subscribe(this::randomColorInt)
            .addTo(disposables)

    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun getImagePixel(bitmap: Bitmap) {
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
            .show()
    }

    private fun animateColorChange(newColor: Int) = colorAnimator((layout.background as ColorDrawable).color, newColor)
        .subscribe { color -> layout.setBackgroundColor(color).also { window.statusBarColor = color } }
        .addTo(disposables)
        .unit()

    private fun colorAnimator(fromColor: Int, toColor: Int): Observable<Int> {
        val valueAnimator = ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor)
        valueAnimator.duration = 250 // milliseconds
        val observable = Observable.create<Int> { emitter -> valueAnimator.addUpdateListener { emitter.onNext(it.animatedValue as Int) } }
        return observable.doOnSubscribe { valueAnimator.start() }
    }

    enum class MenuOptions(private val info: String) {
        ADD("Add to favorites"),
        RANDOM("Random Color"),
        VIEW("View Favorites"),
        MORE_INFO("More Info"),
        SELECT_IMAGE("Pick a color from a picture");

        fun data(c: ColorApi) = when (this) {
            ADD -> "Add ${c.name?.value ?: c.hex?.value} to favorites"
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

    @Suppress("unused")
    private fun Any?.unit() = Unit

    private fun addToFavorites(colorApi: ColorApi) {
        val favorites = (defaultSharedPref.getObject("favorites", emptyList<ColorApi>()) ?: emptyList()).toMutableList()
        if (favorites.all { it.name?.value != colorApi.name?.value || it.hex?.value != colorApi.hex?.value }) favorites.add(colorApi)
            .also { Toast.makeText(this, "Added to Favorites", Toast.LENGTH_SHORT).show() }
        defaultSharedPref.edit().putObject("favorites", favorites).apply()
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
            }
        }
        .show().unit()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

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

    private lateinit var currentPhotoPath: String

    @SuppressLint("SimpleDateFormat")
    @Throws(IOException::class)
    private fun createImageFile(): File = File.createTempFile(
        "JPEG_${SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())}_",
        ".jpg",
        getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    ).apply { currentPhotoPath = absolutePath }

    private fun dispatchTakePictureIntent() = Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
        takePictureIntent.resolveActivity(packageManager)?.also {
            try {
                createImageFile()
            } catch (ex: IOException) {
                null
            }?.also {
                val photoURI: Uri = FileProvider.getUriForFile(this, "com.example.android.fileprovider", it)
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                startActivityForResult(takePictureIntent, takePhotoRequestID)
            }
        }
    }.unit()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_CANCELED) {
            when (requestCode) {
                takePhotoRequestID -> if (resultCode == Activity.RESULT_OK && data != null) imageShow(BitmapFactory.decodeFile(currentPhotoPath))
                pickPhotoRequestID -> if (resultCode == Activity.RESULT_OK && data?.data != null)
                    imageShow(BitmapFactory.decodeStream(contentResolver.openInputStream(data.data!!)))
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showFavorites() {
        val favorites = (defaultSharedPref.getObject<List<ColorApi>>("favorites", null) ?: emptyList()).toMutableList()
        val view = layoutInflater.inflate(R.layout.favorite_layout, null)
        val adapter = FavoriteAdapter(favorites)
        view.favTitle.text = "Favorites: ${favorites.size}"
        view.favoriteRV.adapter = adapter
        DragSwipeUtils.setDragSwipeUp(
            adapter, view.favoriteRV, Direction.UP + Direction.DOWN, Direction.START + Direction.END,
            object : DragSwipeActions<ColorApi, FavHolder> {
                override fun onSwiped(
                    viewHolder: RecyclerView.ViewHolder, direction: Direction, dragSwipeAdapter: DragSwipeAdapter<ColorApi, FavHolder>
                ) {
                    super.onSwiped(viewHolder, direction, dragSwipeAdapter)
                    view.favTitle.text = "Favorites: ${adapter.dataList.size}"
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
            name.text = "${position + 1}. ${item.name?.value ?: item.hex?.value}"
            itemView.setOnClickListener { randomColor(item.hex?.value) }
        }
    }

    class FavHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.colorName
    }
}