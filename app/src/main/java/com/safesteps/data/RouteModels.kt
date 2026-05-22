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
    val nom: String? = null,
    val descripcio: String? = null
)

fun PuntInteres.esEsdeveniment(): Boolean {
    return tipus.trim().uppercase() == "ESDEVENIMENT"
}

fun PuntInteres.nomMostrat(): String {
    return nom ?: tipus
        .lowercase()
        .replace('_', ' ')
        .replaceFirstChar { char -> char.uppercase() }
}
