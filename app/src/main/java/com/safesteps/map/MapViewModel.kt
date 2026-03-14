package com.safesteps.map

import android.location.Location
import androidx.lifecycle.ViewModel
import com.safesteps.domain.RoutePriority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.maplibre.android.geometry.LatLng
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class MapViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    fun onOrigenChange(texto: String) {
        _uiState.update { it.copy(textoOrigen = texto) }
    }

    fun onDestinoChange(texto: String) {
        _uiState.update { it.copy(textoDestino = texto) }
    }

    fun onPrioridadSeleccionada(prioridad: RoutePriority) {
        _uiState.update { it.copy(prioridadSeleccionada = prioridad) }
    }

    fun toggleEstiloSatelite() {
        _uiState.update { it.copy(estiloSatelite = !it.estiloSatelite) }
    }

    fun onLocationPermissionsResult(granted: Boolean) {
        _uiState.update { it.copy(locationGranted = granted) }
    }

    fun clearRuta() {
        _uiState.update {
            it.copy(
                destinoSeleccionado = null,
                textoDestino = "",
                distanceText = "-- km",
                durationText = "-- min"
            )
        }
    }

    fun updateLocation(location: Location) {
        _uiState.update { it.copy(ultimaUbicacion = location) }
        recalcularRuta(location, _uiState.value.destinoSeleccionado)
    }

    fun onMapClicked(point: LatLng) {
        val textoDestino = String.format(Locale.US, "%.4f, %.4f", point.latitude, point.longitude)
        _uiState.update {
            it.copy(
                destinoSeleccionado = point,
                textoDestino = textoDestino,
                mostrarOrigen = true
            )
        }
        recalcularRuta(_uiState.value.ultimaUbicacion, point)
    }

    private fun recalcularRuta(currentLocation: Location?, destination: LatLng?) {
        if (currentLocation == null || destination == null) {
            _uiState.update { it.copy(distanceText = "-- km", durationText = "-- min") }
            return
        }

        val destinationLocation = Location("destination").apply {
            latitude = destination.latitude
            longitude = destination.longitude
        }

        val meters = currentLocation.distanceTo(destinationLocation)
        val km = meters / 1000.0
        val minutes = max(1, (km * 12.0).roundToInt())

        val distanceText = if (km < 1.0) {
            "${meters.roundToInt()} m"
        } else {
            String.format(Locale.US, "%.1f km", km)
        }

        _uiState.update { it.copy(distanceText = distanceText, durationText = "$minutes min") }
    }

    fun onMapaListo() {
        _uiState.update { it.copy(mapaListo = true) }
    }
}