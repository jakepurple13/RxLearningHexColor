package com.programmersbox.hexcolor

import android.content.res.Resources

class Translations(private val res: Resources) : TranslationInterface {
    override fun favorite(amount: Int) = res.getQuantityString(R.plurals.favoriteWord, amount)
    override fun permissions(perms: String) = res.getString(R.string.permissions, perms)
    override fun history() = res.getString(R.string.history)
    override fun addToFavorite() = res.getString(R.string.addToFavorite)
    override fun removeFromFavorites() = res.getString(R.string.removeFromFavorites)
    override fun addedFavorite() = res.getString(R.string.addedFavorite)
    override fun removedFavorite() = res.getString(R.string.removedFavorite)
    override fun done() = res.getString(R.string.done)

    override fun takePhoto() = res.getString(R.string.take_photo)
    override fun gallery() = res.getString(R.string.gallery)
    override fun cancel() = res.getString(R.string.cancel)
    override fun wrong() = res.getString(R.string.wrong)
    override fun tryAgain() = res.getString(R.string.tryAgain)
    override fun photoTakePerm() = res.getString(R.string.photoTakePerm)
    override fun photoPickPerm() = res.getString(R.string.photoPickPerm)
    override fun savePhoto() = res.getString(R.string.savePhoto)
    override fun photoSaved() = res.getString(R.string.photoSaved)
    override fun photoFrom(): String = res.getString(R.string.photoFrom)
}

interface TranslationInterface {
    fun favorite(amount: Int): String
    fun permissions(perms: String): String
    fun history(): String
    fun addToFavorite(): String
    fun removeFromFavorites(): String
    fun addedFavorite(): String
    fun removedFavorite(): String
    fun done(): String

    fun takePhoto(): String
    fun gallery(): String
    fun cancel(): String
    fun wrong(): String
    fun tryAgain(): String
    fun photoTakePerm(): String
    fun photoPickPerm(): String
    fun savePhoto(): String
    fun photoSaved(): String
    fun photoFrom(): String
}