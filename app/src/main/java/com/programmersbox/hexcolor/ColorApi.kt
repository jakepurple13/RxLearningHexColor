package com.programmersbox.hexcolor

import com.programmersbox.gsonutils.fromJson
import org.intellij.lang.annotations.Language

data class ColorApi(
    val hex: Hex?,
    val rgb: Rgb?,
    val hsl: Hsl?,
    val hsv: Hsv?,
    val name: Name?,
    val cmyk: Cmyk?,
    val XYZ: XYZ?,
    val image: Image?,
    val contrast: Contrast?,
    val _links: _links?,
    val _embedded: _embedded?
)

data class Cmyk(val fraction: Fraction?, val value: String?, val c: Number?, val m: Number?, val y: Number?, val k: Number?)

data class Contrast(val value: String?)

data class Fraction(val c: Number?, val m: Number?, val y: Number?, val k: Number?)

data class Hex(val value: String?, val clean: String?)

data class Hsl(val fraction: Fraction?, val h: Number?, val s: Number?, val l: Number?, val value: String?)

data class Hsv(val fraction: Fraction?, val value: String?, val h: Number?, val s: Number?, val v: Number?)

data class Image(val bare: String?, val named: String?)

data class Name(val value: String?, val closest_named_hex: String?, val exact_match_name: Boolean?, val distance: Number?)

data class Rgb(val fraction: Fraction?, val r: Number?, val g: Number?, val b: Number?, val value: String?)

data class Self(val href: String?)

data class XYZ(val fraction: Fraction?, val value: String?, val X: Number?, val Y: Number?, val Z: Number?)

class _embedded()

data class _links(val self: Self?)

@Language("JSON")
val colorApiBlack = """
    {
          "XYZ": {
            "X": 0,
            "Y": 0,
            "Z": 0,
            "fraction": {},
            "value": "XYZ(0, 0, 0)"
          },
          "_embedded": {},
          "_links": {
            "self": {
              "href": "/id?hex\u003d000000"
            }
          },
          "cmyk": {
            "fraction": {
              "k": 1
            },
            "k": 100,
            "value": "cmyk(NaN, NaN, NaN, 100)"
          },
          "contrast": {
            "value": "#ffffff"
          },
          "hex": {
            "clean": "000000",
            "value": "#000000"
          },
          "hsl": {
            "fraction": {},
            "h": 0,
            "l": 0,
            "s": 0,
            "value": "hsl(0, 0%, 0%)"
          },
          "hsv": {
            "fraction": {},
            "h": 0,
            "s": 0,
            "v": 0,
            "value": "hsv(0, 0%, 0%)"
          },
          "image": {
            "bare": "http://www.thecolorapi.com/id?format\u003dsvg\u0026named\u003dfalse\u0026hex\u003d000000",
            "named": "http://www.thecolorapi.com/id?format\u003dsvg\u0026hex\u003d000000"
          },
          "name": {
            "closest_named_hex": "#000000",
            "distance": 0,
            "exact_match_name": true,
            "value": "Black"
          },
          "rgb": {
            "b": 0,
            "fraction": {},
            "g": 0,
            "r": 0,
            "value": "rgb(0, 0, 0)"
          }
        }

""".trimIndent().fromJson<ColorApi>()!!