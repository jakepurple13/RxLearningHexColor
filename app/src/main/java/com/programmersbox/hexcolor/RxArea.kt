package com.programmersbox.hexcolor

import android.content.SharedPreferences
import android.graphics.Color
import com.programmersbox.gsonutils.getObject
import com.programmersbox.rxutils.invoke
import com.programmersbox.rxutils.ioMain
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject

class RxArea(
    private val sharedPrefs: SharedPreferences,
    backStream: Observable<Unit>,
    clearStream: Observable<Unit>,
    digitsStream: Observable<String>,
    disposables: CompositeDisposable = CompositeDisposable(),
    backgroundUpdate: PublishSubject<Int>,
    uiShow: PublishSubject<String>,
    colorApiSubject: BehaviorSubject<ColorApi>
) {
    private val hexStringSubject = BehaviorSubject.createDefault("#")
    private val apiSubject = BehaviorSubject.create<ColorApi>()
    private val currentHexValue get() = hexStringSubject.value ?: ""

    private fun errorColor(s: String = currentHexValue) = run {
        val rgb = Color.parseColor(s).valueOf()
        colorApiBlack.copy(
            name = null,
            hex = Hex(s, null),
            rgb = Rgb(r = rgb.first, g = rgb.second, b = rgb.third, fraction = null, value = "rgb(${rgb.first}, ${rgb.second}, ${rgb.third})")
        )
    }

    private fun getApiOrError(s: String): ColorApi? = try {
        getColorApi(s.drop(1))
    } catch (e: Exception) {
        errorColor(s)
    }

    init {
        hexStringSubject
            .ioMain()
            .map { s ->
                (if (s.length == 7)
                    sharedPrefs.getObject<List<ColorApi>>("favorites", null)?.find { it.hex?.value == s } ?: getApiOrError(s)
                else null) ?: colorApiBlack
            }
            .subscribe(apiSubject::invoke)
            .addTo(disposables)

        hexStringSubject
            .subscribe(uiShow::invoke)
            .addTo(disposables)

        apiSubject
            .map { i -> i.hex?.value?.let(Color::parseColor) ?: Color.BLACK }
            .subscribe(backgroundUpdate::invoke)
            .addTo(disposables)

        apiSubject
            .subscribe(colorApiSubject::invoke)
            .addTo(disposables)

        clearStream
            .map { "#" }
            .subscribe(hexStringSubject::onNext)
            .addTo(disposables)

        backStream
            .map { currentHexValue }
            .filter { it.length >= 2 }
            .map { it.substring(0, currentHexValue.lastIndex) }
            .subscribe(hexStringSubject::onNext)
            .addTo(disposables)

        digitsStream
            .map { it to currentHexValue }
            .filter { it.second.length < 7 }
            .map { currentHexValue + it.first }
            .subscribe(hexStringSubject::onNext)
            .addTo(disposables)
    }

    fun digitClicked(digit: String) = if (currentHexValue.length < 7) hexStringSubject.onNext(currentHexValue + digit) else Unit

}