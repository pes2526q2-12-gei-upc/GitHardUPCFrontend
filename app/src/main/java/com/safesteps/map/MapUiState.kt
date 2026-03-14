package com.safesteps.map

import android.location.Location
import com.safesteps.domain.RoutePriority
import org.maplibre.android.geometry.LatLng

data class MapUiState(
    val destinoSeleccionado: LatLng? = null,
    val textoOrigen: String = "",
    val textoDestino: String = "",
    val mapaListo: Boolean = false,
    val locationGranted: Boolean = false,
    val estiloSatelite: Boolean = false,
    val mostrarOrigen: Boolean = false,
    val prioridadSeleccionada: RoutePriority = RoutePriority.SAFETY,
    val ultimaUbicacion: Location? = null,
    val distanceText: String = "-- km",
    val durationText: String = "-- min"
)
