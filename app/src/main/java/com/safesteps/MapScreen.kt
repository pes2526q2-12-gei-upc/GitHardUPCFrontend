package com.safesteps

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

@Composable
fun MapLibreScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }

    var destinoSeleccionado by remember { mutableStateOf<LatLng?>(null) }
    var textoBusqueda by remember { mutableStateOf("") }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {

        AndroidView(
            factory = {
                mapView.apply {
                    getMapAsync { map ->
                        map.setStyle("https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json")

                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = false

                        val barcelona = LatLng(41.3851, 2.1734)
                        val cameraPosition = CameraPosition.Builder()
                            .target(barcelona)
                            .zoom(12.0)
                            .build()
                        map.cameraPosition = cameraPosition

                        map.addOnMapClickListener { point ->
                            map.clear()
                            map.addMarker(MarkerOptions().position(point))

                            destinoSeleccionado = point
                            textoBusqueda = ""

                            Toast.makeText(
                                context,
                                "Destino marcado en el mapa",
                                Toast.LENGTH_SHORT
                            ).show()
                            true
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),

            update = { view ->

            }
        )



        if (destinoSeleccionado != null || textoBusqueda.isNotBlank()) {
            Button(
                onClick = {
                    val mensaje = if (destinoSeleccionado != null) {
                        "Calculando ruta a: ${destinoSeleccionado?.latitude}, ${destinoSeleccionado?.longitude}"
                    } else {
                        "Buscando dirección: $textoBusqueda"
                    }
                    Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            ) {
                Text("Calcular ruta")
            }
        }
    }
}