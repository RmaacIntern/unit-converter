@file:OptIn(ExperimentalMaterial3Api::class)

package com.aivigil.unitconverter.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aivigil.unitconverter.BuildConfig
import com.aivigil.unitconverter.R
import com.aivigil.unitconverter.data.SettingsRepository
import com.aivigil.unitconverter.data.UserSettings
import com.aivigil.unitconverter.domain.Category
import com.aivigil.unitconverter.domain.NumberFormatter
import com.aivigil.unitconverter.domain.NumberSymbols
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<UserSettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onDecimalPlacesChanged(places: Int) {
        viewModelScope.launch { repository.setDecimalPlaces(places) }
    }

    fun onDefaultCategoryChanged(category: Category) {
        viewModelScope.launch { repository.setDefaultCategory(category) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(SettingsRepository(checkNotNull(this[APPLICATION_KEY]))) }
        }
    }
}

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    privacyOptionsRequired: Boolean,
    onOpenPrivacyOptions: () -> Unit,
    bannerSlot: @Composable () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsScreen(
        settings = settings,
        onBack = onBack,
        onDecimalPlacesChanged = viewModel::onDecimalPlacesChanged,
        onDefaultCategoryChanged = viewModel::onDefaultCategoryChanged,
        privacyOptionsRequired = privacyOptionsRequired,
        onOpenPrivacyOptions = onOpenPrivacyOptions,
        bannerSlot = bannerSlot,
    )
}

@Composable
fun SettingsScreen(
    settings: UserSettings?,
    onBack: () -> Unit,
    onDecimalPlacesChanged: (Int) -> Unit,
    onDefaultCategoryChanged: (Category) -> Unit,
    privacyOptionsRequired: Boolean,
    onOpenPrivacyOptions: () -> Unit,
    bannerSlot: @Composable () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = bannerSlot,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        if (settings == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(vertical = 8.dp)) {
                SectionTitle(stringResource(R.string.settings_decimal_places))
                DecimalPlacesSetting(settings.decimalPlaces, onDecimalPlacesChanged)
                HorizontalDivider(Modifier.padding(vertical = 16.dp))

                SectionTitle(stringResource(R.string.settings_default_category))
                DefaultCategorySetting(settings.defaultCategory, onDefaultCategoryChanged)
                HorizontalDivider(Modifier.padding(vertical = 16.dp))

                SectionTitle(stringResource(R.string.about))
                AboutSection(privacyOptionsRequired, onOpenPrivacyOptions)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun DecimalPlacesSetting(current: Int, onChange: (Int) -> Unit) {
    // Local while dragging; persisted once, when the drag ends.
    var sliderValue by remember(current) { mutableFloatStateOf(current.toFloat()) }
    val places = sliderValue.roundToInt()
    val preview = remember(places) {
        NumberFormatter.fixed(PREVIEW_VALUE, places, NumberSymbols.forLocale(Locale.getDefault()))
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onChange(sliderValue.roundToInt()) },
                valueRange = NumberFormatter.MIN_DECIMALS.toFloat()..NumberFormatter.MAX_DECIMALS.toFloat(),
                steps = NumberFormatter.MAX_DECIMALS - NumberFormatter.MIN_DECIMALS - 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = places.toString(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 40.dp),
            )
        }
        Text(
            text = stringResource(R.string.settings_decimal_places_preview, preview),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DefaultCategorySetting(current: Category, onChange: (Category) -> Unit) {
    Column(Modifier.selectableGroup()) {
        Category.entries.forEach { category ->
            val isCurrent = category == current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(selected = isCurrent, onClick = { onChange(category) }, role = Role.RadioButton)
                    .padding(horizontal = 16.dp),
            ) {
                RadioButton(selected = isCurrent, onClick = null)
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun AboutSection(privacyOptionsRequired: Boolean, onOpenPrivacyOptions: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val policyUrl = stringResource(R.string.privacy_policy_url)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_rmaac_logo),
            contentDescription = stringResource(R.string.about_logo_description),
            modifier = Modifier.height(56.dp),
        )
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.about_program),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // No browser installed must not crash the app.
        TextButton(onClick = { runCatching { uriHandler.openUri(policyUrl) } }) {
            Text(stringResource(R.string.privacy_policy))
        }
        if (privacyOptionsRequired) {
            OutlinedButton(onClick = onOpenPrivacyOptions) {
                Text(stringResource(R.string.privacy_choices))
            }
        }
    }
}

private const val PREVIEW_VALUE = 1_234.567_891
