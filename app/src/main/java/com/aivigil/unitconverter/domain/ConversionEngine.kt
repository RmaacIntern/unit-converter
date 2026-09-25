package com.aivigil.unitconverter.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

enum class LowerBound { NonNegative, AbsoluteZero }

data class MeasureUnit(
    val id: String,
    val name: String,
    val symbol: String,
    val factorToBase: Double = 1.0,
    val toKelvin: ((Double) -> Double)? = null,
    val fromKelvin: ((Double) -> Double)? = null,
    val toKelvinFormula: String? = null,
    val fromKelvinFormula: String? = null,
)

enum class Category(
    val id: String,
    val displayName: String,
    val lowerBound: LowerBound,
    val defaultFromIndex: Int,
    val defaultToIndex: Int,
    val units: List<MeasureUnit>,
) {
    Length(
        id = "length",
        displayName = "Length",
        lowerBound = LowerBound.NonNegative,
        defaultFromIndex = 1, // km
        defaultToIndex = 4,   // mi
        units = listOf(
            MeasureUnit("length.m", "Meter", "m", 1.0),
            MeasureUnit("length.km", "Kilometre", "km", 1000.0),
            MeasureUnit("length.cm", "Centimetre", "cm", 0.01),
            MeasureUnit("length.mm", "Millimetre", "mm", 0.001),
            MeasureUnit("length.mi", "Mile", "mi", 1609.344),
            MeasureUnit("length.ft", "Foot", "ft", 0.3048),
        ),
    ),
    Weight(
        id = "weight",
        displayName = "Weight",
        lowerBound = LowerBound.NonNegative,
        defaultFromIndex = 0, // kg
        defaultToIndex = 3,   // lb
        units = listOf(
            MeasureUnit("weight.kg", "Kilogram", "kg", 1.0),
            MeasureUnit("weight.g", "Gram", "g", 0.001),
            MeasureUnit("weight.mg", "Milligram", "mg", 0.000001),
            MeasureUnit("weight.lb", "Pound", "lb", 0.45359237),
            MeasureUnit("weight.oz", "Ounce", "oz", 0.028349523125),
            MeasureUnit("weight.t", "Metric Ton", "t", 1000.0),
        ),
    ),
    Volume(
        id = "volume",
        displayName = "Volume",
        lowerBound = LowerBound.NonNegative,
        defaultFromIndex = 0, // l
        defaultToIndex = 3,   // gal_us
        units = listOf(
            MeasureUnit("volume.l", "Litre", "L", 1.0),
            MeasureUnit("volume.ml", "Millilitre", "mL", 0.001),
            MeasureUnit("volume.m3", "Cubic Metre", "m³", 1000.0),
            MeasureUnit("volume.gal_us", "US Gallon", "gal", 3.785411784),
            MeasureUnit("volume.qt_us", "US Quart", "qt", 0.946352946),
            MeasureUnit("volume.floz_us", "US Fluid Ounce", "fl oz", 0.0295735295625),
        ),
    ),
    Temperature(
        id = "temperature",
        displayName = "Temperature",
        lowerBound = LowerBound.AbsoluteZero,
        defaultFromIndex = 0, // c
        defaultToIndex = 1,   // f
        units = listOf(
            MeasureUnit(
                id = "temp.c", name = "Celsius", symbol = "°C",
                toKelvin = { it + 273.15 },
                fromKelvin = { it - 273.15 },
                toKelvinFormula = "K = °C + 273.15",
                fromKelvinFormula = "°C = K − 273.15",
            ),
            MeasureUnit(
                id = "temp.f", name = "Fahrenheit", symbol = "°F",
                toKelvin = { (it + 459.67) * 5.0 / 9.0 },
                fromKelvin = { it * 9.0 / 5.0 - 459.67 },
                toKelvinFormula = "K = (°F + 459.67) × 5/9",
                fromKelvinFormula = "°F = K × 9/5 − 459.67",
            ),
            MeasureUnit(
                id = "temp.k", name = "Kelvin", symbol = "K",
                toKelvin = { it },
                fromKelvin = { it },
                toKelvinFormula = null,
                fromKelvinFormula = null,
            ),
            MeasureUnit(
                id = "temp.r", name = "Rankine", symbol = "°R",
                toKelvin = { it * 5.0 / 9.0 },
                fromKelvin = { it * 9.0 / 5.0 },
                toKelvinFormula = "K = °R × 5/9",
                fromKelvinFormula = "°R = K × 9/5",
            ),
            MeasureUnit(
                id = "temp.re", name = "Réaumur", symbol = "°Ré",
                toKelvin = { it * 5.0 / 4.0 + 273.15 },
                fromKelvin = { (it - 273.15) * 4.0 / 5.0 },
                toKelvinFormula = "K = °Ré × 5/4 + 273.15",
                fromKelvinFormula = "°Ré = (K − 273.15) × 4/5",
            ),
            MeasureUnit(
                id = "temp.de", name = "Delisle", symbol = "°De",
                toKelvin = { 373.15 - it * 2.0 / 3.0 },
                fromKelvin = { (373.15 - it) * 3.0 / 2.0 },
                toKelvinFormula = "K = 373.15 − °De × 2/3",
                fromKelvinFormula = "°De = (373.15 − K) × 3/2",
            ),
        ),
    ),
    Area(
        id = "area",
        displayName = "Area",
        lowerBound = LowerBound.NonNegative,
        defaultFromIndex = 3, // ha
        defaultToIndex = 4,   // ac
        units = listOf(
            MeasureUnit("area.m2", "Square Metre", "m²", 1.0),
            MeasureUnit("area.km2", "Square Kilometre", "km²", 1_000_000.0),
            MeasureUnit("area.ha", "Hectare", "ha", 10_000.0),
            MeasureUnit("area.ac", "Acre", "ac", 4046.8564224),
            MeasureUnit("area.ft2", "Square Foot", "ft²", 0.09290304),
            MeasureUnit("area.mi2", "Square Mile", "mi²", 2589988.110336),
        ),
    ),
    Speed(
        id = "speed",
        displayName = "Speed",
        lowerBound = LowerBound.NonNegative,
        defaultFromIndex = 1, // kmh
        defaultToIndex = 2,   // mph
        units = listOf(
            MeasureUnit("speed.ms", "Metre per Second", "m/s", 1.0),
            MeasureUnit("speed.kmh", "Kilometre per Hour", "km/h", 1.0 / 3.6),
            MeasureUnit("speed.mph", "Mile per Hour", "mph", 0.44704),
            MeasureUnit("speed.kn", "Knot", "kn", 1.852 / 3.6),
            MeasureUnit("speed.fts", "Foot per Second", "ft/s", 0.3048),
            MeasureUnit("speed.mach", "Mach", "Ma", 340.29),
        ),
    );

    val defaultFrom: MeasureUnit get() = units[defaultFromIndex]
    val defaultTo: MeasureUnit get() = units[defaultToIndex]

    fun unitOrNull(id: String?): MeasureUnit? = units.firstOrNull { it.id == id }

    companion object {
        fun fromId(id: String?): Category? = entries.firstOrNull { it.id == id }
    }
}

sealed interface Evaluation {
    data object Blank : Evaluation
    data object Incomplete : Evaluation
    data class Invalid(val reason: InvalidReason) : Evaluation
    data class Success(val input: Double, val output: Double) : Evaluation
}

sealed interface InvalidReason {
    data object NotANumber : InvalidReason
    data object MultipleDecimalPoints : InvalidReason
    data class NegativeNotAllowed(val category: Category) : InvalidReason
    data class BeyondAbsoluteZero(val limit: Double, val unit: MeasureUnit, val limitIsMaximum: Boolean) : InvalidReason
    data object OutOfRange : InvalidReason
}

sealed interface Formula {
    data object SameUnit : Formula
    data class Factor(val oneFromInTo: Double) : Formula
    data class ViaKelvin(val toKelvin: String?, val fromKelvin: String?) : Formula
}

data class NumberSymbols(val decimal: Char, val grouping: Char) {
    companion object {
        val Default = NumberSymbols('.', ',')
        fun forLocale(locale: Locale): NumberSymbols {
            val dfs = DecimalFormatSymbols.getInstance(locale)
            return NumberSymbols(dfs.decimalSeparator, dfs.groupingSeparator)
        }
    }
}

object NumberFormatter {
    const val DEFAULT_DECIMALS = 4
    const val MIN_DECIMALS = 0
    const val MAX_DECIMALS = 6

    fun fixed(value: Double, decimals: Int, symbols: NumberSymbols = NumberSymbols.Default): String {
        val cl = decimals.coerceIn(MIN_DECIMALS, MAX_DECIMALS)
        val absVal = abs(value)
        val isZero = absVal == 0.0 || absVal < 1e-12
        if (!isZero && (absVal >= 1e15 || isSmallerThanPrecision(absVal, cl))) {
            return scientific(value, cl, symbols)
        }
        val bd = BigDecimal.valueOf(if (isZero) 0.0 else value).setScale(cl, RoundingMode.HALF_UP)
        return formatPlainString(bd.toPlainString(), symbols)
    }

    fun exact(value: Double, symbols: NumberSymbols = NumberSymbols.Default): String {
        val absVal = abs(value)
        val isZero = absVal == 0.0 || absVal < 1e-12
        if (isZero) return "0"
        val bd = BigDecimal.valueOf(value).stripTrailingZeros()
        return formatPlainString(bd.toPlainString(), symbols)
    }

    fun significant(value: Double, digits: Int = 10, symbols: NumberSymbols = NumberSymbols.Default): String {
        val bd = BigDecimal(value, MathContext(digits, RoundingMode.HALF_UP)).stripTrailingZeros()
        return formatPlainString(bd.toPlainString(), symbols)
    }

    private fun isSmallerThanPrecision(value: Double, decimals: Int): Boolean {
        if (decimals == 0) return value < 0.5
        val threshold = Math.pow(10.0, -decimals.toDouble()) / 2.0
        return value < threshold
    }

    private fun scientific(value: Double, decimals: Int, symbols: NumberSymbols): String {
        val expStr = String.format(Locale.US, "%.${decimals}E", value)
            .replace("E-0", "E-")
            .replace("E+0", "E")
            .replace("E+", "E")
        return expStr.replace('.', symbols.decimal)
    }

    private fun formatPlainString(plainStr: String, symbols: NumberSymbols): String {
        val isNeg = plainStr.startsWith("-")
        val clean = if (isNeg) plainStr.substring(1) else plainStr
        val parts = clean.split('.')
        val intPart = parts[0]
        val decPart = if (parts.size > 1) parts[1] else null

        val sb = StringBuilder()
        val len = intPart.length
        for (i in 0 until len) {
            if (i > 0 && (len - i) % 3 == 0) {
                sb.append(symbols.grouping)
            }
            sb.append(intPart[i])
        }
        val formattedInt = sb.toString()
        val result = if (decPart != null) "$formattedInt${symbols.decimal}$decPart" else formattedInt
        return if (isNeg && result != "0" && !result.startsWith("0.000000".substring(0, minOf(result.length, 8)))) "-$result" else result
    }
}

object ConversionEngine {
    const val MAX_INPUT_LENGTH = 30

    private val STRICT_REGEX = Regex("""^-?\d+([.,]\d+)?$|^-?[.,]\d+$|^-?\d+[.,]$""")

    fun convert(value: Double, from: MeasureUnit, to: MeasureUnit): Double {
        if (from == to) return value
        if (from.toKelvin != null && to.fromKelvin != null) {
            val kelvin = from.toKelvin.invoke(value)
            val result = to.fromKelvin.invoke(kelvin)
            return if (abs(result) < 1e-9) 0.0 else result
        }
        val base = value * from.factorToBase
        return base / to.factorToBase
    }

    fun evaluate(
        raw: String,
        category: Category,
        from: MeasureUnit,
        to: MeasureUnit,
        symbols: NumberSymbols = NumberSymbols.Default,
    ): Evaluation {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Evaluation.Blank
        if (trimmed == "-" || trimmed == "." || trimmed == "-." || trimmed == "," || trimmed == "-,") {
            return Evaluation.Incomplete
        }
        if (trimmed.length > MAX_INPUT_LENGTH) return Evaluation.Invalid(InvalidReason.OutOfRange)

        if (symbols.decimal == '.') {
            if (trimmed.contains(',')) return Evaluation.Invalid(InvalidReason.NotANumber)
        }

        val normalized = trimmed.replace(symbols.grouping.toString(), "")
            .replace(symbols.decimal, '.')

        if (trimmed.count { it == '.' || it == ',' || it == symbols.decimal } > 1) {
            return Evaluation.Invalid(InvalidReason.MultipleDecimalPoints)
        }

        if (!STRICT_REGEX.matches(normalized)) {
            return Evaluation.Invalid(InvalidReason.NotANumber)
        }

        val inputVal = normalized.toDoubleOrNull() ?: return Evaluation.Invalid(InvalidReason.OutOfRange)

        if (category.lowerBound == LowerBound.NonNegative) {
            if (inputVal < 0.0 && abs(inputVal) > 1e-12) {
                return Evaluation.Invalid(InvalidReason.NegativeNotAllowed(category))
            }
        }

        if (category.lowerBound == LowerBound.AbsoluteZero) {
            val kelvin = from.toKelvin?.invoke(inputVal) ?: inputVal
            if (kelvin < -1e-9) {
                val limit = absZeroLimit(from)
                return Evaluation.Invalid(
                    InvalidReason.BeyondAbsoluteZero(
                        limit = limit,
                        unit = from,
                        limitIsMaximum = from.id == "temp.de",
                    ),
                )
            }
        }

        val resultVal = convert(inputVal, from, to)
        if (resultVal.isInfinite() || resultVal.isNaN()) {
            return Evaluation.Invalid(InvalidReason.OutOfRange)
        }

        return Evaluation.Success(inputVal, resultVal)
    }

    fun formula(from: MeasureUnit, to: MeasureUnit): Formula {
        if (from == to) return Formula.SameUnit
        if (from.toKelvin != null && to.fromKelvin != null) {
            return Formula.ViaKelvin(from.toKelvinFormula, to.fromKelvinFormula)
        }
        val factor = from.factorToBase / to.factorToBase
        return Formula.Factor(factor)
    }

    private fun absZeroLimit(unit: MeasureUnit): Double = when (unit.id) {
        "temp.c" -> -273.15
        "temp.f" -> -459.67
        "temp.k" -> 0.0
        "temp.r" -> 0.0
        "temp.re" -> -218.52
        "temp.de" -> 559.725
        else -> 0.0
    }
}
