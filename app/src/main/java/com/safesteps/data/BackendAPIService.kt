package com.safesteps.data

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

data class RouteRequest(
    val originLat: Double,
    val originLong: Double,
    val destLat: Double,
    val destLong: Double
)

data class RutaResponse(
    val routes: List<RouteData>
)

data class RouteData(
    val distànciaKm: Double,
    val tempsMinuts: Int,
    val coordenades: List<Coordenada>,

    val puntsInteres: List<PuntInteres> = emptyList()
)

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

interface ElMeuBackendApi {
    @POST("api/v1/calculate-route")
    suspend fun calcularRuta(
        @Body request: RouteRequest
    ): Response<RutaResponse>
}

object backendAPI {
    private const val BASE_URL = "http://nattech.fib.upc.edu:40381/"

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val service: ElMeuBackendApi by lazy {
        retrofit.create(ElMeuBackendApi::class.java)
    }
}