package com.safesteps.map

import android.location.Location
import com.safesteps.data.Coordenada
import com.safesteps.data.Feature
import com.safesteps.domain.RoutePriority
import org.maplibre.android.geometry.LatLng

enum class textField {
    NONE,
    ORIGIN,
    DESTINY
}

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
    val durationText: String = "-- min",
    val etaText: String = "--:--",
    val adrecesSuggerides: List<Feature> = emptyList(),
    val origenSeleccionado: LatLng? = null,
    val isTyping: Boolean = false,
    val firstLocationZoomDone: Boolean = false,
    val campActiu: textField = textField.NONE,
    val rutaCoordenades: List<Coordenada> = emptyList(),
    val modoRuta: Boolean = false
)