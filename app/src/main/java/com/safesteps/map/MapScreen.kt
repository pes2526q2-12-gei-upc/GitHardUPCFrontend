package com.safesteps.map

import android.Manifest
import android.location.LocationListener
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safesteps.data.Feature
import com.safesteps.domain.RoutePriority
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
    campActiu: textField
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
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(34.dp), shape = CircleShape, color = Color(0xFFF4F6F5)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Menu, null, tint = Color(0xFF66716C), modifier = Modifier.size(18.dp)) }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
                    Text("SafeSteps", color = Color(0xFF33413B), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                }
                Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = Color(0xFF6DD29A)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            AnimatedVisibility(visible = mostrarOrigen) {
                Column {
                    val esUbicacioActual = origen.isBlank() && campActiu != textField.ORIGIN
                    SearchBarItem(
                        value = origen,
                        onValueChange = onOrigenChange,
                        placeholder = "La meva ubicació",
                        borderColor = if (esUbicacioActual) Color(0xFFBBDEFB) else Color(0xFFDDEEE5),
                        textColor = if (esUbicacioActual) Color(0xFF1E88E5) else Color(0xFF3D4A45),
                        placeholderColor = if (esUbicacioActual) Color(0xFF1E88E5) else Color(0xFF9AA7A0),
                        leadingIcon = {
                            Icon(
                                if (esUbicacioActual) Icons.Default.MyLocation else Icons.Default.RadioButtonUnchecked,
                                null,
                                tint = if (esUbicacioActual) Color(0xFF1E88E5) else Color(0xFF74D3A2),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onFocus = onOrigenFocus
                    )

                    if (campActiu == textField.ORIGIN) {
                        DropdownSuggeriments(adrecesSuggerides, onAdrecaSeleccionada)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            SearchBarItem(
                value = destino,
                onValueChange = onDestinoChange,
                placeholder = "Destí",
                borderColor = Color(0xFFF2D8D4),
                leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = Color(0xFFFF8A80), modifier = Modifier.size(18.dp)) },
                onFocus = onDestinoFocus
            )

            if (campActiu == textField.DESTINY) {
                DropdownSuggeriments(adrecesSuggerides, onAdrecaSeleccionada)
            }
        }
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
    onClose: () -> Unit,
    onStartRoute: () -> Unit
) {
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
                    text = "Prioritats de Ruta",
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
                        contentDescription = "Cerrar",
                        tint = Color(0xFF6C7772)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            RoutePriorityOption(
                title = "Seguretat",
                subtitle = "Zones il·luminades i concorregudes",
                selected = selectedPriority == RoutePriority.SAFETY,
                icon = Icons.Default.Security,
                iconBackground = Color(0xFF1F4A85),
                iconTint = Color.White,
                onClick = { onPrioritySelected(RoutePriority.SAFETY) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            RoutePriorityOption(
                title = "Confort i Accessibilitat",
                subtitle = "Sense barreres i tranquil",
                selected = selectedPriority == RoutePriority.ACCESSIBILITY,
                icon = Icons.Default.Accessible,
                iconBackground = Color(0xFF7FD7AA),
                iconTint = Color.White,
                onClick = { onPrioritySelected(RoutePriority.ACCESSIBILITY) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            RoutePriorityOption(
                title = "Emergència Tèrmica",
                subtitle = "Ombra i refugis climàtics",
                selected = selectedPriority == RoutePriority.HEAT,
                icon = Icons.Default.WbSunny,
                iconBackground = Color(0xFFFF7B42),
                iconTint = Color.White,
                onClick = { onPrioritySelected(RoutePriority.HEAT) }
            )

            Spacer(modifier = Modifier.height(16.dp))

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
                    text = "•",
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
                    text = "Iniciar Ruta",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun MapLibreScreen(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = viewModel()
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val mapView = rememberMapViewWithLifecycle()
    val uiState by viewModel.uiState.collectAsState()

    var sheetHeightPx by remember { mutableStateOf(0f) }
    var sheetOffsetPx by remember { mutableStateOf(0f) }
    val visibleSheetHeightPx = with(density) { 150.dp.toPx() }
    val collapsedSheetOffset = max(0f, sheetHeightPx - visibleSheetHeightPx)

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val granted = fine || coarse || hasLocationPermission(context)
        viewModel.onLocationPermissionsResult(granted)
        if (!granted) Toast.makeText(context, "Permís d'ubicació necessari.", Toast.LENGTH_SHORT).show()
    }

    val sheetDragState = rememberDraggableState { delta ->
        if (uiState.destinoSeleccionado != null && !uiState.modoRuta) {
            sheetOffsetPx = (sheetOffsetPx + delta).coerceIn(0f, collapsedSheetOffset)
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

    LaunchedEffect(uiState.estiloSatelite, uiState.modoRuta, uiState.rutaCoordenades) {
        mapView.getMapAsync { map ->
            val styleUrl =
                if (uiState.estiloSatelite) {
                    "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
                } else {
                    "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json"
                }

            map.setStyle(styleUrl) {
                viewModel.onMapaListo()

                if (uiState.locationGranted) {
                    activateLocationComponent(mapView)
                }

                if (uiState.modoRuta && uiState.rutaCoordenades.isNotEmpty()) {
                    drawRoute(mapView, uiState.rutaCoordenades)
                } else {
                    map.clear()
                    uiState.origenSeleccionado?.let { ori ->
                        map.addMarker(
                            MarkerOptions()
                                .position(ori)
                                .title("Origen")
                                .icon(crearIconaGrisa(context))
                        )
                    }
                    uiState.destinoSeleccionado?.let { dest ->
                        map.addMarker(
                            MarkerOptions()
                                .position(dest)
                                .title("Destí")
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(
        uiState.origenSeleccionado,
        uiState.destinoSeleccionado,
        uiState.modoRuta
    ) {
        if (uiState.modoRuta) return@LaunchedEffect

        mapView.getMapAsync { map ->
            if (map.style?.isFullyLoaded == true) {
                map.clear()
                uiState.origenSeleccionado?.let { ori ->
                    map.addMarker(
                        MarkerOptions()
                            .position(ori)
                            .title("Origen")
                            .icon(crearIconaGrisa(context))
                    )
                }
                uiState.destinoSeleccionado?.let { dest ->
                    map.addMarker(
                        MarkerOptions()
                            .position(dest)
                            .title("Destí")
                    )
                }
            }
        }
    }

    LaunchedEffect(uiState.rutaCoordenades, uiState.modoRuta, uiState.mapaListo) {
        if (uiState.modoRuta && uiState.mapaListo && uiState.rutaCoordenades.isNotEmpty()) {
            drawRoute(mapView, uiState.rutaCoordenades)
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
                onOrigenChange = { viewModel.onTextoBuscadorModificado(it, textField.ORIGIN) },
                destino = uiState.textoDestino,
                onDestinoChange = { viewModel.onTextoBuscadorModificado(it, textField.DESTINY) },
                mostrarOrigen = uiState.mostrarOrigen,
                onOrigenFocus = { viewModel.onTextoBuscadorModificado(uiState.textoOrigen, textField.ORIGIN) },
                onDestinoFocus = { viewModel.onTextoBuscadorModificado(uiState.textoDestino, textField.DESTINY) },
                adrecesSuggerides = uiState.adrecesSuggerides,
                onAdrecaSeleccionada = { feature ->
                    viewModel.onAdrecaSeleccionada(feature)
                    val puntSeleccionat = LatLng(feature.geometry.latitud, feature.geometry.longitud)
                    mapView.getMapAsync { map ->
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(puntSeleccionat, 15.0),
                            1500
                        )
                    }
                },
                campActiu = uiState.campActiu
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
                onPrioritySelected = { viewModel.onPrioridadSeleccionada(it) },
                distanceText = uiState.distanceText,
                durationText = uiState.durationText,
                onClose = {
                    viewModel.clearRuta()
                    sheetOffsetPx = 0f
                    mapView.getMapAsync { map ->
                        map.clear()
                        uiState.origenSeleccionado?.let { ori ->
                            map.addMarker(
                                MarkerOptions()
                                    .position(ori)
                                    .title("Origen")
                                    .icon(crearIconaGrisa(context))
                            )
                        }
                    }
                },
                onStartRoute = {
                    val destination = uiState.destinoSeleccionado
                    val selectedOrigin = uiState.origenSeleccionado
                    val currentLocation = uiState.ultimaUbicacion

                    if (destination == null) {
                        Toast.makeText(context, "Selecciona un destí", Toast.LENGTH_SHORT).show()
                    } else {
                        val origenLong = selectedOrigin?.longitude ?: currentLocation?.longitude
                        val origenLat = selectedOrigin?.latitude ?: currentLocation?.latitude

                        if (origenLong == null || origenLat == null) {
                            Toast.makeText(context, "No s'ha pogut obtenir l'origen", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.calcularRuta(
                                origenLong = origenLong,
                                origenLat = origenLat,
                                destiLong = destination.longitude,
                                destiLat = destination.latitude
                            )
                        }
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = uiState.destinoSeleccionado == null && !uiState.modoRuta,
            enter = slideInVertically(initialOffsetY = { it / 2 }),
            exit = slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.End
            ) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.toggleEstiloSatelite() },
                    icon = { Icon(Icons.Default.Layers, contentDescription = "Canviar estil") },
                    text = { Text(if (uiState.estiloSatelite) "Estàndard" else "Satèl·lit") },
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
                                Toast.makeText(context, "Buscant senyal GPS...", Toast.LENGTH_SHORT).show()
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
                    containerColor = Color.White,
                    contentColor = Color(0xFF49B97E)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "La meva ubicació")
                }
            }
        }
    }
}