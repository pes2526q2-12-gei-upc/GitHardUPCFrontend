package com.safesteps.data

import retrofit2.http.GET
import retrofit2.http.Query
import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

interface PhotonApiService {

    @GET("api/")
    suspend fun findAddress(
        @Query("q") query: String,
        @Query("bbox") bbox: String = "2.052,41.317,2.228,41.468",
        @Query("lang") lang: String = "en",
        @Query("limit") limit: Int = 15,
        @Query("lat") lat: Double? = null,
        @Query("lon") lon: Double? = null,
        @Query("suggest_addresses") suggestAddresses: Boolean = true
    ): PhotonResponse

    @GET("reverse/")
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("lang") lang: String = "en"
    ): PhotonResponse
}

object PhotonApi {
    private const val BASE_URL = "https://photon.komoot.io/"

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val service: PhotonApiService by lazy {
        retrofit.create(PhotonApiService::class.java)
    }
}

data class PhotonResponse(
    val features: List<Feature>
)

data class Feature(
    val properties: Properties,
    val geometry: Geometry
)

data class Properties(
    val name: String?,
    val street: String?,
    val housenumber: String?,
    val postcode: String?,
    val city: String?,
    val state: String?,
    val country: String?,

    @SerializedName("osm_value")
    val osmValue: String?
) {
    fun getAddress(): String {
        if (!name.isNullOrEmpty() && name != city && name != street) {
            return if (!city.isNullOrEmpty()) "$name, $city" else name
        }

        if (!street.isNullOrEmpty()) {
            val carrerNum = if (!housenumber.isNullOrEmpty()) "$street, $housenumber" else street
            return if (!city.isNullOrEmpty()) "$carrerNum, $city" else carrerNum
        }

        return city ?: ""
    }
}

data class Geometry(
    val coordinates: List<Double>
) {
    val latitud: Double
        get() = coordinates.getOrNull(1) ?: 0.0

    val longitud: Double
        get() = coordinates.getOrNull(0) ?: 0.0
}