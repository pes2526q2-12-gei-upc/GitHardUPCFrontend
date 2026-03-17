package com.safesteps.data

import android.util.Log
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
    ): Response<Any>
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
    nRoutes: Int
): List<Coordenada> {
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

    if (!response.isSuccessful) {
        throw IOException("Error calculant la ruta: ${response.code()} ${response.message()}")
    }

    Log.d("ROUTE_API", "Envaint petición al servidor")
    Log.d("ROUTE_API", "origin=($origenLat, $origenLong), destination=($destiLat, $destiLong)")
    Log.d("ROUTE_API", "Resposta HTTP: code=${response.code()} success=${response.isSuccessful}")

    return emptyList()
}