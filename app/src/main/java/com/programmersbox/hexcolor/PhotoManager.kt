package com.programmersbox.hexcolor

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.palette.graphics.Palette
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.github.florent37.inlineactivityresult.rx.RxInlineActivityResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.programmersbox.helpfulutils.requestPermissions
import com.programmersbox.helpfulutils.whatIf
import com.programmersbox.rxutils.invoke
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.zoom_custom_title.view.*
import kotlinx.android.synthetic.main.zoom_layout.view.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class PhotoManager(
    private val imageGet: PublishSubject<Int>,
    private val activity: AppCompatActivity,
    private val disposables: CompositeDisposable = CompositeDisposable()
) {

    private val takePhotoRequestID = 5
    private val pickPhotoRequestID = takePhotoRequestID + 1
    private val pictureName get() = "JPEG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}_"

    @Suppress("DEPRECATION")
    private val folderPath = "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath}/HexColor"
    private val folderLocation get() = File(folderPath)
    private var currentPhotoPath: String? = null

    private val imageShow = PublishSubject.create<Bitmap>()

    init {
        imageShow
            .subscribe(this::getImagePixel)
            .addTo(disposables)
    }

    private enum class ImageOptions(val text: CharSequence) { TAKE_PHOTO("Take Photo"), GALLERY("Choose from Gallery"), CANCEL("Cancel") }

    fun selectImage() = MaterialAlertDialogBuilder(activity)
        .setTitle("Take a picture from")
        .setItems(ImageOptions.values().map(ImageOptions::text).toTypedArray()) { dialog, item ->
            when (ImageOptions.values()[item]) {
                ImageOptions.TAKE_PHOTO -> activity.requestPermissions(
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) { if (it.isGranted) dispatchTakePictureIntent() else activity.toast("Please accept the permissions in order to take a photo") }
                ImageOptions.GALLERY -> activity.requestPermissions(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) { if (it.isGranted) pickPhoto() else activity.toast("Please accept the permissions in order to pick a photo") }
                ImageOptions.CANCEL -> dialog.dismiss()
            }
        }
        .show().let { Unit }

    private fun pickPhoto() = RxInlineActivityResult(activity)
        .requestAsSingle(Intent(Intent.ACTION_PICK).apply { type = "image/*" })
        .doOnSuccess { onActivityResult(pickPhotoRequestID, it.resultCode, it.data) }
        .doOnError { activity.toast("Something went wrong") }
        .subscribe()
        .addTo(disposables)

    private fun dispatchTakePictureIntent() = Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
        takePictureIntent.resolveActivity(activity.packageManager)?.also {
            createImageFile()
                .subscribeBy(
                    onError = { activity.toast("Something went wrong, please try again") },
                    onSuccess = { startPictureIntent(it, takePictureIntent) }
                )
                .addTo(disposables)
        }
    }.let { Unit }

    @Throws(IOException::class)
    private fun createImageFile(): Single<File> = Single.create emitter@{ emitter ->
        try {
            emitter.onSuccess(File.createTempFile(pictureName, ".jpg", activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES)))
        } catch (e: IOException) {
            emitter.onError(e)
        }
    }

    private fun startPictureIntent(file: File, takePictureIntent: Intent) {
        currentPhotoPath = file.absolutePath
        val photoURI: Uri = FileProvider.getUriForFile(activity, "com.example.android.fileprovider", file)
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
        RxInlineActivityResult(activity)
            .requestAsSingle(takePictureIntent)
            .doOnSuccess { onActivityResult(takePhotoRequestID, it.resultCode, it.data) }
            .doOnError { activity.toast("Something went wrong") }
            .subscribe()
            .addTo(disposables)
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun getImagePixel(bitmap: Bitmap) {
        folderLocation.apply { if (!exists()) mkdirs() }
        val view = activity.layoutInflater.inflate(R.layout.zoom_layout, null)
        val gestureDetector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                if (view.zoomImage.isReady) {
                    val sCoord: PointF = view.zoomImage.viewToSourceCoord(e.x, e.y)!!
                    val pixel = bitmap.getPixel(sCoord.x.toInt(), sCoord.y.toInt())
                    imageGet(pixel)
                }
            }
        })

        if (bitmap.width > bitmap.height) view.zoomImage.orientation = SubsamplingScaleImageView.ORIENTATION_90
        view.zoomImage.setImage(ImageSource.bitmap(bitmap))
        view.zoomImage.setOnTouchListener { _, motionEvent -> gestureDetector.onTouchEvent(motionEvent) }

        val titleView = activity.layoutInflater.inflate(R.layout.zoom_custom_title, null)
        val palette = Palette.from(bitmap).generate()
        palette.dominantSwatch?.titleTextColor?.let { titleView.titleText.setTextColor(it) }
        palette.dominantSwatch?.rgb?.let { titleView.zoomTitleBackground.setBackgroundColor(it) }

        MaterialAlertDialogBuilder(activity)
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
        activity.toast("Photo Saved")
    }

    private fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_CANCELED) {
            when (requestCode) {
                takePhotoRequestID -> if (resultCode == Activity.RESULT_OK && data != null) imageShow(BitmapFactory.decodeFile(currentPhotoPath!!))
                pickPhotoRequestID -> if (resultCode == Activity.RESULT_OK && data?.data != null)
                    imageShow(BitmapFactory.decodeStream(activity.contentResolver.openInputStream(data.data!!)))
            }
        }
    }
}