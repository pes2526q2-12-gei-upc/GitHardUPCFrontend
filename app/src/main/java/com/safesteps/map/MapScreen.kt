package com.safesteps.map

import android.Manifest
import android.location.LocationListener
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.safesteps.R
import com.safesteps.auth.UserInfo
import com.safesteps.data.Feature
import com.safesteps.domain.RoutePriority
import com.safesteps.i18n.AppLanguage
import com.safesteps.i18n.appPlural
import com.safesteps.i18n.appString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import kotlin.math.max

@Composable
private fun SearchBarItem(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
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
                        .testTag("btn_perfil")
                )
            } else {
                Surface(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(onClick = onProfileClick)
                        .testTag("btn_perfil"),
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
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
            modifier = Modifier.testTag("input_origen"),
            value = origen,
            onValueChange = onOrigenChange,
            placeholder = appString(R.string.my_location),
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
                OriginSearchSection(
                    origen, onOrigenChange, onOrigenFocus,
                    campActiu, adrecesSuggerides, onAdrecaSeleccionada
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            SearchBarItem(
                modifier = Modifier.testTag("input_desti"),
                value = destino,
                onValueChange = onDestinoChange,
                placeholder = appString(R.string.destination_label),
                borderColor = Color(0xFFF2D8D4),
                leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = Color(0xFFFF8A80), modifier = Modifier.size(18.dp)) },
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
private fun RoutePriorityCompactOption(
    title: String,
    selected: Boolean,
    icon: ImageVector,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected) activeColor.copy(alpha = 0.15f) else Color.Transparent
    val contentColor = if (selected) activeColor else Color(0xFF77837D)
    val borderColor = if (selected) activeColor else Color(0xFFE7ECE8)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}

@Composable
private fun RoutePriorityOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    icon: ImageVector,
    iconBackground: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (selected) 5.dp else 0.dp,
                shape = RoundedCornerShape(22.dp),
                clip = false
            )
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(
                width = 1.dp,
                color = if (selected) Color(0xFF5D6F8A) else Color(0xFFE7ECE8),
                shape = RoundedCornerShape(22.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(16.dp),
                color = iconBackground
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color(0xFF23333A),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color(0xFF77837D),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color(0xFF3C577D),
                    unselectedColor = Color(0xFFC3CDD0)
                )
            )
        }
    }
}

@Composable
private fun RoutePlannerSheet(
    modifier: Modifier = Modifier,
    selectedPriority: RoutePriority,
    onPrioritySelected: (RoutePriority) -> Unit,
    distanceText: String,
    durationText: String,
    puntsInteres: List<com.safesteps.data.PuntInteres>,
    onClose: () -> Unit,
    onStartRoute: () -> Unit
) {
    val closeLabel = appString(R.string.close)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(44.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFD8DDDA))
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = appString(R.string.route_priorities),
                    color = Color(0xFF1F2C3B),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = Color(0xFFEAF9EF)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DirectionsWalk,
                            contentDescription = null,
                            tint = Color(0xFF5CCF8A),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = closeLabel,
                        tint = Color(0xFF6C7772)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RoutePriorityCompactOption(
                    title = appString(R.string.filter_safety),
                    selected = selectedPriority == RoutePriority.SAFETY,
                    icon = Icons.Default.Security,
                    activeColor = Color(0xFF1F4A85),
                    onClick = { onPrioritySelected(RoutePriority.SAFETY) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_prioritat_seguretat")
                )

                RoutePriorityCompactOption(
                    title = appString(R.string.filter_comfort),
                    selected = selectedPriority == RoutePriority.ACCESSIBILITY,
                    icon = Icons.Default.Accessible,
                    activeColor = Color(0xFF7FD7AA),
                    onClick = { onPrioritySelected(RoutePriority.ACCESSIBILITY) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_prioritat_accessibilitat")
                )

                RoutePriorityCompactOption(
                    title = appString(R.string.filter_climate),
                    selected = selectedPriority == RoutePriority.HEAT,
                    icon = Icons.Default.WbSunny,
                    activeColor = Color(0xFFFF7B42),
                    onClick = { onPrioritySelected(RoutePriority.HEAT) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_prioritat_clima")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (puntsInteres.isNotEmpty()) {
                val fonts = puntsInteres.count { it.tipus.uppercase() == "FONT" }
                val bancs = puntsInteres.count { it.tipus.uppercase() == "BANC" }
                val comisaries = puntsInteres.count { it.tipus.uppercase() == "COMISSARIA" }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF2F4F3), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (fonts > 0) {
                        Text(
                            text = "\uD83D\uDCA7 ${appPlural(R.plurals.poi_fountains, fonts, fonts)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF3D4A45)
                        )
                    }
                    if (bancs > 0) {
                        Text(
                            text = "\uD83E\uDE91 ${appPlural(R.plurals.poi_benches, bancs, bancs)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF3D4A45)
                        )
                    }
                    if (comisaries > 0) {
                        Text(
                            text = "\uD83D\uDC6E ${appPlural(R.plurals.poi_police_stations, comisaries, comisaries)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF3D4A45)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFF5E6763),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = distanceText,
                    color = Color(0xFF5E6763),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "\u2022",
                    color = Color(0xFF88D1A6),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = Color(0xFF5E6763),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = durationText,
                    color = Color(0xFF5E6763),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onStartRoute,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFC86A37),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = appString(R.string.start_route),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun RouteActiveBottomBar(
    durationText: String,
    distanceText: String,
    etaText: String,
    onClose: () -> Unit
) {
    val closeLabel = appString(R.string.close)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = Color(0xFFF2F4F3)
            ) {
                IconButton(onClick = onClose, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = closeLabel,
                        tint = Color(0xFF3D4A45)
                    )
                }
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = durationText,
                        color = Color(0xFF202124),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = distanceText,
                            color = Color(0xFF5F6368),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "\u2022",
                            color = Color(0xFF5F6368),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = etaText,
                            color = Color(0xFF5F6368),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.size(44.dp))
        }
    }
}

    @Composable
    fun MapLibreScreen(
        modifier: Modifier = Modifier,
        currentUser: UserInfo? = null,
        currentLanguage: AppLanguage = AppLanguage.default,
        onLoginClick: () -> Unit = {},
        onProfileClick: () -> Unit = {},
    ) {
        val context = LocalContext.current
        val viewModel: MapViewModel = viewModel(
            factory = MapViewModelFactory(context.applicationContext)
        )
        val density = LocalDensity.current
        val coroutineScope = rememberCoroutineScope()
        val mapView = rememberMapViewWithLifecycle()
        var hasDoneInitialZoom by remember { mutableStateOf(false) }
        val uiState by viewModel.uiState.collectAsState()
        val locationPermissionRequiredMessage = appString(R.string.location_permission_required)
        val waitingGpsLocationMessage = appString(R.string.waiting_gps_location)
        val searchingGpsSignalMessage = appString(R.string.searching_gps_signal)
        val originLabel = appString(R.string.origin_label)
        val destinationLabel = appString(R.string.destination_label)
        val hideExtraInfoLabel = appString(R.string.hide_extra_info)
        val showExtraInfoLabel = appString(R.string.show_extra_info)
        val changeMapStyleLabel = appString(R.string.change_map_style)
        val standardMapStyleLabel = appString(R.string.map_style_standard)
        val satelliteMapStyleLabel = appString(R.string.map_style_satellite)
        val myLocationLabel = appString(R.string.my_location)
        val calculatingBestRouteLabel = appString(R.string.calculating_best_route)

        LaunchedEffect(currentLanguage) {
            viewModel.onLanguageChanged(currentLanguage)
        }

        var sheetHeightPx by remember { mutableStateOf(0f) }
        var sheetOffsetPx by remember { mutableStateOf(0f) }
        val visibleSheetHeightPx = with(density) { 150.dp.toPx() }
        val collapsedSheetOffset = max(0f, sheetHeightPx - visibleSheetHeightPx)

        val dynamicBottomPadding by animateDpAsState(
            targetValue = when {
                uiState.modoRuta -> 110.dp
                uiState.destinoSeleccionado != null -> {
                    val currentVisibleHeightPx = sheetHeightPx - sheetOffsetPx
                    val currentVisibleHeightDp = with(density) { currentVisibleHeightPx.toDp() }
                    currentVisibleHeightDp + 16.dp
                }
                else -> 16.dp
            },
            label = "buttonPadding"
        )
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            val granted = fine || coarse

            viewModel.onLocationPermissionsResult(granted)

            if (!granted) {
                Toast.makeText(context, locationPermissionRequiredMessage, Toast.LENGTH_SHORT).show()
            }
        }

        val sheetDragState = rememberDraggableState { delta ->
            if (uiState.destinoSeleccionado != null && !uiState.modoRuta) {
                sheetOffsetPx = (sheetOffsetPx + delta).coerceIn(0f, collapsedSheetOffset)
            }
        }

        val resetToMainMenu = {
            viewModel.clearRuta()
            viewModel.limpiarOrigen()
            sheetOffsetPx = 0f
            mapView.getMapAsync { map ->
                map.clear()
                uiState.ultimaUbicacion?.let { loc ->
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(loc.latitude, loc.longitude),
                            15.0
                        ),
                        1000
                    )
                }
            }
        }

        LaunchedEffect(Unit) {
            val yaTengoPermiso = hasLocationPermission(context)
            viewModel.onLocationPermissionsResult(yaTengoPermiso)

            if (!yaTengoPermiso) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }

        LaunchedEffect(uiState.locationGranted, uiState.mapaListo) {
            if (uiState.locationGranted && uiState.mapaListo) {
                activateLocationComponent(mapView)
            }
        }

        LaunchedEffect(uiState.destinoSeleccionado, uiState.origenSeleccionado) {
            val destination = uiState.destinoSeleccionado
            if (destination != null) {
                val origenPoint = uiState.origenSeleccionado ?: uiState.ultimaUbicacion?.let { LatLng(it.latitude, it.longitude) }

                if (origenPoint != null) {
                    viewModel.calcularRuta(
                        origenLong = origenPoint.longitude,
                        origenLat = origenPoint.latitude,
                        destiLong = destination.longitude,
                        destiLat = destination.latitude
                    )
                }
            }
        }

        LaunchedEffect(
            uiState.estiloSatelite,
            uiState.modoRuta,
            uiState.rutaCoordenades,
            uiState.mostrarPuntsInteres,
            uiState.puntsInteres,
            uiState.origenSeleccionado,
            uiState.destinoSeleccionado
        ) {
            mapView.getMapAsync { map ->
                val styleUrl = if (uiState.estiloSatelite) {
                    "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
                } else {
                    "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json"
                }

                map.setStyle(styleUrl) {
                    viewModel.onMapaListo()
                    if (uiState.locationGranted) activateLocationComponent(mapView)

                    map.clear()

                    if (uiState.rutaCoordenades.isNotEmpty()) {
                        val origenPoint = uiState.origenSeleccionado ?: uiState.ultimaUbicacion?.let { LatLng(it.latitude, it.longitude) }

                        drawRoute(
                            mapView = mapView,
                            coordenades = uiState.rutaCoordenades,
                            origen = origenPoint,
                            desti = uiState.destinoSeleccionado,
                            context = context,
                            originTitle = originLabel,
                            destinationTitle = destinationLabel
                        )

                        if (uiState.mostrarPuntsInteres) {
                            uiState.puntsInteres
                                .filter { punt ->
                                    val tipus = punt.tipus.trim().uppercase()
                                    tipus == "FONT" || tipus == "COMISSARIA"
                                }
                                .forEach { punt ->
                                    val titulo = punt.nom ?: punt.tipus.lowercase().replaceFirstChar { it.uppercase() }
                                    map.addMarker(
                                        MarkerOptions()
                                            .position(LatLng(punt.latitud, punt.longitud))
                                            .title(titulo)
                                            .icon(crearIconaPoi(context, punt.tipus))
                                    )
                                }
                        }
                    } else {
                        uiState.origenSeleccionado?.let { ori ->
                            map.addMarker(
                                MarkerOptions()
                                    .position(ori)
                                    .title(originLabel)
                                    .icon(crearIconaGrisa(context))
                            )
                        }
                        uiState.destinoSeleccionado?.let { dest ->
                            map.addMarker(MarkerOptions().position(dest).title(destinationLabel))
                        }
                    }
                }
            }
        }

        DisposableEffect(uiState.locationGranted) {
            var listener: LocationListener? = null
            if (uiState.locationGranted) {
                listener = startAndroidLocationUpdates(context, mapView) { loc ->
                    viewModel.updateLocation(loc)
                }
            }
            onDispose { stopAndroidLocationUpdates(context, listener) }
        }

        LaunchedEffect(uiState.ultimaUbicacion, uiState.mapaListo) {
            if (uiState.ultimaUbicacion != null && uiState.mapaListo && !uiState.firstLocationZoomDone) {
                viewModel.marcarZoomInicialHecho()
                delay(500)
                mapView.getMapAsync { map ->
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(uiState.ultimaUbicacion!!.latitude, uiState.ultimaUbicacion!!.longitude),
                            15.0
                        ),
                        1500
                    )
                }
            }
        }

        Box(modifier = modifier.fillMaxSize()) {
            AndroidView(
                factory = {
                    mapView.apply {
                        getMapAsync { map ->
                            map.uiSettings.isLogoEnabled = false
                            map.uiSettings.isAttributionEnabled = false

                            map.setOnMarkerClickListener { marker ->
                                val puntPulsat = uiState.puntsInteres.find {
                                    it.latitud == marker.position.latitude && it.longitud == marker.position.longitude
                                }
                                viewModel.onPuntInteresSeleccionat(puntPulsat)

                                false
                            }

                            map.addOnMapClickListener { point ->
                                if (uiState.modoRuta) {
                                    true
                                } else {
                                    viewModel.onMapClicked(point)
                                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 15.0), 1000)
                                    true
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            AnimatedVisibility(visible = !uiState.modoRuta) {
                TopSearchPanel(
                    origen = uiState.textoOrigen,
                    onOrigenChange = { text ->
                        viewModel.onTextoBuscadorModificado(text, textField.ORIGIN)
                        if (text.isEmpty()) {
                            viewModel.limpiarOrigen()
                            viewModel.cancelarRutaVisual()
                        }
                    },
                    destino = uiState.textoDestino,
                    onDestinoChange = { text ->
                        viewModel.onTextoBuscadorModificado(text, textField.DESTINY)
                        if (text.isEmpty()) {
                            viewModel.limpiarDestino()
                            viewModel.cancelarRutaVisual()
                        }
                    },
                    mostrarOrigen = uiState.mostrarOrigen,
                    onOrigenFocus = { viewModel.onTextoBuscadorModificado(uiState.textoOrigen, textField.ORIGIN) },
                    onDestinoFocus = { viewModel.onTextoBuscadorModificado(uiState.textoDestino, textField.DESTINY) },
                    adrecesSuggerides = uiState.adrecesSuggerides,
                    onAdrecaSeleccionada = { feature ->
                        val estavemBuscantOrigen = uiState.campActiu == textField.ORIGIN
                        viewModel.onAdrecaSeleccionada(feature)
                        if (estavemBuscantOrigen) {
                            val puntSeleccionat = LatLng(feature.geometry.latitud, feature.geometry.longitud)
                            mapView.getMapAsync { map ->
                                map.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(puntSeleccionat, 15.0),
                                    1500
                                )
                            }
                        }
                    },
                    campActiu = uiState.campActiu,
                    currentUser = currentUser,
                    onLoginClick = onLoginClick,
                    onProfileClick = onProfileClick
                )
            }

            AnimatedVisibility(
                visible = uiState.destinoSeleccionado != null && !uiState.modoRuta,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 10.dp)
            ) {
                RoutePlannerSheet(
                    modifier = Modifier
                        .offset(y = with(density) { sheetOffsetPx.toDp() })
                        .onGloballyPositioned {
                            sheetHeightPx = it.size.height.toFloat()
                            if (sheetOffsetPx > collapsedSheetOffset) sheetOffsetPx = collapsedSheetOffset
                        }
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = sheetDragState,
                            onDragStopped = {
                                val target = if (sheetOffsetPx > collapsedSheetOffset / 2f) collapsedSheetOffset else 0f
                                coroutineScope.launch {
                                    animate(initialValue = sheetOffsetPx, targetValue = target) { value, _ ->
                                        sheetOffsetPx = value
                                    }
                                }
                            }
                        ),
                    selectedPriority = uiState.prioridadSeleccionada,
                    onPrioritySelected = { priority ->
                        viewModel.onPrioritySelected(priority)
                        val destination = uiState.destinoSeleccionado
                        val selectedOrigin = uiState.origenSeleccionado
                        val currentLocation = uiState.ultimaUbicacion

                        if (destination != null) {
                            val origenLong = selectedOrigin?.longitude ?: currentLocation?.longitude
                            val origenLat = selectedOrigin?.latitude ?: currentLocation?.latitude

                            if (origenLong != null && origenLat != null) {
                                viewModel.calcularRuta(
                                    origenLong = origenLong,
                                    origenLat = origenLat,
                                    destiLong = destination.longitude,
                                    destiLat = destination.latitude
                                )
                            } else {
                                Toast.makeText(context, waitingGpsLocationMessage, Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    },
                    distanceText = uiState.distanceText,
                    durationText = uiState.durationText,
                    onClose = resetToMainMenu,
                    puntsInteres = uiState.puntsInteres,
                    onStartRoute = {
                        viewModel.iniciarNavegacio()
                    }
                )
            }

            AnimatedVisibility(
                visible = uiState.modoRuta,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                RouteActiveBottomBar(
                    durationText = uiState.durationText,
                    distanceText = uiState.distanceText,
                    etaText = uiState.etaText,
                    onClose = resetToMainMenu
                )
            }

            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(initialOffsetY = { it / 2 }),
                exit = slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(end = 16.dp, bottom = dynamicBottomPadding),
                    horizontalAlignment = Alignment.End
                ) {
                    AnimatedVisibility(visible = uiState.puntsInteres.isNotEmpty() && !uiState.modoRuta) {
                        ExtendedFloatingActionButton(
                            onClick = { viewModel.togglePuntsInteres() },
                            icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                            text = {
                                Text(
                                    if (uiState.mostrarPuntsInteres) {
                                        hideExtraInfoLabel
                                    } else {
                                        showExtraInfoLabel
                                    }
                                )
                            },
                            containerColor = Color.White,
                            contentColor = Color(0xFFC86A37),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    ExtendedFloatingActionButton(
                        onClick = { viewModel.toggleEstiloSatelite() },
                        modifier = Modifier.testTag("btn_satellit"),
                        icon = { Icon(Icons.Default.Layers, contentDescription = changeMapStyleLabel) },
                        text = {
                            Text(
                                if (uiState.estiloSatelite) {
                                    standardMapStyleLabel
                                } else {
                                    satelliteMapStyleLabel
                                }
                            )
                        },
                        containerColor = if (uiState.estiloSatelite) Color(0xFF2F3B44) else Color.White,
                        contentColor = if (uiState.estiloSatelite) Color.White else Color(0xFF3D4A45)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    FloatingActionButton(
                        onClick = {
                            if (uiState.locationGranted) {
                                viewModel.limpiarOrigen()
                                activateLocationComponent(mapView)

                                if (uiState.ultimaUbicacion != null) {
                                    mapView.getMapAsync { map ->
                                        map.animateCamera(
                                            CameraUpdateFactory.newLatLngZoom(
                                                LatLng(uiState.ultimaUbicacion!!.latitude, uiState.ultimaUbicacion!!.longitude),
                                                15.0
                                            ),
                                            1000
                                        )
                                    }
                                } else {
                                    Toast.makeText(context, searchingGpsSignalMessage, Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        modifier = Modifier.testTag("btn_ubicacio_actual"),
                        containerColor = Color.White,
                        contentColor = Color(0xFF49B97E)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = myLocationLabel)
                    }
                }
            }
            if (uiState.calculantRuta) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.85f))
                        .clickable(enabled = false, onClick = {}),
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
        }
    }
