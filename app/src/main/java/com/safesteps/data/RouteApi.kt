package com.safesteps.data

import android.util.Log
import com.google.gson.JsonObject
import java.io.IOException
import java.util.UUID
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

enum class RouteType {
    SEGURETAT,
    CLIMA,
    CONFORT,
    PERSONALITZAT
}

data class RouteCoordinatesRequest(
    val googleId: String? = null,
    val origenLong: Double,
    val origenLat: Double,
    val destiLong: Double,
    val destiLat: Double,
    val nRoutes: Int = 1,
    val routeType: RouteType
)

private data class RoutePointRequest(
    val lat: Double,
    val lon: Double
)

private data class RouteRequestWithNRoutes(
    val googleId: String? = null,
    val origin: RoutePointRequest,
    val destination: RoutePointRequest,
    val nRoutes: Int = 1,
    val filtre: RouteType
)

private fun interface RouteCoordinatesApi {
    @POST("api/v1/calculate-route")
    suspend fun calcularRuta(
        @Body request: RouteRequestWithNRoutes
    ): Response<JsonObject>
}

private object RouteCoordinatesBackend {
    private const val BASE_URL = "http://nattech.fib.upc.edu:40382/"

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
    request: RouteCoordinatesRequest
): Triple<List<Coordenada>, Pair<Int, Double>, List<PuntInteres>> {
    Log.d("ROUTE_API", "Enviando peticion al servidor")
    Log.d(
        "ROUTE_API",
        "googleId=${request.googleId}, origin=(${request.origenLat}, ${request.origenLong}), " +
            "destination=(${request.destiLat}, ${request.destiLong}), " +
            "nRoutes=${request.nRoutes}, routeType=${request.routeType}"
    )

    val response = RouteCoordinatesBackend.service.calcularRuta(
        RouteRequestWithNRoutes(
            googleId = request.googleId,
            origin = RoutePointRequest(
                lat = request.origenLat,
                lon = request.origenLong
            ),
            destination = RoutePointRequest(
                lat = request.destiLat,
                lon = request.destiLong
            ),
            nRoutes = request.nRoutes,
            filtre = request.routeType
        )
    )

    Log.d("ROUTE_API", "Respuesta HTTP: code=${response.code()} success=${response.isSuccessful}")

    if (!response.isSuccessful) {
        throw IOException("Error calculant la ruta: ${response.code()} ${response.message()}")
    }

    val body = response.body() ?: throw IOException("La resposta del servidor es buida")
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

    val estimatedTimeMinutes = primeraRuta["estimatedTimeMinutes"]
    val temps = if (primeraRuta.has("estimatedTimeMinutes") && !estimatedTimeMinutes.isJsonNull) {
        estimatedTimeMinutes.asDouble.toInt()
    } else {
        0
    }

    val distanceMeters = primeraRuta["distanceMeters"]
    val distancia = if (primeraRuta.has("distanceMeters") && !distanceMeters.isJsonNull) {
        distanceMeters.asDouble
    } else {
        0.0
    }

    val puntsInteresList = mutableListOf<PuntInteres>()
    val pois = primeraRuta["pois"]
    if (primeraRuta.has("pois") && !pois.isJsonNull) {
        val poisJson = primeraRuta.getAsJsonArray("pois")
        poisJson.forEach { element ->
            val obj = element.asJsonObject
            val name = obj["name"]
            val description = obj["description"]
            puntsInteresList.add(
                PuntInteres(
                    id = UUID.randomUUID().toString(),
                    tipus = obj["type"].asString,
                    latitud = obj["lat"].asDouble,
                    longitud = obj["lon"].asDouble,
                    nom = if (obj.has("name") && !name.isJsonNull) name.asString else null,
                    descripcio = if (obj.has("description") && !description.isJsonNull) {
                        description.asString
                    } else {
                        null
                    }
                )
            )
        }
    }

    return Triple(coordenades, temps to distancia, puntsInteresList)
}
