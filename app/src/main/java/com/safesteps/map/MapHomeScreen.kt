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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.platform.testTag
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

@Composable
private fun SearchBarItem(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    borderColor: Color = Color(0xFFE4ECE8),
    textColor: Color = Color(0xFF3D4A45),
    placeholderColor: Color = Color(0xFF9AA7A0),
    onFocus: (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(26.dp)
            )
            .background(Color.White, RoundedCornerShape(26.dp))
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
                if (value.isBlank()) {
                    Text(
                        text = placeholder,
                        color = placeholderColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = textColor
                    ),
                    cursorBrush = SolidColor(Color(0xFF5AC98B)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                        .onFocusChanged {
                            if (it.isFocused) {
                                onFocus?.invoke()
                            }
                        }
                )
            }

            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(4.dp))
                trailingIcon()
            }

            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(10.dp))
                trailingContent()
            }
        }
    }
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
            placeholder = appString(R.string.my_location),
            testTag = "input_origen",
            borderColor = actualBorderColor,
            textColor = actualTextColor,
            placeholderColor = actualPlaceholderColor,
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
            },
            onFocus = onOrigenFocus
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
    origen: String,
    onOrigenChange: (String) -> Unit,
    destino: String,
    onDestinoChange: (String) -> Unit,
    mostrarOrigen: Boolean,
    onOrigenFocus: () -> Unit,
    onDestinoFocus: () -> Unit,
    adrecesSuggerides: List<Feature>,
    onAdrecaSeleccionada: (Feature) -> Unit,
    campActiu: textField,
    currentUser: UserInfo?,
    onLoginClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .shadow(10.dp, RoundedCornerShape(24.dp), clip = false),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            TopPanelHeader(
                currentUser = currentUser,
                onLoginClick = onLoginClick,
                onProfileClick = onProfileClick
            )

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedVisibility(visible = mostrarOrigen) {
                Column {
                    OriginSearchSection(
                        origen = origen,
                        onOrigenChange = onOrigenChange,
                        onOrigenFocus = onOrigenFocus,
                        campActiu = campActiu,
                        adrecesSuggerides = adrecesSuggerides,
                        onAdrecaSeleccionada = onAdrecaSeleccionada
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            SearchBarItem(
                value = destino,
                onValueChange = onDestinoChange,
                placeholder = appString(R.string.destination_label),
                testTag = "input_desti",
                borderColor = Color(0xFFF2D8D4),
                leadingIcon = {
                    Icon(
                        Icons.Default.LocationOn,
                        null,
                        tint = Color(0xFFFF8A80),
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (destino.isNotEmpty()) {
                        IconButton(onClick = { onDestinoChange("") }) {
                            Icon(Icons.Default.Clear, null, tint = Color.Gray)
                        }
                    }
                },
                onFocus = onDestinoFocus
            )

            if (campActiu == textField.DESTINY) {
                DropdownSuggeriments(adrecesSuggerides, onAdrecaSeleccionada)
            }
        }
    }
}

@Composable
internal fun BoxScope.MainMapOverlay(
    uiState: MapUiState,
    currentUser: UserInfo?,
    onLoginClick: () -> Unit,
    onProfileClick: () -> Unit,
    onOrigenChange: (String) -> Unit,
    onDestinoChange: (String) -> Unit,
    onOrigenFocus: () -> Unit,
    onDestinoFocus: () -> Unit,
    onAdrecaSeleccionada: (Feature) -> Unit
) {
    AnimatedVisibility(
        visible = !uiState.modoRuta,
        modifier = Modifier.align(Alignment.TopCenter)
    ) {
        TopSearchPanel(
            origen = uiState.textoOrigen,
            onOrigenChange = onOrigenChange,
            destino = uiState.textoDestino,
            onDestinoChange = onDestinoChange,
            mostrarOrigen = uiState.mostrarOrigen,
            onOrigenFocus = onOrigenFocus,
            onDestinoFocus = onDestinoFocus,
            adrecesSuggerides = uiState.adrecesSuggerides,
            onAdrecaSeleccionada = onAdrecaSeleccionada,
            campActiu = uiState.campActiu,
            currentUser = currentUser,
            onLoginClick = onLoginClick,
            onProfileClick = onProfileClick
        )
    }
}

@Composable
private fun MapActionPill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            .shadow(12.dp, RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        color = containerColor,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge
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
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(54.dp)
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
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
internal fun BoxScope.MapFloatingActions(
    uiState: MapUiState,
    bottomPadding: Dp,
    hideExtraInfoLabel: String,
    showExtraInfoLabel: String,
    standardMapStyleLabel: String,
    satelliteMapStyleLabel: String,
    myLocationLabel: String,
    onTogglePuntsInteres: () -> Unit,
    onToggleMapStyle: () -> Unit,
    onMyLocationClick: () -> Unit
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { it / 2 }),
        exit = slideOutVertically(targetOffsetY = { it / 2 }),
        modifier = Modifier.align(Alignment.BottomEnd)
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = bottomPadding),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedVisibility(visible = uiState.puntsInteres.isNotEmpty()) {
                MapActionPill(
                    label = if (uiState.mostrarPuntsInteres) {
                        hideExtraInfoLabel
                    } else {
                        showExtraInfoLabel
                    },
                    icon = Icons.Default.LocationOn,
                    selected = uiState.mostrarPuntsInteres,
                    onClick = onTogglePuntsInteres,
                    modifier = Modifier.width(132.dp)
                )
            }

            MapActionPill(
                label = if (uiState.estiloSatelite) {
                    standardMapStyleLabel
                } else {
                    satelliteMapStyleLabel
                },
                icon = Icons.Default.Layers,
                selected = uiState.estiloSatelite,
                onClick = onToggleMapStyle,
                modifier = Modifier
                    .width(132.dp)
                    .testTag("btn_satellit")
            )

            MapActionCircleButton(
                icon = Icons.Default.MyLocation,
                contentDescription = myLocationLabel,
                iconTint = if (uiState.modoRuta) Color(0xFF1A73E8) else Color(0xFF159957),
                onClick = onMyLocationClick,
                modifier = Modifier.testTag("btn_ubicacio_actual")
            )
        }
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
