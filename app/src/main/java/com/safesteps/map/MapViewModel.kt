package com.safesteps.map

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safesteps.data.Feature
import com.safesteps.data.PhotonApi
import com.safesteps.data.obtenirCoordenadesRuta
import com.safesteps.domain.RoutePriority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
                durationText = "-- min",
                rutaCoordenades = emptyList(),
                modoRuta = false,
                adrecesSuggerides = emptyList(),
                campActiu = textField.NONE,
                isTyping = false,
                mostrarOrigen = false
            )
        }
    }

    fun updateLocation(location: Location) {
        _uiState.update { it.copy(ultimaUbicacion = location) }
        recalcularRuta(location, _uiState.value.destinoSeleccionado)
    }

    fun onTextoBuscadorModificado(texto: String, campo: textField) {
        _uiState.update {
            it.copy(
                campActiu = campo,
                isTyping = true,
                textoOrigen = if (campo == textField.ORIGIN) texto else it.textoOrigen,
                textoDestino = if (campo == textField.DESTINY) texto else it.textoDestino,
                mostrarOrigen = if (campo == textField.DESTINY) true else it.mostrarOrigen
            )
        }

        if (texto.length >= 3) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(300)
                try {
                    val respuesta = PhotonApi.service.findAddress(query = texto)
                    _uiState.update { state ->
                        state.copy(adrecesSuggerides = respuesta.features.distinctBy { it.properties.getAddress() })
                    }
                } catch (_: Exception) {
                    _uiState.update { it.copy(adrecesSuggerides = emptyList()) }
                }
            }
        } else {
            _uiState.update { it.copy(adrecesSuggerides = emptyList()) }
        }
    }

    fun onAdrecaSeleccionada(feature: Feature) {
        if (_uiState.value.modoRuta) return

        val puntSeleccionat = LatLng(feature.geometry.latitud, feature.geometry.longitud)
        val textAdreca = feature.properties.getAddress()

        _uiState.update { state ->
            if (state.campActiu == textField.ORIGIN) {
                state.copy(
                    textoOrigen = textAdreca,
                    origenSeleccionado = puntSeleccionat,
                    campActiu = textField.NONE,
                    isTyping = false,
                    adrecesSuggerides = emptyList()
                )
            } else {
                state.copy(
                    textoDestino = textAdreca,
                    destinoSeleccionado = puntSeleccionat,
                    campActiu = textField.NONE,
                    isTyping = false,
                    adrecesSuggerides = emptyList()
                )
            }
        }
    }

    fun limpiarOrigen() {
        _uiState.update {
            it.copy(
                origenSeleccionado = null,
                textoOrigen = "",
                campActiu = textField.NONE,
                isTyping = false
            )
        }
    }

    fun onMapClicked(point: LatLng) {
        if (_uiState.value.modoRuta) return

        _uiState.update {
            it.copy(
                destinoSeleccionado = point,
                textoDestino = "Buscant adreça...",
                mostrarOrigen = true
            )
        }
        recalcularRuta(_uiState.value.ultimaUbicacion, point)

        viewModelScope.launch {
            val direccionReal = getTextoDestino(point)
            _uiState.update { it.copy(textoDestino = direccionReal) }
        }
    }

    fun marcarZoomInicialHecho() {
        _uiState.update { it.copy(firstLocationZoomDone = true) }
    }

    fun calcularRuta(
        origenLong: Double,
        origenLat: Double,
        destiLong: Double,
        destiLat: Double
    ) {
        viewModelScope.launch {
            try {
                val infoRuta = obtenirCoordenadesRuta(
                    origenLong = origenLong,
                    origenLat = origenLat,
                    destiLong = destiLong,
                    destiLat = destiLat,
                    nRoutes = 1
                )
                Log.d("ROUTE_VM", " = ${infoRuta.first}")
                Log.d("time", "time = ${infoRuta.second.first}")
                Log.d("distance", "distancia = ${infoRuta.second.second}")

                _uiState.update {
                    it.copy(
                        rutaCoordenades = infoRuta.first,
                        modoRuta = infoRuta.first.isNotEmpty(),
                        adrecesSuggerides = emptyList(),
                        campActiu = textField.NONE,
                        isTyping = false,
                        mostrarOrigen = false
                    )
                }
            } catch (_: Exception) {
            }
        }
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

    fun calculateDistanceAndDuration(
        currentLocation: Location?,
        destination: LatLng?
    ): Pair<String, String> {
        if (currentLocation == null || destination == null) return "-- km" to "-- min"

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

        return distanceText to "$minutes min"
    }

    private suspend fun getTextoDestino(point: LatLng): String {
        return try {
            val resposta = PhotonApi.service.reverseGeocode(
                lat = point.latitude,
                lon = point.longitude
            )
            val adreca = resposta.features.firstOrNull()?.properties?.getAddress()

            if (adreca.isNullOrBlank() || adreca.contains("LatLng") || adreca.contains("Location")) {
                "${point.longitude}, ${point.latitude}"
            } else {
                adreca
            }
        } catch (_: Exception) {
            "Ubicació seleccionada al mapa"
        }
    }
}