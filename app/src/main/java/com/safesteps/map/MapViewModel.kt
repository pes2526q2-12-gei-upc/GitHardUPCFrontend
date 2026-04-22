package com.safesteps.map

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safesteps.data.Coordenada
import com.safesteps.data.Feature
import com.safesteps.data.PhotonApi
import com.safesteps.data.PuntInteres
import com.safesteps.data.obtenirCoordenadesRuta
import com.safesteps.domain.RoutePriority
import com.safesteps.i18n.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class MapViewModel(
    private val textProvider: MapTextProvider
) : ViewModel() {
    private companion object {
        const val DEFAULT_DISTANCE_TEXT = "-- km"
        const val DEFAULT_DURATION_TEXT = "-- min"
        const val DEFAULT_ETA_TEXT = "--:--"
    }

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    private var currentLanguage: AppLanguage = AppLanguage.default

    fun onLanguageChanged(language: AppLanguage) {
        currentLanguage = language
    }

    fun onPrioritySelected(prioridad: RoutePriority) {
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
                distanceText = DEFAULT_DISTANCE_TEXT,
                durationText = DEFAULT_DURATION_TEXT,
                etaText = DEFAULT_ETA_TEXT,
                rutaCoordenades = emptyList(),
                modoRuta = false,
                adrecesSuggerides = emptyList(),
                campActiu = textField.NONE,
                isTyping = false,
                mostrarOrigen = false,
                puntsInteres = emptyList(),
                puntInteresSeleccionat = null
            )
        }
    }

    fun cancelarRutaVisual() {
        _uiState.update { it.copy(
            rutaCoordenades = emptyList(),
            modoRuta = false,
            distanceText = DEFAULT_DISTANCE_TEXT,
            durationText = DEFAULT_DURATION_TEXT,
            etaText = DEFAULT_ETA_TEXT,
            calculantRuta = false,
            puntsInteres = emptyList(),
            puntInteresSeleccionat = null
        ) }
    }

    fun updateLocation(location: Location) {
        _uiState.update { it.copy(ultimaUbicacion = location) }
    }

    fun onTextoBuscadorModificado(texto: String, campo: textField) {
        _uiState.update {
            it.copy(
                campActiu = campo,
                isTyping = true,
                textoOrigen = if (campo == textField.ORIGIN) texto else it.textoOrigen,
                textoDestino = if (campo == textField.DESTINY) texto else it.textoDestino,
                mostrarOrigen = campo == textField.DESTINY || it.mostrarOrigen
            )
        }

        if (texto.length >= 3) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(300)
                try {
                    val respuesta = PhotonApi.service.findAddress(
                        query = texto,
                        lang = textProvider.photonLanguage(currentLanguage)
                    )
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
        _uiState.update { it.copy(
            origenSeleccionado = null,
            textoOrigen = ""
        ) }
    }

    fun limpiarDestino() {
        _uiState.update { it.copy(
            destinoSeleccionado = null,
            textoDestino = ""
        ) }
    }



    fun onMapClicked(point: LatLng) {
        if (_uiState.value.modoRuta) return

        _uiState.update {
            it.copy(
                destinoSeleccionado = point,
                textoDestino = textProvider.searchingAddress(currentLanguage),
                mostrarOrigen = true,
                distanceText = DEFAULT_DISTANCE_TEXT,
                durationText = DEFAULT_DURATION_TEXT,
                etaText = DEFAULT_ETA_TEXT
            )
        }

        viewModelScope.launch {
            val direccionReal = getTextoDestino(point)
            _uiState.update { it.copy(textoDestino = direccionReal) }
        }
    }

    fun marcarZoomInicialHecho() {
        _uiState.update { it.copy(firstLocationZoomDone = true) }
    }

    fun prepararNuevaSesionMapa() {
        _uiState.update {
            it.copy(
                mapaListo = false,
                firstLocationZoomDone = false
            )
        }
    }

    fun calcularRuta(
        origenLong: Double,
        origenLat: Double,
        destiLong: Double,
        destiLat: Double
    ) {
        val prioridad = _uiState.value.prioridadSeleccionada
        val routeWeights = routeWeightsFor(prioridad)
        Log.d("PRUEBA_RUTA", "Llamando a calcularRuta. Prioridad actual: $prioridad")

        viewModelScope.launch {
            _uiState.update { it.copy(calculantRuta = true) }
            try {
                val infoRuta = obtenirCoordenadesRuta(
                    origenLong = origenLong,
                    origenLat = origenLat,
                    destiLong = destiLong,
                    destiLat = destiLat,
                    nRoutes = 1,
                    seguretat = routeWeights.seguretat,
                    fontsAigua = routeWeights.fontsAigua,
                    ombra = routeWeights.ombra,
                    eMecaniques = routeWeights.eMecaniques,
                    bancs = routeWeights.bancs
                )

                val coordenadas = infoRuta.first
                val tiempoDistancia = infoRuta.second
                val puntosInteres = infoRuta.third

                Log.d("ROUTE_VM", "Coordenadas = ${coordenadas.size}")
                Log.d("time", "time = ${tiempoDistancia.first}")
                Log.d("distance", "distancia = ${tiempoDistancia.second}")
                Log.d("POIS", "Puntos encontrados = ${puntosInteres.size}")

                val routeDurationMinutes = resolveRouteDurationMinutes(tiempoDistancia)
                applyCalculatedRoute(
                    coordenadas = coordenadas,
                    tiempoDistancia = tiempoDistancia,
                    routeDurationMinutes = routeDurationMinutes,
                    puntosInteres = puntosInteres
                )
            } catch (e: Exception) {
                Log.e("ROUTE_VM", "Error calculant la ruta: ${e.message}")
            } finally {
                _uiState.update { it.copy(calculantRuta = false) }
            }
        }
    }

    fun iniciarNavegacio() {
        _uiState.update { it.copy(modoRuta = true) }
    }

    fun onMapaListo() {
        _uiState.update { it.copy(mapaListo = true) }
    }

    fun togglePuntsInteres() {
        _uiState.update { it.copy(mostrarPuntsInteres = !it.mostrarPuntsInteres) }
    }

    fun onPuntInteresSeleccionat(punt: com.safesteps.data.PuntInteres?) {
        _uiState.update { it.copy(puntInteresSeleccionat = punt) }
    }

    private suspend fun getTextoDestino(point: LatLng): String {
        return try {
            val resposta = PhotonApi.service.reverseGeocode(
                lat = point.latitude,
                lon = point.longitude,
                lang = textProvider.photonLanguage(currentLanguage)
            )
            val adreca = resposta.features.firstOrNull()?.properties?.getAddress()

            if (adreca.isNullOrBlank() || adreca.contains("LatLng") || adreca.contains("Location")) {
                "${point.longitude}, ${point.latitude}"
            } else {
                adreca
            }
        } catch (_: Exception) {
            textProvider.selectedMapLocation(currentLanguage)
        }
    }

    private fun formatDistance(distanceMeters: Double): String {
        return if (distanceMeters < 1000.0) {
            "${distanceMeters.roundToInt()} m"
        } else {
            String.format(localeForCurrentLanguage(), "%.1f km", distanceMeters / 1000.0)
        }
    }

    private fun formatDuration(durationMinutes: Int): String {
        return if (durationMinutes > 0) "$durationMinutes min" else DEFAULT_DURATION_TEXT
    }

    private fun formatEta(durationMinutes: Int): String {
        if (durationMinutes <= 0) return DEFAULT_ETA_TEXT
        val calendar = Calendar.getInstance().apply {
            add(Calendar.MINUTE, durationMinutes)
        }
        return SimpleDateFormat("h:mm a", localeForCurrentLanguage()).format(calendar.time)
    }

    private fun estimateMinutesFromDistanceMeters(distanceMeters: Double): Int {
        val km = distanceMeters / 1000.0
        return max(1, (km * 12.0).roundToInt())
    }

    private fun localeForCurrentLanguage(): Locale {
        return Locale.forLanguageTag(currentLanguage.languageTag)
    }

    private fun routeWeightsFor(priority: RoutePriority): RouteWeights {
        return when (priority) {
            RoutePriority.SAFETY -> RouteWeights(seguretat = 1f)
            RoutePriority.ACCESSIBILITY -> RouteWeights(eMecaniques = 1f, bancs = 1f)
            RoutePriority.HEAT -> RouteWeights(ombra = 1f, fontsAigua = 1f)
        }
    }

    private fun resolveRouteDurationMinutes(tiempoDistancia: Pair<Int, Double>): Int {
        return when {
            tiempoDistancia.first > 0 -> tiempoDistancia.first
            tiempoDistancia.second > 0.0 -> estimateMinutesFromDistanceMeters(tiempoDistancia.second)
            else -> 0
        }
    }

    private fun applyCalculatedRoute(
        coordenadas: List<Coordenada>,
        tiempoDistancia: Pair<Int, Double>,
        routeDurationMinutes: Int,
        puntosInteres: List<PuntInteres>
    ) {
        _uiState.update {
            it.copy(
                rutaCoordenades = coordenadas,
                distanceText = tiempoDistancia.second
                    .takeIf { distance -> distance > 0.0 }
                    ?.let(::formatDistance)
                    ?: it.distanceText,
                durationText = routeDurationMinutes
                    .takeIf { duration -> duration > 0 }
                    ?.let(::formatDuration)
                    ?: it.durationText,
                etaText = routeDurationMinutes
                    .takeIf { duration -> duration > 0 }
                    ?.let(::formatEta)
                    ?: it.etaText,
                adrecesSuggerides = emptyList(),
                campActiu = textField.NONE,
                isTyping = false,
                puntsInteres = puntosInteres
            )
        }
    }

    private data class RouteWeights(
        val seguretat: Float = 0f,
        val fontsAigua: Float = 0f,
        val ombra: Float = 0f,
        val eMecaniques: Float = 0f,
        val bancs: Float = 0f
    )
}
