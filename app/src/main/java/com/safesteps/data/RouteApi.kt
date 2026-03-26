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
): Triple<List<Coordenada>, Pair<Int, Double>, List<PuntInteres>> {

    Log.d("ROUTE_API", "Enviando petición: origin=($origenLat, $origenLong), destination=($destiLat, $destiLong)")

    val response = RouteCoordinatesBackend.service.calcularRuta(
        RouteRequestWithNRoutes(
            origin = RoutePointRequest(lat = origenLat, lon = origenLong),
            destination = RoutePointRequest(lat = destiLat, lon = destiLong),
            nRoutes = nRoutes
        )
    )

    if (!response.isSuccessful) {
        throw IOException("Error: ${response.code()}")
    }

    val body = response.body() ?: throw IOException("Resposta buida")

    return normalizarResposta(body)
}

fun normalizarResposta(
    response: JsonObject
): Triple<List<Coordenada>, Pair<Int, Double>, List<PuntInteres>> {

    val routes = response.getAsJsonArray("routes")
        ?: throw IOException("No hi ha routes")

    if (routes.size() == 0) {
        return Triple(emptyList(), 0 to 0.0, emptyList())
    }

    val primeraRuta = routes[0].asJsonObject

    val coordinatesJson = primeraRuta.getAsJsonArray("coordinates")
        ?: throw IOException("No hi ha coordinates")

    val coordenades = coordinatesJson.map { pointElement ->
        val point = pointElement.asJsonArray
        Coordenada(lat = point[1].asDouble, lon = point[0].asDouble)
    }

    val temps = if (primeraRuta.has("estimatedTimeMinutes") && !primeraRuta.get("estimatedTimeMinutes").isJsonNull) {
        primeraRuta.get("estimatedTimeMinutes").asDouble.toInt()
    } else 0

    val distancia = if (primeraRuta.has("distanceMeters") && !primeraRuta.get("distanceMeters").isJsonNull) {
        primeraRuta.get("distanceMeters").asDouble
    } else 0.0

    val puntsInteresList = mutableListOf<PuntInteres>()
    if (primeraRuta.has("pois") && !primeraRuta.get("pois").isJsonNull) {
        val poisJson = primeraRuta.getAsJsonArray("pois")
        poisJson.forEach { element ->
            val obj = element.asJsonObject
            puntsInteresList.add(
                PuntInteres(
                    id = java.util.UUID.randomUUID().toString(), // Generamos ID único
                    tipus = obj.get("type").asString,
                    latitud = obj.get("lat").asDouble,
                    longitud = obj.get("lon").asDouble,
                    nom = if (obj.has("name") && !obj.get("name").isJsonNull) obj.get("name").asString else null
                )
            )
        }
    }

    return Triple(coordenades, temps to distancia, puntsInteresList)
}