package com.programmersbox.hexcolor

import android.content.Context
import android.graphics.Color
import com.github.pwittchen.reactivenetwork.library.rx2.ReactiveNetwork
import com.github.pwittchen.reactivenetwork.library.rx2.internet.observing.InternetObservingSettings
import com.github.pwittchen.reactivenetwork.library.rx2.internet.observing.strategy.SocketInternetObservingStrategy
import com.programmersbox.rxutils.invoke
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject

class RxArea(
    private val context: Context,
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
            hex = Hex(s, s.drop(1)),
            rgb = Rgb(r = rgb.first, g = rgb.second, b = rgb.third, fraction = null, value = "rgb(${rgb.first}, ${rgb.second}, ${rgb.third})")
        )
    }

    private fun getApiOrError(s: String): ColorApi? = try {
        getColorApi(s.drop(1))
    } catch (e: Exception) {
        errorColor(s)
    }

    init {
        var connected = false
        ReactiveNetwork.observeInternetConnectivity(
            InternetObservingSettings.builder()
                .host("https://www.thecolorapi.com")
                .strategy(SocketInternetObservingStrategy())
                .build()
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { connected = it }
            .addTo(disposables)

        hexStringSubject
            .observeOn(Schedulers.io())
            .subscribeOn(AndroidSchedulers.mainThread())
            .map { s ->
                (if (s.length == 7) {
                    context.favoriteList?.find { it.hex?.value == s }
                        ?: context.history?.find { it.hex?.value == s }
                        ?: if (connected) getApiOrError(s) else errorColor(s)
                } else null) ?: colorApiBlack
            }
            .subscribe(apiSubject::invoke)
            .addTo(disposables)

        hexStringSubject
            .subscribe(uiShow::invoke)
            .addTo(disposables)

        apiSubject
            .distinctUntilChanged()
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
    fun getCurrentHex() = currentHexValue

}