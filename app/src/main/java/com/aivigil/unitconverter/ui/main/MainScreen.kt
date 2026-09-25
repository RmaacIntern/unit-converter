@file:OptIn(ExperimentalMaterial3Api::class)

package com.aivigil.unitconverter.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SquareFoot
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aivigil.unitconverter.R
import com.aivigil.unitconverter.domain.Category
import com.aivigil.unitconverter.domain.InvalidReason
import com.aivigil.unitconverter.domain.MeasureUnit
import com.aivigil.unitconverter.ui.theme.AccentAmber
import com.aivigil.unitconverter.ui.theme.AccentEmerald
import com.aivigil.unitconverter.ui.theme.AccentIndigo
import com.aivigil.unitconverter.ui.theme.AccentRose
import com.aivigil.unitconverter.ui.theme.BgDarkVoid
import com.aivigil.unitconverter.ui.theme.BorderCyan
import com.aivigil.unitconverter.ui.theme.BorderPurple
import com.aivigil.unitconverter.ui.theme.BorderSubtle
import com.aivigil.unitconverter.ui.theme.ElectricCyan
import com.aivigil.unitconverter.ui.theme.NeonPurple
import com.aivigil.unitconverter.ui.theme.NeonPurpleLight
import com.aivigil.unitconverter.ui.theme.SurfaceCard
import com.aivigil.unitconverter.ui.theme.SurfaceInput

data class MainActions(
    val onCategorySelected: (Category) -> Unit,
    val onFromUnitSelected: (MeasureUnit) -> Unit,
    val onToUnitSelected: (MeasureUnit) -> Unit,
    val onSwapUnits: () -> Unit,
    val onInputChanged: (String) -> Unit,
    val onToggleSign: () -> Unit,
    val onClear: () -> Unit,
    val onConvert: () -> Unit,
)

private enum class Pane { Splash, Home, Convert }

@Composable
fun MainRoute(
    onOpenSettings: () -> Unit,
    onConversionCompleted: () -> Unit,
    onUnitChanged: () -> Unit,
    onExitApp: () -> Unit,
    bannerSlot: @Composable () -> Unit,
    viewModel: ConversionViewModel = viewModel(factory = ConversionViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestCallback by rememberUpdatedState(onConversionCompleted)
    val latestUnitChangedCallback by rememberUpdatedState(onUnitChanged)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    MainEvent.ConversionCompleted -> latestCallback()
                    MainEvent.UnitChanged -> latestUnitChangedCallback()
                }
            }
        }
    }

    val actions = remember(viewModel) {
        MainActions(
            onCategorySelected = viewModel::onCategorySelected,
            onFromUnitSelected = viewModel::onFromUnitSelected,
            onToUnitSelected = viewModel::onToUnitSelected,
            onSwapUnits = viewModel::onSwapUnits,
            onInputChanged = viewModel::onInputChanged,
            onToggleSign = viewModel::onToggleSign,
            onClear = viewModel::onClear,
            onConvert = viewModel::onConvert,
        )
    }

    MainScreen(
        state = state,
        actions = actions,
        onOpenSettings = onOpenSettings,
        onExitApp = onExitApp,
        bannerSlot = bannerSlot,
    )
}

@Composable
fun MainScreen(
    state: MainUiState,
    actions: MainActions,
    onOpenSettings: () -> Unit,
    onExitApp: () -> Unit,
    bannerSlot: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pane by rememberSaveable { mutableStateOf(Pane.Splash) }
    val scope = rememberCoroutineScope()
    val exitPrompt = rememberExitPromptController(scope)

    BackHandler(enabled = pane == Pane.Convert) { pane = Pane.Home }
    // Splash is entry-only: back from Home leaves the app rather than reopening it,
    // so the exit prompt is the last thing between the user and closing the app.
    BackHandler(enabled = pane == Pane.Home || pane == Pane.Splash) { exitPrompt.show() }

    ExitConfirmDialog(controller = exitPrompt, onConfirmExit = onExitApp)

    val categoryName = when (state) {
        is MainUiState.Ready -> state.controls.category.displayName
        else -> ""
    }

    when (pane) {
        Pane.Splash -> SplashScreen(onEnter = { pane = Pane.Home })

        Pane.Home -> Scaffold(
            modifier = modifier,
            containerColor = BgDarkVoid,
            bottomBar = bannerSlot,
        ) { innerPadding ->
            when (state) {
                MainUiState.Loading -> LoadingBody(Modifier.fillMaxSize().padding(innerPadding))
                is MainUiState.Ready -> HomeGrid(
                    selected = state.controls.category,
                    onCategory = { cat ->
                        actions.onCategorySelected(cat)
                        pane = Pane.Convert
                    },
                    onSettings = onOpenSettings,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }

        Pane.Convert -> Scaffold(
            modifier = modifier,
            containerColor = BgDarkVoid,
            topBar = {
                NeonTopBar(
                    title = categoryName,
                    onBack = { pane = Pane.Home },
                    onSettings = onOpenSettings,
                )
            },
            bottomBar = bannerSlot,
        ) { innerPadding ->
            when (state) {
                MainUiState.Loading -> LoadingBody(Modifier.fillMaxSize().padding(innerPadding))
                is MainUiState.Ready -> ConvertPane(
                    state = state,
                    actions = actions,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun LoadingBody(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(BgDarkVoid),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = ElectricCyan)
    }
}

@Composable
private fun SplashScreen(onEnter: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDarkVoid)
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.height(16.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NeonLogoBox()
                Spacer(Modifier.height(28.dp))
                Text(
                    text = "Unit Converter",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Fast & Offline Utility",
                    fontSize = 15.sp,
                    color = Color(0xFF9CA3AF),
                )
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(
                    modifier = Modifier.width(180.dp),
                    color = NeonPurple.copy(alpha = 0.5f),
                    thickness = 1.5.dp,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = NeonPurple,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "All metrics loaded",
                        fontSize = 13.sp,
                        color = Color(0xFF9CA3AF),
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(Color(0xFFC2B0FF))
                        .clickable { onEnter() }
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Tap to enter",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1A0A40),
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFF1A0A40),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NeonLogoBox() {
    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(SurfaceCard)
            .border(1.5.dp, BorderPurple, RoundedCornerShape(32.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_app_logo),
            contentDescription = "Unit Converter Logo",
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun NeonTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    onSettings: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = NeonPurpleLight,
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings),
                    tint = NeonPurpleLight,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = BgDarkVoid,
        ),
    )
}

@Composable
private fun HomeGrid(
    selected: Category,
    onCategory: (Category) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(NeonPurple, AccentIndigo, ElectricCyan),
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.GridView,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Units",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "6 Categories",
                    fontSize = 13.sp,
                    color = Color(0xFF9CA3AF),
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings),
                    tint = NeonPurpleLight,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items = Category.entries) { cat ->
                NeonCategoryCard(
                    category = cat,
                    isSelected = cat == selected,
                    onClick = { onCategory(cat) },
                )
            }
        }
    }
}

private data class CatStyle(
    val iconTint: Color,
    val iconBg: Color,
    val border: Color,
    val dot: Color,
    val gradStart: Color,
)

private fun categoryStyle(category: Category): CatStyle = when (category) {
    Category.Weight      -> CatStyle(AccentAmber,     Color(0x33F59E0B), Color(0x66F59E0B), AccentAmber,     Color(0x1DF59E0B))
    Category.Length      -> CatStyle(ElectricCyan,    Color(0x3338BDF8), BorderCyan,        ElectricCyan,    Color(0x1D38BDF8))
    Category.Speed       -> CatStyle(AccentIndigo,    Color(0x336366F1), Color(0x666366F1), AccentIndigo,    Color(0x1D6366F1))
    Category.Temperature -> CatStyle(AccentRose,      Color(0x33F43F5E), Color(0x66F43F5E), AccentRose,      Color(0x1DF43F5E))
    Category.Area        -> CatStyle(AccentEmerald,   Color(0x3334D399), Color(0x6634D399), AccentEmerald,   Color(0x1D34D399))
    Category.Volume      -> CatStyle(NeonPurpleLight, Color(0x33A855F7), BorderPurple,      NeonPurpleLight, Color(0x1DA855F7))
}

private fun categorySubtitle(category: Category): String = when (category) {
    Category.Weight      -> "kg, g, lb, oz"
    Category.Length      -> "m, km, ft, in, mi"
    Category.Speed       -> "km/h, m/s, mph"
    Category.Temperature -> "°C, °F, K, °R"
    Category.Area        -> "m², sq ft, acre"
    Category.Volume      -> "L, mL, gal, fl oz"
}

@Composable
private fun NeonCategoryCard(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val style = categoryStyle(category)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.05f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) style.border else BorderSubtle,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(style.gradStart, SurfaceCard)
                    )
                )
                .padding(14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Top Row: Icon + Arrow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(style.iconBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = categoryIcon(category),
                            contentDescription = null,
                            tint = style.iconTint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = style.iconTint.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                    )
                }

                Spacer(Modifier.weight(1f))

                // Unit Title
                Text(
                    text = category.displayName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )

                // Minimal Gap between Unit & Subunit
                Spacer(Modifier.height(2.dp))

                // Subunit Description + Indicator Dot
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = categorySubtitle(category),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF9CA3AF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(style.dot),
                    )
                }
            }
        }
    }
}

private fun categoryIcon(category: Category): ImageVector = when (category) {
    Category.Length      -> Icons.Outlined.Straighten
    Category.Weight      -> Icons.Outlined.MonitorWeight
    Category.Volume      -> Icons.Outlined.WaterDrop
    Category.Temperature -> Icons.Outlined.Thermostat
    Category.Area        -> Icons.Outlined.SquareFoot
    Category.Speed       -> Icons.Outlined.Speed
}

@Composable
private fun ConvertPane(
    state: MainUiState.Ready,
    actions: MainActions,
    modifier: Modifier = Modifier,
) {
    val controls = state.controls

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDarkVoid)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NeonConvCard(
            label = "FROM",
            units = controls.category.units,
            selected = controls.from,
            onUnitSelected = actions.onFromUnitSelected,
            valueText = controls.input.ifEmpty { "0" },
            valueTint = if (state is MainUiState.Invalid) MaterialTheme.colorScheme.error else Color.White,
            borderColor = BorderPurple,
        )

        if (state is MainUiState.Invalid) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentRose.copy(alpha = 0.1f))
                    .padding(10.dp),
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRose, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(invalidHelperText(state), fontSize = 12.sp, color = AccentRose)
            }
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(NeonPurple, AccentIndigo, ElectricCyan),
                        )
                    )
                    .clickable { actions.onSwapUnits() },
                contentAlignment = Alignment.Center,
            ) {
                Text("⇅", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        NeonResultCard(state = state, actions = actions)

        if (state is MainUiState.Content) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Surface(
                    shape = CircleShape,
                    color = SurfaceInput,
                    border = BorderStroke(1.dp, BorderPurple),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("⇄ ", fontSize = 12.sp, color = NeonPurple)
                        Text(
                            text = state.result.formulaLines.joinToString(" · "),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = NeonPurpleLight,
                        )
                    }
                }
            }
        }

        NeonKeypad(
            onKey = { key ->
                when (key) {
                    "C"  -> actions.onClear()
                    "±"  -> actions.onToggleSign()
                    "⌫"  -> {
                        val cur = controls.input
                        actions.onInputChanged(if (cur.length <= 1) "" else cur.dropLast(1))
                    }
                    else -> actions.onInputChanged(controls.input + key)
                }
            },
            showSign = controls.allowsNegative,
            onConvert = actions.onConvert,
            convertEnabled = controls.canConvert,
        )

        Spacer(Modifier.height(8.dp))
    }
}

private fun invalidHelperText(state: MainUiState.Invalid): String = when (val r = state.reason) {
    is InvalidReason.BeyondAbsoluteZero -> if (r.limitIsMaximum) "Value cannot exceed ${state.limitText}" else "Value cannot be below ${state.limitText}"
    InvalidReason.MultipleDecimalPoints -> "Multiple decimal points detected"
    is InvalidReason.NegativeNotAllowed -> "Negative values are not allowed for ${r.category.displayName}"
    InvalidReason.NotANumber -> "Invalid number format"
    InvalidReason.OutOfRange -> "Value is out of valid range"
}

@Composable
private fun NeonConvCard(
    label: String,
    units: List<MeasureUnit>,
    selected: MeasureUnit,
    onUnitSelected: (MeasureUnit) -> Unit,
    valueText: String,
    valueTint: Color,
    borderColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color(0xFF6B7280),
                )
                NeonUnitPicker(
                    units = units,
                    selected = selected,
                    label = label,
                    onSelected = onUnitSelected,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = valueText,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = valueTint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = selected.symbol,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonPurple,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun NeonResultCard(
    state: MainUiState.Ready,
    actions: MainActions,
) {
    val controls = state.controls
    val resultText = when (state) {
        is MainUiState.Content -> state.result.outputText
        else -> "0"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderCyan, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "TO",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color(0xFF6B7280),
                )
                NeonUnitPicker(
                    units = controls.category.units,
                    selected = controls.to,
                    label = "TO",
                    onSelected = actions.onToUnitSelected,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = resultText,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ElectricCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = controls.to.symbol,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun NeonUnitPicker(
    units: List<MeasureUnit>,
    selected: MeasureUnit,
    label: String,
    onSelected: (MeasureUnit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceInput,
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selected.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Spacer(Modifier.width(4.dp))
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(SurfaceCard)
                .requiredWidthIn(min = 210.dp),
        ) {
            units.forEach { unit ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = unit.name,
                                color = Color.White,
                                fontSize = 14.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = unit.symbol,
                                color = ElectricCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    },
                    onClick = {
                        onSelected(unit)
                        expanded = false
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun NeonKeypad(
    onKey: (String) -> Unit,
    showSign: Boolean,
    onConvert: () -> Unit,
    convertEnabled: Boolean,
) {
    val keys = listOf(
        listOf("7", "8", "9", "⌫"),
        listOf("4", "5", "6", "C"),
        listOf("1", "2", "3", "."),
        listOf(if (showSign) "±" else "", "0"),
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        keys.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { key ->
                    if (key.isNotEmpty()) {
                        KeypadButton(
                            text = key,
                            modifier = Modifier.weight(1f),
                            onClick = { onKey(key) },
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Button(
            onClick = onConvert,
            enabled = convertEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricCyan,
                contentColor = BgDarkVoid,
                disabledContainerColor = SurfaceInput,
                disabledContentColor = Color(0xFF4B5563),
            ),
        ) {
            Text(
                text = "CONVERT",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isAction = text == "C" || text == "⌫" || text == "±"
    val bgColor = if (isAction) SurfaceInput else SurfaceCard
    val textColor = if (isAction) NeonPurpleLight else Color.White

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
        )
    }
}