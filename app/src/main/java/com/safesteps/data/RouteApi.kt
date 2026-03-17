package com.safesteps.data

import android.util.Log
import com.google.gson.JsonObject
import java.io.IOException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

private data class RoutePointRequest(
    val lat: Double,
    val lon: Double
)

private data class RouteRequestWithNRoutes(
    val origin: RoutePointRequest,
    val destination: RoutePointRequest,
    val nRoutes: Int = 1
)

private interface RouteCoordinatesApi {
    @POST("api/v1/calculate-route")
    suspend fun calcularRuta(
        @Body request: RouteRequestWithNRoutes
    ): Response<JsonObject>
}

private object RouteCoordinatesBackend {
    private const val BASE_URL = "http://nattech.fib.upc.edu:40381/"

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val service: RouteCoordinatesApi by lazy {
        retrofit.create(RouteCoordinatesApi::class.java)
    }
}

suspend fun obtenirCoordenadesRuta(
    origenLong: Double,
    origenLat: Double,
    destiLong: Double,
    destiLat: Double,
    nRoutes: Int = 1
): Pair<List<Coordenada>, Pair<Int, Double>> {
    Log.d("ROUTE_API", "Enviando petición al servidor")
    Log.d("ROUTE_API", "origin=($origenLat, $origenLong), destination=($destiLat, $destiLong), nRoutes=$nRoutes")

    val response = RouteCoordinatesBackend.service.calcularRuta(
        RouteRequestWithNRoutes(
            origin = RoutePointRequest(
                lat = origenLat,
                lon = origenLong
            ),
            destination = RoutePointRequest(
                lat = destiLat,
                lon = destiLong
            ),
            nRoutes = nRoutes
        )
    )

    Log.d("ROUTE_API", "Respuesta HTTP: code=${response.code()} success=${response.isSuccessful}")

    if (!response.isSuccessful) {
        throw IOException("Error calculant la ruta: ${response.code()} ${response.message()}")
    }

    val body = response.body() ?: throw IOException("La resposta del servidor és buida")
    val resultatNormalitzat = normalizarResposta(body)

    return resultatNormalitzat
}

fun normalizarResposta(
    response: JsonObject
): Pair<List<Coordenada>, Pair<Int, Double>> {
    val routes = response.getAsJsonArray("routes")
        ?: throw IOException("La resposta no conté el camp routes")

    if (routes.size() == 0) {
        return emptyList<Coordenada>() to (0 to 0.0)
    }

    val primeraRuta = routes[0].asJsonObject

    val coordinatesJson = primeraRuta.getAsJsonArray("coordinates")
        ?: throw IOException("La ruta no conté el camp coordinates")

    val coordenades = coordinatesJson.map { pointElement ->
        val point = pointElement.asJsonArray
        if (point.size() < 2) {
            throw IOException("Una coordenada de la resposta no té el format correcte")
        }

        val lon = point[0].asDouble
        val lat = point[1].asDouble

        Coordenada(
            lat,
            lon
        )
    }

    val temps = when {
        primeraRuta.has("estimatedTimeMinutes") && !primeraRuta.get("estimatedTimeMinutes").isJsonNull -> {
            primeraRuta.get("estimatedTimeMinutes").asString.toDoubleOrNull()?.toInt() ?: 0
        }
        else -> 0
    }

    val distancia = when {
        primeraRuta.has("distanceMeters") && !primeraRuta.get("distanceMeters").isJsonNull -> {
            primeraRuta.get("distanceMeters").asString.toDoubleOrNull() ?: 0.0
        }
        else -> 0.0
    }

    return coordenades to (temps to distancia)
}