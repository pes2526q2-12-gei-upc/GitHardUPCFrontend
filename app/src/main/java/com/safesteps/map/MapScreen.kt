package com.safesteps.map

import android.Manifest
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safesteps.domain.RoutePriority
import kotlinx.coroutines.launch
import org.maplibre.android.annotations.MarkerOptions
import kotlin.math.max
import com.safesteps.map.activateLocationComponent
import com.safesteps.map.hasLocationPermission
import com.safesteps.map.rememberMapViewWithLifecycle
import com.safesteps.map.startAndroidLocationUpdates
import com.safesteps.map.stopAndroidLocationUpdates

@Composable
private fun SearchBarItem(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    borderColor: Color = Color(0xFFE4ECE8),
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
                        color = Color(0xFF9AA7A0),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF3D4A45)
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
private fun TopSearchPanel(
    origen: String,
    onOrigenChange: (String) -> Unit,
    destino: String,
    onDestinoChange: (String) -> Unit,
    mostrarOrigen: Boolean,
    onDestinoFocus: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = Color(0xFFF4F6F5)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menú",
                            tint = Color(0xFF66716C),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "SafeSteps",
                    color = Color(0xFF33413B),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = Color(0xFF6DD29A)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Perfil",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedVisibility(visible = mostrarOrigen) {
                Column {
                    SearchBarItem(
                        value = origen,
                        onValueChange = onOrigenChange,
                        placeholder = "Ubicació actual",
                        borderColor = Color(0xFFDDEEE5),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color(0xFF74D3A2),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            SearchBarItem(
                value = destino,
                onValueChange = onDestinoChange,
                placeholder = "Destí",
                borderColor = Color(0xFFF2D8D4),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFFFF8A80),
                        modifier = Modifier.size(18.dp)
                    )
                },
                onFocus = onDestinoFocus
            )
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val granted = fine || coarse || hasLocationPermission(context)

        viewModel.onLocationPermissionsResult(granted)

        if (!granted) {
            Toast.makeText(context, "Permiso de ubicación necesario para navegación.", Toast.LENGTH_SHORT).show()
        }
    }

    val sheetDragState = rememberDraggableState { delta ->
        if (uiState.destinoSeleccionado != null) {
            sheetOffsetPx = (sheetOffsetPx + delta).coerceIn(0f, collapsedSheetOffset)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission(context)) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        } else {
            viewModel.onLocationPermissionsResult(true)
        }
    }

    LaunchedEffect(uiState.destinoSeleccionado) {
        if (uiState.destinoSeleccionado != null) {
            sheetOffsetPx = 0f
        }
    }

    DisposableEffect(uiState.mapaListo, uiState.locationGranted) {
        if (uiState.mapaListo && uiState.locationGranted) {
            activateLocationComponent(mapView)
            val listener = startAndroidLocationUpdates(context, mapView) { location ->
                viewModel.updateLocation(location)
            }
            onDispose { stopAndroidLocationUpdates(context, listener) }
        } else {
            onDispose { }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                mapView.apply {
                    getMapAsync { map ->
                        map.setStyle("https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json") {
                            viewModel.onMapaListo()

                            map.uiSettings.isLogoEnabled = false
                            map.uiSettings.isAttributionEnabled = false

                            if (uiState.locationGranted) {
                                activateLocationComponent(this@apply)
                            }

                            map.addOnMapClickListener { point ->
                                map.clear()
                                map.addMarker(MarkerOptions().position(point))
                                viewModel.onMapClicked(point)
                                true
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        TopSearchPanel(
            origen = uiState.textoOrigen,
            onOrigenChange = { viewModel.onOrigenChange(it) },
            destino = uiState.textoDestino,
            onDestinoChange = { viewModel.onDestinoChange(it) },
            mostrarOrigen = uiState.mostrarOrigen,
            onDestinoFocus = { /* Opcional: mostrar origen al hacer clic */ }
        )

        AnimatedVisibility(
            visible = uiState.destinoSeleccionado != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp)
        ) {
            RoutePlannerSheet(
                modifier = Modifier
                    .offset(y = with(density) { sheetOffsetPx.toDp() })
                    .onGloballyPositioned {
                        sheetHeightPx = it.size.height.toFloat()
                        if (sheetOffsetPx > collapsedSheetOffset) {
                            sheetOffsetPx = collapsedSheetOffset
                        }
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
                    mapView.getMapAsync { it.clear() }
                },
                onStartRoute = {
                    val prioridad = when (uiState.prioridadSeleccionada) {
                        RoutePriority.SAFETY -> "Seguretat"
                        RoutePriority.ACCESSIBILITY -> "Confort i Accessibilitat"
                        RoutePriority.HEAT -> "Emergència Tèrmica"
                        else -> "Seguretat"
                    }
                    Toast.makeText(context, "Iniciando ruta: $prioridad", Toast.LENGTH_SHORT).show()
                }
            )
        }

        AnimatedVisibility(
            visible = uiState.destinoSeleccionado == null,
            enter = slideInVertically(initialOffsetY = { it / 2 }),
            exit = slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Column(
                modifier = Modifier.navigationBarsPadding().padding(end = 16.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.End
            ) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.toggleEstiloSatelite() },
                    icon = { Icon(Icons.Default.Layers, contentDescription = null) },
                    text = { Text(if (uiState.estiloSatelite) "Estándar" else "Satélite") },
                    containerColor = if (uiState.estiloSatelite) Color(0xFF2F3B44) else Color.White,
                    contentColor = if (uiState.estiloSatelite) Color.White else Color(0xFF3D4A45)
                )

                Spacer(modifier = Modifier.height(12.dp))

                FloatingActionButton(
                    onClick = {
                        if (uiState.locationGranted) {
                            activateLocationComponent(mapView)
                        } else {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                        }
                    },
                    containerColor = Color.White,
                    contentColor = Color(0xFF49B97E)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = "La meva ubicació")
                }
            }
        }
    }
}
