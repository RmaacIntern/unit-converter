package com.aivigil.unitconverter.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aivigil.unitconverter.data.SettingsRepository
import com.aivigil.unitconverter.data.UserSettings
import com.aivigil.unitconverter.domain.Category
import com.aivigil.unitconverter.domain.ConversionEngine
import com.aivigil.unitconverter.domain.Evaluation
import com.aivigil.unitconverter.domain.Formula
import com.aivigil.unitconverter.domain.InvalidReason
import com.aivigil.unitconverter.domain.LowerBound
import com.aivigil.unitconverter.domain.MeasureUnit
import com.aivigil.unitconverter.domain.NumberFormatter
import com.aivigil.unitconverter.domain.NumberSymbols
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// UI model — the four Main-screen states (DESIGN-STANDARD §1.3)
// ─────────────────────────────────────────────────────────────────────────────

sealed interface MainUiState {
    /** DataStore settings have not yet been read. No controls are shown. */
    data object Loading : MainUiState

    sealed interface Ready : MainUiState {
        val controls: ControlsUi
    }

    /** Blank input, an in-progress token ("-", ".", "-."), or valid value not yet converted. */
    data class Empty(override val controls: ControlsUi, val hint: EmptyHint) : Ready

    /** A completed conversion: the result card is filled. */
    data class Content(override val controls: ControlsUi, val result: ResultUi) : Ready

    /** The input cannot be converted. The field and result card both show the error. */
    data class Invalid(
        override val controls: ControlsUi,
        val reason: InvalidReason,
        /** Formatted limit for the absolute-zero error message, e.g. "-273.15 °C". */
        val limitText: String?,
    ) : Ready
}

enum class EmptyHint { EnterValue, TapConvert }

data class ControlsUi(
    val category: Category,
    val from: MeasureUnit,
    val to: MeasureUnit,
    val input: String,
    val canConvert: Boolean,
    /** True for Temperature only: decimal keyboards often have no minus key. */
    val allowsNegative: Boolean,
)

data class ResultUi(
    val inputText: String,
    val outputText: String,
    val formulaLines: List<String>,
)

// ─────────────────────────────────────────────────────────────────────────────
// One-shot event (the only interstitial trigger)
// ─────────────────────────────────────────────────────────────────────────────

sealed interface MainEvent {
    data object ConversionCompleted : MainEvent
    /** Fired whenever the user switches category, from-unit, to-unit, or swaps. */
    data object UnitChanged : MainEvent
}

// ─────────────────────────────────────────────────────────────────────────────
// Form state — persisted in SavedStateHandle (rotation + process death)
// ─────────────────────────────────────────────────────────────────────────────

internal data class FormState(
    val categoryId: String? = null,
    val fromId: String? = null,
    val toId: String? = null,
    val input: String = "",
    val converted: Boolean = false,
) {
    fun saveTo(handle: SavedStateHandle) {
        handle[KEY_CATEGORY] = categoryId
        handle[KEY_FROM] = fromId
        handle[KEY_TO] = toId
        handle[KEY_INPUT] = input
        handle[KEY_CONVERTED] = converted
    }

    companion object {
        private const val KEY_CATEGORY = "form.category"
        private const val KEY_FROM = "form.from"
        private const val KEY_TO = "form.to"
        private const val KEY_INPUT = "form.input"
        private const val KEY_CONVERTED = "form.converted"

        fun restoreFrom(handle: SavedStateHandle) = FormState(
            categoryId = handle[KEY_CATEGORY],
            fromId = handle[KEY_FROM],
            toId = handle[KEY_TO],
            input = handle[KEY_INPUT] ?: "",
            converted = handle[KEY_CONVERTED] ?: false,
        )
    }
}

internal data class Selection(val category: Category, val from: MeasureUnit, val to: MeasureUnit)

internal fun resolveSelection(form: FormState, settings: UserSettings): Selection {
    val category = Category.fromId(form.categoryId) ?: settings.defaultCategory
    return Selection(
        category = category,
        from = category.unitOrNull(form.fromId) ?: category.defaultFrom,
        to = category.unitOrNull(form.toId) ?: category.defaultTo,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Pure reducer — unit-tested in ConversionStateTest
// ─────────────────────────────────────────────────────────────────────────────

internal fun reduceMainState(
    form: FormState,
    settings: UserSettings?,
    symbols: NumberSymbols,
): MainUiState {
    if (settings == null) return MainUiState.Loading
    val selection = resolveSelection(form, settings)
    val evaluation = ConversionEngine.evaluate(
        raw = form.input,
        category = selection.category,
        from = selection.from,
        to = selection.to,
        symbols = symbols,
    )
    val controls = ControlsUi(
        category = selection.category,
        from = selection.from,
        to = selection.to,
        input = form.input,
        canConvert = evaluation is Evaluation.Success && !form.converted,
        allowsNegative = selection.category.lowerBound == LowerBound.AbsoluteZero,
    )
    return when (evaluation) {
        Evaluation.Blank, Evaluation.Incomplete ->
            MainUiState.Empty(controls, EmptyHint.EnterValue)

        is Evaluation.Invalid ->
            MainUiState.Invalid(
                controls = controls,
                reason = evaluation.reason,
                limitText = (evaluation.reason as? InvalidReason.BeyondAbsoluteZero)?.let {
                    "${NumberFormatter.significant(it.limit, symbols = symbols)} ${it.unit.symbol}"
                },
            )

        is Evaluation.Success ->
            if (!form.converted) {
                MainUiState.Empty(controls, EmptyHint.TapConvert)
            } else {
                MainUiState.Content(
                    controls = controls,
                    result = ResultUi(
                        inputText = NumberFormatter.exact(evaluation.input, symbols),
                        outputText = NumberFormatter.fixed(
                            evaluation.output,
                            settings.decimalPlaces,
                            symbols,
                        ),
                        formulaLines = buildFormulaLines(selection.from, selection.to, symbols),
                    ),
                )
            }
    }
}

private fun buildFormulaLines(
    from: MeasureUnit,
    to: MeasureUnit,
    symbols: NumberSymbols,
): List<String> = when (val formula = ConversionEngine.formula(from, to)) {
    Formula.SameUnit -> listOf("1 ${from.symbol} = 1 ${to.symbol}")
    is Formula.Factor -> listOf(
        "1 ${from.symbol} = ${NumberFormatter.significant(formula.oneFromInTo, symbols = symbols)} ${to.symbol}",
    )
    is Formula.ViaKelvin -> listOfNotNull(formula.toKelvin, formula.fromKelvin)
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class ConversionViewModel(
    private val savedState: SavedStateHandle,
    settingsRepository: SettingsRepository,
    private val symbols: () -> NumberSymbols = {
        NumberSymbols.forLocale(Locale.getDefault())
    },
) : ViewModel() {

    private val form = MutableStateFlow(FormState.restoreFrom(savedState))

    private val settings: StateFlow<UserSettings?> =
        settingsRepository.settings.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            initialValue = null,
        )

    val uiState: StateFlow<MainUiState> =
        combine(form, settings) { formState, userSettings ->
            reduceMainState(formState, userSettings, symbols())
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            MainUiState.Loading,
        )

    private val _events = Channel<MainEvent>(Channel.BUFFERED)
    val events: Flow<MainEvent> = _events.receiveAsFlow()

    // ── User actions ─────────────────────────────────────────────────────────

    fun onCategorySelected(category: Category) {
        var changed = false
        mutate { draft, selection ->
            if (category == selection.category) draft
            else { changed = true; draft.copy(categoryId = category.id, fromId = category.defaultFrom.id, toId = category.defaultTo.id, converted = false) }
        }
        if (changed) _events.trySend(MainEvent.UnitChanged)
    }

    /** Picking the unit on the other side swaps them. A shown result stays live. */
    fun onFromUnitSelected(unit: MeasureUnit) {
        var changed = false
        mutate { draft, selection ->
            when (unit) {
                selection.from -> draft
                selection.to -> { changed = true; draft.copy(fromId = selection.to.id, toId = selection.from.id) }
                in selection.category.units -> { changed = true; draft.copy(fromId = unit.id) }
                else -> draft
            }
        }
        if (changed) _events.trySend(MainEvent.UnitChanged)
    }

    fun onToUnitSelected(unit: MeasureUnit) {
        var changed = false
        mutate { draft, selection ->
            when (unit) {
                selection.to -> draft
                selection.from -> { changed = true; draft.copy(fromId = selection.to.id, toId = selection.from.id) }
                in selection.category.units -> { changed = true; draft.copy(toId = unit.id) }
                else -> draft
            }
        }
        if (changed) _events.trySend(MainEvent.UnitChanged)
    }

    fun onSwapUnits() {
        _events.trySend(MainEvent.UnitChanged)
        mutate { draft, selection ->
            draft.copy(fromId = selection.to.id, toId = selection.from.id)
        }
    }

    fun onInputChanged(text: String) = mutate { draft, _ ->
        when {
            text == draft.input -> draft
            text.length > ConversionEngine.MAX_INPUT_LENGTH -> draft
            else -> draft.copy(input = text, converted = false)
        }
    }

    fun onToggleSign() = mutate { draft, _ ->
        val toggled = if (draft.input.startsWith("-")) {
            draft.input.removePrefix("-")
        } else {
            "-${draft.input}"
        }
        if (toggled == "-0") draft.copy(input = "0", converted = false)
        else if (toggled.length > ConversionEngine.MAX_INPUT_LENGTH) draft
        else draft.copy(input = toggled, converted = false)
    }

    fun onClear() = mutate { draft, _ -> draft.copy(input = "", converted = false) }

    fun onConvert() {
        var emitEvent = false
        mutate { draft, selection ->
            if (draft.converted) return@mutate draft
            val evaluation = ConversionEngine.evaluate(
                raw = draft.input,
                category = selection.category,
                from = selection.from,
                to = selection.to,
                symbols = symbols(),
            )
            if (evaluation is Evaluation.Success) {
                emitEvent = true
                draft.copy(converted = true)
            } else {
                draft
            }
        }
        if (emitEvent) _events.trySend(MainEvent.ConversionCompleted)
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Applies a mutation against the resolved selection.
     *
     * On the first user interaction the resolved category and units are pinned into
     * [FormState], so a later change to the default category in Settings never
     * disrupts a mid-task conversion.
     */
    private inline fun mutate(transform: (FormState, Selection) -> FormState) {
        val currentSettings = settings.value ?: return
        val current = form.value
        val selection = resolveSelection(current, currentSettings)
        val pinned = current.copy(
            categoryId = selection.category.id,
            fromId = selection.from.id,
            toId = selection.to.id,
        )
        val next = transform(pinned, selection)
        if (next != current) {
            form.value = next
            next.saveTo(savedState)
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])
                ConversionViewModel(
                    savedState = createSavedStateHandle(),
                    settingsRepository = SettingsRepository(application),
                )
            }
        }
    }
}