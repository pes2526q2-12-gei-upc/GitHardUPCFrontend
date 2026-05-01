package com.safesteps.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.safesteps.R
import com.safesteps.auth.UserInfo
import com.safesteps.data.Feature
import com.safesteps.i18n.appString

private data class SearchBarVisuals(
    val borderColor: Color = Color(0xFFE4ECE8),
    val textColor: Color = Color(0xFF3D4A45),
    val placeholderColor: Color = Color(0xFF9AA7A0)
)

private data class SearchBarConfig(
    val placeholder: String,
    val testTag: String? = null,
    val visuals: SearchBarVisuals = SearchBarVisuals(),
    val onFocus: (() -> Unit)? = null
)

internal data class TopPanelAccountActions(
    val currentUser: UserInfo?,
    val onLoginClick: () -> Unit,
    val onProfileClick: () -> Unit
)

private data class TopSearchPanelState(
    val origen: String,
    val destino: String,
    val mostrarOrigen: Boolean,
    val adrecesSuggerides: List<Feature>,
    val campActiu: textField
)

internal data class TopSearchPanelCallbacks(
    val onOrigenChange: (String) -> Unit,
    val onDestinoChange: (String) -> Unit,
    val onOrigenFocus: () -> Unit,
    val onDestinoFocus: () -> Unit,
    val onAdrecaSeleccionada: (Feature) -> Unit,
    val onHeightChanged: (Float) -> Unit
)

internal data class FloatingActionsState(
    val bottomPadding: Dp,
    val compactMode: Boolean,
    val showPoiAction: Boolean,
    val showMapStyleAction: Boolean,
    val showMyLocationAction: Boolean
)

internal data class FloatingActionLabels(
    val hideExtraInfoLabel: String,
    val showExtraInfoLabel: String,
    val standardMapStyleLabel: String,
    val satelliteMapStyleLabel: String,
    val myLocationLabel: String
)

internal data class FloatingActionCallbacks(
    val onTogglePuntsInteres: () -> Unit,
    val onToggleMapStyle: () -> Unit,
    val onMyLocationClick: () -> Unit
)

@Composable
private fun SearchBarItem(
    value: String,
    onValueChange: (String) -> Unit,
    config: SearchBarConfig,
    leadingIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(26.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .border(
                width = 1.dp,
                color = config.visuals.borderColor,
                shape = shape
            )
            .background(Color.White, shape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            leadingIcon()

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                SearchBarPlaceholder(
                    value = value,
                    placeholder = config.placeholder,
                    placeholderColor = config.visuals.placeholderColor
                )

                SearchBarTextField(
                    value = value,
                    onValueChange = onValueChange,
                    config = config
                )
            }

            SearchBarAccessory(
                content = trailingIcon,
                spacing = 4.dp
            )

            SearchBarAccessory(
                content = trailingContent,
                spacing = 10.dp
            )
        }
    }
}

@Composable
private fun SearchBarPlaceholder(
    value: String,
    placeholder: String,
    placeholderColor: Color
) {
    if (value.isNotBlank()) {
        return
    }

    Text(
        text = placeholder,
        color = placeholderColor,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun SearchBarTextField(
    value: String,
    onValueChange: (String) -> Unit,
    config: SearchBarConfig
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = config.visuals.textColor
        ),
        cursorBrush = SolidColor(Color(0xFF5AC98B)),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (config.testTag != null) Modifier.testTag(config.testTag) else Modifier)
            .onFocusChanged {
                if (it.isFocused) {
                    config.onFocus?.invoke()
                }
            }
    )
}

@Composable
private fun SearchBarAccessory(
    content: (@Composable (() -> Unit))?,
    spacing: Dp
) {
    if (content == null) {
        return
    }

    Spacer(modifier = Modifier.width(spacing))
    content()
}

@Composable
private fun DropdownSuggeriments(
    adrecesSuggerides: List<Feature>,
    onAdrecaSeleccionada: (Feature) -> Unit
) {
    AnimatedVisibility(visible = adrecesSuggerides.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp)
                .shadow(4.dp, RoundedCornerShape(16.dp))
                .background(Color.White, RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFFE4ECE8), RoundedCornerShape(16.dp))
        ) {
            adrecesSuggerides.forEach { feature ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAdrecaSeleccionada(feature) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF9AA7A0),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = feature.properties.getAddress(),
                        color = Color(0xFF3D4A45),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (feature != adrecesSuggerides.last()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFF4F6F5))
                    )
                }
            }
        }
    }
}

@Composable
private fun TopPanelHeader(
    currentUser: UserInfo?,
    onLoginClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val profilePhotoLabel = appString(R.string.profile_photo)

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(modifier = Modifier.size(34.dp), shape = CircleShape, color = Color(0xFFF4F6F5)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Menu, null, tint = Color(0xFF66716C), modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
            Text(
                text = appString(R.string.app_name),
                color = Color(0xFF33413B),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (currentUser != null) {
            if (!currentUser.photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = currentUser.photoUrl,
                    contentDescription = profilePhotoLabel,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onProfileClick)
                )
            } else {
                Surface(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(onClick = onProfileClick),
                    shape = CircleShape,
                    color = Color(0xFF6DD29A)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        } else {
            Button(
                onClick = onLoginClick,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6DD29A))
            ) {
                Text(
                    text = appString(R.string.log_in),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun OriginSearchSection(
    origen: String,
    onOrigenChange: (String) -> Unit,
    onOrigenFocus: () -> Unit,
    campActiu: textField,
    adrecesSuggerides: List<Feature>,
    onAdrecaSeleccionada: (Feature) -> Unit
) {
    val esUbicacioActual = origen.isBlank() && campActiu != textField.ORIGIN

    val actualBorderColor = if (esUbicacioActual) Color(0xFFBBDEFB) else Color(0xFFDDEEE5)
    val actualTextColor = if (esUbicacioActual) Color(0xFF1E88E5) else Color(0xFF3D4A45)
    val actualPlaceholderColor = if (esUbicacioActual) Color(0xFF1E88E5) else Color(0xFF9AA7A0)

    val actualIconVector = if (esUbicacioActual) Icons.Default.MyLocation else Icons.Default.RadioButtonUnchecked
    val actualIconTint = if (esUbicacioActual) Color(0xFF1E88E5) else Color(0xFF74D3A2)

    Column {
        SearchBarItem(
            value = origen,
            onValueChange = onOrigenChange,
            config = SearchBarConfig(
                placeholder = appString(R.string.my_location),
                testTag = "input_origen",
                visuals = SearchBarVisuals(
                    borderColor = actualBorderColor,
                    textColor = actualTextColor,
                    placeholderColor = actualPlaceholderColor
                ),
                onFocus = onOrigenFocus
            ),
            leadingIcon = {
                Icon(
                    imageVector = actualIconVector,
                    contentDescription = null,
                    tint = actualIconTint,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                BotonBorrarOrigen(origen, onOrigenChange)
            }
        )

        if (campActiu == textField.ORIGIN) {
            DropdownSuggeriments(adrecesSuggerides, onAdrecaSeleccionada)
        }
    }
}

@Composable
private fun BotonBorrarOrigen(origen: String, onOrigenChange: (String) -> Unit) {
    if (origen.isNotEmpty()) {
        IconButton(onClick = { onOrigenChange("") }) {
            Icon(Icons.Default.Clear, null, tint = Color.Gray)
        }
    }
}

@Composable
private fun TopSearchPanel(
    state: TopSearchPanelState,
    callbacks: TopSearchPanelCallbacks,
    accountActions: TopPanelAccountActions
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .shadow(10.dp, RoundedCornerShape(24.dp), clip = false)
            .onGloballyPositioned {
                callbacks.onHeightChanged(it.size.height.toFloat())
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            TopPanelHeader(
                currentUser = accountActions.currentUser,
                onLoginClick = accountActions.onLoginClick,
                onProfileClick = accountActions.onProfileClick
            )

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedVisibility(visible = state.mostrarOrigen) {
                Column {
                    OriginSearchSection(
                        origen = state.origen,
                        onOrigenChange = callbacks.onOrigenChange,
                        onOrigenFocus = callbacks.onOrigenFocus,
                        campActiu = state.campActiu,
                        adrecesSuggerides = state.adrecesSuggerides,
                        onAdrecaSeleccionada = callbacks.onAdrecaSeleccionada
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            SearchBarItem(
                value = state.destino,
                onValueChange = callbacks.onDestinoChange,
                config = SearchBarConfig(
                    placeholder = appString(R.string.destination_label),
                    testTag = "input_desti",
                    visuals = SearchBarVisuals(borderColor = Color(0xFFF2D8D4)),
                    onFocus = callbacks.onDestinoFocus
                ),
                leadingIcon = {
                    Icon(
                        Icons.Default.LocationOn,
                        null,
                        tint = Color(0xFFFF8A80),
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (state.destino.isNotEmpty()) {
                        IconButton(onClick = { callbacks.onDestinoChange("") }) {
                            Icon(Icons.Default.Clear, null, tint = Color.Gray)
                        }
                    }
                }
            )

            if (state.campActiu == textField.DESTINY) {
                DropdownSuggeriments(state.adrecesSuggerides, callbacks.onAdrecaSeleccionada)
            }
        }
    }
}

@Composable
internal fun BoxScope.MainMapOverlay(
    uiState: MapUiState,
    accountActions: TopPanelAccountActions,
    callbacks: TopSearchPanelCallbacks
) {
    LaunchedEffect(uiState.modoRuta) {
        if (uiState.modoRuta) {
            callbacks.onHeightChanged(0f)
        }
    }

    AnimatedVisibility(
        visible = !uiState.modoRuta,
        modifier = Modifier.align(Alignment.TopCenter)
    ) {
        TopSearchPanel(
            state = TopSearchPanelState(
                origen = uiState.textoOrigen,
                destino = uiState.textoDestino,
                mostrarOrigen = uiState.mostrarOrigen,
                adrecesSuggerides = uiState.adrecesSuggerides,
                campActiu = uiState.campActiu
            ),
            callbacks = callbacks,
            accountActions = accountActions
        )
    }
}

@Composable
private fun MapActionPill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    compactMode: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(if (compactMode) 16.dp else 18.dp)
    val horizontalPadding = if (compactMode) 10.dp else 14.dp
    val verticalPadding = if (compactMode) 8.dp else 11.dp
    val iconSize = if (compactMode) 16.dp else 18.dp
    val spacing = if (compactMode) 6.dp else 8.dp
    val textStyle = if (compactMode) {
        MaterialTheme.typography.labelMedium
    } else {
        MaterialTheme.typography.labelLarge
    }

    val containerColor by animateColorAsState(
        targetValue = if (selected) Color(0xFFE8F0FE) else Color.White,
        label = "mapActionPillContainer"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) Color(0xFFB9D4FB) else Color(0xFFE2E7E4),
        label = "mapActionPillBorder"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color(0xFF1A73E8) else Color(0xFF44514B),
        label = "mapActionPillContent"
    )

    Surface(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .shadow(12.dp, shape, clip = false)
            .clip(shape)
            .clickable(onClick = onClick),
        color = containerColor,
        shape = shape
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, borderColor, shape)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(iconSize)
            )
            Text(
                text = label,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
                style = textStyle
            )
        }
    }
}

@Composable
private fun MapActionCircleButton(
    icon: ImageVector,
    contentDescription: String,
    iconTint: Color,
    onClick: () -> Unit,
    compactMode: Boolean,
    modifier: Modifier = Modifier
) {
    val buttonSize = if (compactMode) 46.dp else 54.dp
    val iconSize = if (compactMode) 18.dp else 22.dp

    Surface(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .size(buttonSize)
            .shadow(12.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        color = Color.White,
        shape = CircleShape
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, Color(0xFFE2E7E4), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
internal fun BoxScope.MapFloatingActions(
    uiState: MapUiState,
    reportIssueLabel: String,
    onReportIssueClick: () -> Unit,
    state: FloatingActionsState,
    labels: FloatingActionLabels,
    callbacks: FloatingActionCallbacks
) {
    val navigationBarsBottomPadding = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val resolvedBottomPadding = maxOf(state.bottomPadding, navigationBarsBottomPadding + 16.dp)
    val hasVisibleActions = hasVisibleFloatingActions(state)

    AnimatedVisibility(
        visible = hasVisibleActions,
        enter = slideInVertically(initialOffsetY = { it / 2 }),
        exit = slideOutVertically(targetOffsetY = { it / 2 }),
        modifier = Modifier.align(Alignment.BottomEnd)
    ) {
        Column(
            modifier = Modifier
                .padding(
                    end = if (state.compactMode) 12.dp else 16.dp,
                    bottom = resolvedBottomPadding
                ),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(if (state.compactMode) 8.dp else 10.dp)
        ) {
            PoiFloatingAction(
                uiState = uiState,
                state = state,
                labels = labels,
                onClick = callbacks.onTogglePuntsInteres
            )
            MapStyleFloatingAction(
                uiState = uiState,
                state = state,
                labels = labels,
                onClick = callbacks.onToggleMapStyle
            )

            AnimatedVisibility(visible = !uiState.modoRuta) {
                MapActionCircleButton(
                    icon = Icons.Default.ReportProblem,
                    contentDescription = reportIssueLabel,
                    iconTint = Color(0xFFE53935),
                    onClick = onReportIssueClick,
                    compactMode = state.compactMode,
                    modifier = Modifier.testTag("btn_incidencies")
                )
            }

            MyLocationFloatingAction(
                uiState = uiState,
                state = state,
                label = labels.myLocationLabel,
                onClick = callbacks.onMyLocationClick
            )
        }
    }
}

private fun hasVisibleFloatingActions(state: FloatingActionsState): Boolean {
    return state.showPoiAction || state.showMapStyleAction || state.showMyLocationAction
}

@Composable
private fun PoiFloatingAction(
    uiState: MapUiState,
    state: FloatingActionsState,
    labels: FloatingActionLabels,
    onClick: () -> Unit
) {
    AnimatedVisibility(visible = state.showPoiAction && uiState.puntsInteres.isNotEmpty()) {
        MapActionPill(
            label = if (uiState.mostrarPuntsInteres) {
                labels.hideExtraInfoLabel
            } else {
                labels.showExtraInfoLabel
            },
            icon = Icons.Default.LocationOn,
            selected = uiState.mostrarPuntsInteres,
            onClick = onClick,
            compactMode = state.compactMode,
            modifier = Modifier.width(if (state.compactMode) 112.dp else 132.dp)
        )
    }
}

@Composable
private fun MapStyleFloatingAction(
    uiState: MapUiState,
    state: FloatingActionsState,
    labels: FloatingActionLabels,
    onClick: () -> Unit
) {
    AnimatedVisibility(visible = state.showMapStyleAction) {
        MapActionPill(
            label = if (uiState.estiloSatelite) {
                labels.standardMapStyleLabel
            } else {
                labels.satelliteMapStyleLabel
            },
            icon = Icons.Default.Layers,
            selected = uiState.estiloSatelite,
            onClick = onClick,
            compactMode = state.compactMode,
            modifier = Modifier
                .width(if (state.compactMode) 112.dp else 132.dp)
                .testTag("btn_satellit")
        )
    }
}

@Composable
private fun MyLocationFloatingAction(
    uiState: MapUiState,
    state: FloatingActionsState,
    label: String,
    onClick: () -> Unit
) {
    AnimatedVisibility(visible = state.showMyLocationAction) {
        MapActionCircleButton(
            icon = Icons.Default.MyLocation,
            contentDescription = label,
            iconTint = if (uiState.modoRuta) Color(0xFF1A73E8) else Color(0xFF159957),
            onClick = onClick,
            compactMode = state.compactMode,
            modifier = Modifier.testTag("btn_ubicacio_actual")
        )
    }
}

@Composable
internal fun BoxScope.CalculatingRouteOverlay(
    visible: Boolean,
    calculatingBestRouteLabel: String
) {
    if (!visible) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.85f))
            .clickable(enabled = false, onClick = {})
            .align(Alignment.Center),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.traveler))
            val progress by animateLottieCompositionAsState(
                composition = composition,
                iterations = LottieConstants.IterateForever
            )

            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier
                    .size(200.dp)
                    .graphicsLayer {
                        colorFilter = ColorFilter.colorMatrix(
                            ColorMatrix().apply { setToSaturation(0f) }
                        )
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = calculatingBestRouteLabel,
                color = Color.DarkGray,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
