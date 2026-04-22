package com.safesteps.data

data class Coordenada(
    val lat: Double,
    val lon: Double
)

data class PuntInteres(
    val id: String,
    val tipus: String,
    val latitud: Double,
    val longitud: Double,
    val nom: String? = null
)
