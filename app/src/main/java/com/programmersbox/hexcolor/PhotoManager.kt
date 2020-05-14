package com.programmersbox.hexcolor

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.palette.graphics.Palette
import com.davemorrissey.labs.subscaleview.ImageSource
import com.github.florent37.inlineactivityresult.Result
import com.github.florent37.inlineactivityresult.rx.RxInlineActivityResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.programmersbox.helpfulutils.requestPermissions
import com.programmersbox.helpfulutils.setEnumItems
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

    private val translations: TranslationInterface by lazy { Translations(activity.resources) }

    init {
        imageShow
            .subscribe(this::getImagePixel)
            .addTo(disposables)
    }

    private enum class ImageOptions {
        TAKE_PHOTO, GALLERY, CANCEL;

        fun getText(translationInterface: TranslationInterface) = when (this) {
            TAKE_PHOTO -> translationInterface.takePhoto()
            GALLERY -> translationInterface.gallery()
            CANCEL -> translationInterface.cancel()
        }
    }

    fun selectImage(): AlertDialog = MaterialAlertDialogBuilder(activity)
        .setTitle(translations.photoFrom())
        .setEnumItems(ImageOptions.values().map { it.getText(translations) }.toTypedArray()) { item: ImageOptions, dialog ->
            when (item) {
                ImageOptions.TAKE_PHOTO -> activity.requestPermissions(
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) { if (it.isGranted) dispatchTakePictureIntent() else activity.toast(translations.photoTakePerm()) }
                ImageOptions.GALLERY -> activity.requestPermissions(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) { if (it.isGranted) pickPhoto() else activity.toast(translations.photoPickPerm()) }
                ImageOptions.CANCEL -> dialog.dismiss()
            }
        }
        .show()

    private fun onResult(requestCode: Int): (Result) -> Unit = { onActivityResult(requestCode, it.resultCode, it.data) }
    private val onError: (Throwable) -> Unit = { if (it is RxInlineActivityResult.Error) activity.toast(translations.wrong()) }

    private fun pickPhoto() = RxInlineActivityResult(activity)
        .requestAsSingle(Intent(Intent.ACTION_PICK).apply { type = "image/*" })
        .subscribe(onResult(pickPhotoRequestID), onError)
        .addTo(disposables)

    private fun dispatchTakePictureIntent() = Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
        takePictureIntent.resolveActivity(activity.packageManager)?.also {
            createImageFile()
                .subscribeBy(
                    onError = { activity.toast(translations.tryAgain()) },
                    onSuccess = { startPictureIntent(it, takePictureIntent) }
                )
                .addTo(disposables)
        }
    }

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
            .subscribe(onResult(takePhotoRequestID), onError)
            .addTo(disposables)
    }

    private fun Bitmap.rotate(degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun getImagePixel(bitmap: Bitmap) = MaterialAlertDialogBuilder(activity)
        .setCustomTitle(R.layout.zoom_custom_title) {
            Palette.from(bitmap).generate().dominantSwatch?.let {
                titleText.setTextColor(it.titleTextColor)
                zoomTitleBackground.setBackgroundColor(it.rgb)
            }
        }
        .setView(R.layout.zoom_layout) {
            val b = if (bitmap.width > bitmap.height) bitmap.rotate(90f) else bitmap
            val gestureDetector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent) {
                    if (zoomImage.isReady) {
                        try {
                            val sCoord: PointF = zoomImage.viewToSourceCoord(e.x, e.y)!!
                            val pixel = b.getPixel(sCoord.x.toInt(), sCoord.y.toInt())
                            imageGet(pixel)
                        } catch (e: Exception) {

                        }
                    }
                }
            })

            zoomImage.setImage(ImageSource.bitmap(b))
            zoomImage.setOnTouchListener { _, motionEvent -> gestureDetector.onTouchEvent(motionEvent) }
        }
        .setPositiveButton(translations.done()) { _, _ -> }
        .whatIf(currentPhotoPath?.isNotEmpty()) {
            setNeutralButton(translations.savePhoto()) { _, _ ->
                folderLocation.apply { if (!exists()) mkdirs() }
                bitmap.saveFile(File(folderPath, "$pictureName.jpg"))
            }
        }
        .setOnDismissListener { currentPhotoPath = null }
        .show().let { Unit }

    private fun Bitmap.saveFile(f: File) {
        if (!f.exists()) f.createNewFile()
        val stream = FileOutputStream(f)
        compress(Bitmap.CompressFormat.JPEG, 100, stream)
        stream.flush()
        stream.close()
        activity.toast(translations.photoSaved())
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