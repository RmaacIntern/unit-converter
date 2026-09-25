package com.aivigil.unitconverter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversionEngineTest {

    private val allUnits = Category.entries.flatMap { it.units }
    private fun unit(id: String): MeasureUnit = allUnits.single { it.id == id }
    private fun convert(value: Double, from: String, to: String) = ConversionEngine.convert(value, unit(from), unit(to))

    private fun evaluate(
        raw: String,
        category: Category,
        from: String,
        to: String,
        symbols: NumberSymbols = NumberSymbols.Default,
    ) = ConversionEngine.evaluate(raw, category, unit(from), unit(to), symbols)

    private fun output(raw: String, category: Category, from: String, to: String) =
        (evaluate(raw, category, from, to) as Evaluation.Success).output

    private fun reason(raw: String, category: Category, from: String, to: String) =
        (evaluate(raw, category, from, to) as Evaluation.Invalid).reason

    // --- Catalogue ------------------------------------------------------------------------------

    @Test
    fun `six categories with six units each and 36 unique ids`() {
        assertEquals(6, Category.entries.size)
        Category.entries.forEach { assertEquals(it.displayName, 6, it.units.size) }
        assertEquals(36, allUnits.map { it.id }.toSet().size)
    }

    @Test
    fun `every category opens on two different units`() {
        Category.entries.forEach { assertNotEquals(it.displayName, it.defaultFrom, it.defaultTo) }
    }

    // --- Linear categories ------------------------------------------------------------------------

    @Test
    fun `linear reference values`() {
        assertEquals(1.609344, convert(1.0, "length.mi", "length.km"), 1e-12)
        assertEquals(30.48, convert(1.0, "length.ft", "length.cm"), 1e-12)
        assertEquals(453.59237, convert(1.0, "weight.lb", "weight.g"), 1e-9)
        assertEquals(16.0, convert(1.0, "weight.lb", "weight.oz"), 1e-12)
        assertEquals(3.785411784, convert(1.0, "volume.gal_us", "volume.l"), 1e-12)
        assertEquals(128.0, convert(1.0, "volume.gal_us", "volume.floz_us"), 1e-9)
        assertEquals(43_560.0, convert(1.0, "area.ac", "area.ft2"), 1e-6)
        assertEquals(640.0, convert(1.0, "area.mi2", "area.ac"), 1e-9)
        assertEquals(62.137119223733, convert(100.0, "speed.kmh", "speed.mph"), 1e-9)
        assertEquals(1.852, convert(1.0, "speed.kn", "speed.kmh"), 1e-12)
    }

    @Test
    fun `every linear pair round-trips`() {
        Category.entries.filter { it.lowerBound == LowerBound.NonNegative }.forEach { category ->
            for (a in category.units) for (b in category.units) {
                val back = ConversionEngine.convert(ConversionEngine.convert(123.456, a, b), b, a)
                assertEquals("$a -> $b -> $a", 123.456, back, 1e-9)
            }
        }
    }

    // --- Temperature: function pairs through Kelvin -----------------------------------------------

    @Test
    fun `temperature fixed points on all six scales`() {
        assertEquals(212.0, convert(100.0, "temp.c", "temp.f"), 1e-9)
        assertEquals(32.0, convert(0.0, "temp.c", "temp.f"), 1e-9)
        assertEquals(-40.0, convert(-40.0, "temp.c", "temp.f"), 1e-9)
        assertEquals(273.15, convert(0.0, "temp.c", "temp.k"), 1e-9)
        assertEquals(491.67, convert(32.0, "temp.f", "temp.r"), 1e-9)
        assertEquals(80.0, convert(100.0, "temp.c", "temp.re"), 1e-9)
        assertEquals(150.0, convert(0.0, "temp.c", "temp.de"), 1e-9)
        assertEquals(0.0, convert(100.0, "temp.c", "temp.de"), 1e-9)
        assertEquals(559.725, convert(0.0, "temp.k", "temp.de"), 1e-9)
        assertEquals(0.0, convert(-459.67, "temp.f", "temp.k"), 0.0)
    }

    @Test
    fun `temperature is not a multiplier`() {
        // A factor-only engine would make 20 °C exactly twice 10 °C in °F.
        assertNotEquals(2 * convert(10.0, "temp.c", "temp.f"), convert(20.0, "temp.c", "temp.f"), 1e-9)
    }

    @Test
    fun `all 36 temperature pairs round-trip and agree via any intermediate scale`() {
        val scales = Category.Temperature.units
        val samples = listOf(-40.0, 0.0, 36.6, 100.0, 1_000.0)
        for (a in scales) for (b in scales) for (x in samples) {
            val direct = ConversionEngine.convert(x, a, b)
            assertEquals("$a -> $b -> $a at $x", x, ConversionEngine.convert(direct, b, a), 1e-9)
            for (via in scales) {
                val twoStep = ConversionEngine.convert(ConversionEngine.convert(x, a, via), via, b)
                assertEquals("$a -> $via -> $b at $x", direct, twoStep, 1e-9)
            }
        }
    }

    @Test
    fun `absolute zero itself is valid on every scale and shows as exactly 0 K`() {
        mapOf(
            "temp.c" to "-273.15", "temp.f" to "-459.67", "temp.k" to "0",
            "temp.r" to "0", "temp.re" to "-218.52", "temp.de" to "559.725",
        ).forEach { (id, zero) ->
            assertEquals(id, 0.0, output(zero, Category.Temperature, id, "temp.k"), 0.0)
        }
    }

    @Test
    fun `below absolute zero is rejected with the scale's own limit`() {
        val celsius = reason("-273.16", Category.Temperature, "temp.c", "temp.f") as InvalidReason.BeyondAbsoluteZero
        assertEquals(-273.15, celsius.limit, 1e-9)
        assertFalse(celsius.limitIsMaximum)
        assertTrue(evaluate("-0.001", Category.Temperature, "temp.k", "temp.c") is Evaluation.Invalid)
        assertTrue(evaluate("-1", Category.Temperature, "temp.r", "temp.c") is Evaluation.Invalid)
    }

    @Test
    fun `Delisle runs backwards so its limit is a maximum`() {
        val delisle = reason("560", Category.Temperature, "temp.de", "temp.c") as InvalidReason.BeyondAbsoluteZero
        assertEquals(559.725, delisle.limit, 1e-9)
        assertTrue(delisle.limitIsMaximum)
        // Negative Delisle is hotter than boiling water: unusual, but physically fine.
        assertTrue(evaluate("-100", Category.Temperature, "temp.de", "temp.c") is Evaluation.Success)
    }

    @Test
    fun `negative temperatures above absolute zero convert`() {
        assertEquals(-40.0, output("-40", Category.Temperature, "temp.c", "temp.f"), 1e-9)
    }

    // --- Parsing and validation ---------------------------------------------------------------------

    @Test
    fun `blank and half-typed input is not an error`() {
        assertEquals(Evaluation.Blank, evaluate("", Category.Length, "length.m", "length.km"))
        assertEquals(Evaluation.Blank, evaluate("   ", Category.Length, "length.m", "length.km"))
        listOf("-", ".", "-.").forEach {
            assertEquals(it, Evaluation.Incomplete, evaluate(it, Category.Temperature, "temp.c", "temp.f"))
        }
    }

    @Test
    fun `malformed numbers get a specific reason`() {
        assertEquals(InvalidReason.MultipleDecimalPoints, reason("1.2.3", Category.Length, "length.m", "length.km"))
        listOf("1e5", "NaN", "Infinity", "0x1A", "12abc", "1,5", "1 000", "+5", "--5", "1.0f").forEach {
            assertEquals(it, InvalidReason.NotANumber, reason(it, Category.Length, "length.m", "length.km"))
        }
    }

    @Test
    fun `leading and trailing decimal points parse`() {
        assertEquals(5.0, (evaluate("5.", Category.Length, "length.m", "length.km") as Evaluation.Success).input, 0.0)
        assertEquals(0.5, (evaluate(".5", Category.Length, "length.m", "length.km") as Evaluation.Success).input, 0.0)
    }

    @Test
    fun `comma is a decimal point only where the locale uses one`() {
        val german = NumberSymbols(decimal = ',', grouping = '.')
        val ok = evaluate("1,5", Category.Length, "length.m", "length.km", german) as Evaluation.Success
        assertEquals(1.5, ok.input, 0.0)
        assertTrue(evaluate("1,5", Category.Length, "length.m", "length.km") is Evaluation.Invalid)
    }

    @Test
    fun `magnitudes cannot be negative but minus zero is zero`() {
        assertEquals(
            InvalidReason.NegativeNotAllowed(Category.Length),
            reason("-1", Category.Length, "length.m", "length.km"),
        )
        assertEquals(0.0, output("-0", Category.Length, "length.m", "length.km"), 0.0)
    }

    @Test
    fun `overflow is reported and never returned as Infinity`() {
        assertEquals(InvalidReason.OutOfRange, reason("9".repeat(400), Category.Length, "length.m", "length.km"))
        assertEquals(InvalidReason.OutOfRange, reason("1" + "0".repeat(305), Category.Area, "area.mi2", "area.m2"))
    }

    // --- Formatting -----------------------------------------------------------------------------------

    @Test
    fun `fixed formatting applies 0 to 6 places`() {
        assertEquals("1,234,568", NumberFormatter.fixed(1_234_567.891, 0))
        assertEquals("1,234,567.9", NumberFormatter.fixed(1_234_567.891, 1))
        assertEquals("2.68", NumberFormatter.fixed(2.675, 2)) // rounds the decimal the user sees
        assertEquals("0.000000", NumberFormatter.fixed(0.0, 6))
        assertEquals("1.234568", NumberFormatter.fixed(1.23456789, 9)) // clamped to 6
        assertEquals("1", NumberFormatter.fixed(1.2, -3)) // clamped to 0
    }

    @Test
    fun `values the chosen precision would hide switch to scientific notation`() {
        assertEquals("3.86E-7", NumberFormatter.fixed(3.861021585e-7, 2))
        assertEquals("-1.000E-7", NumberFormatter.fixed(-1e-7, 3))
        assertEquals("1.00E20", NumberFormatter.fixed(1e20, 2))
    }

    @Test
    fun `separators follow the supplied locale symbols`() {
        assertEquals("1.234,50", NumberFormatter.fixed(1_234.5, 2, NumberSymbols(decimal = ',', grouping = '.')))
    }

    @Test
    fun `negative zero never shows a sign`() {
        assertEquals("0.00", NumberFormatter.fixed(-0.0, 2))
        assertEquals("0", NumberFormatter.exact(-0.0))
    }

    @Test
    fun `significant and exact formatting`() {
        assertEquals("0.6213711922", NumberFormatter.significant(1 / 1.609344))
        assertEquals("559.725", NumberFormatter.significant(559.725))
        assertEquals("1,234.5", NumberFormatter.exact(1_234.5))
        assertEquals("0.00001", NumberFormatter.exact(0.00001))
    }

    // --- Formula summary ------------------------------------------------------------------------------

    @Test
    fun `formula summary`() {
        val factor = ConversionEngine.formula(unit("length.km"), unit("length.mi")) as Formula.Factor
        assertEquals(0.621371192237334, factor.oneFromInTo, 1e-12)

        val viaKelvin = ConversionEngine.formula(unit("temp.c"), unit("temp.f")) as Formula.ViaKelvin
        assertEquals("K = °C + 273.15", viaKelvin.toKelvin)
        assertEquals("°F = K × 9/5 − 459.67", viaKelvin.fromKelvin)

        assertNull((ConversionEngine.formula(unit("temp.k"), unit("temp.c")) as Formula.ViaKelvin).toKelvin)
        assertEquals(Formula.SameUnit, ConversionEngine.formula(unit("length.m"), unit("length.m")))
    }
}
