package com.safesteps

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
        @Query("limit") limit: Int = 5,
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

    // El 'by lazy' fa que només es construeixi la primera vegada que es crida, estalviant memòria
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // Aquesta és la variable que utilitzaràs des de la MainActivity
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
    val name: String?,          // Pot ser el nom d'un lloc (ex: Estació de Sants)
    val street: String?,        // Nom del carrer
    val housenumber: String?,   // Número del carrer (si l'usuari el busca)
    val postcode: String?,      // Codi postal
    val city: String?,          // Ciutat (ex: Barcelona)
    val state: String?,         // Comunitat/Estat (ex: Catalunya)
    val country: String?,       // País (ex: España)

    @SerializedName("osm_value")
    val osmValue: String?       // Per saber si és un carrer, un edifici, una ciutat...
) {
    // 💡 Funció extra de regal: Formatador automàtic per mostrar a la pantalla
    fun getAddress(): String {
        val trossos = mutableListOf<String>()

        // Si hi ha carrer i número, els ajuntem
        if (!street.isNullOrEmpty()) {
            val carrerNum = if (!housenumber.isNullOrEmpty()) "$street, $housenumber" else street
            trossos.add(carrerNum)
        } else if (!name.isNullOrEmpty()) {
            trossos.add(name) // Si no hi ha carrer, posem el nom del lloc
        }

        city?.let { trossos.add(it) }
        postcode?.let { trossos.add(it) }

        return trossos.joinToString(", ")
    }
}

// 4. Les coordenades per poder posar la xinxeta al mapa
data class Geometry(
    val coordinates: List<Double>
) {
    // Photon retorna [Longitud, Latitud], a Google Maps es fa servir (Latitud, Longitud)
    val latitud: Double
        get() = coordinates.getOrNull(1) ?: 0.0

    val longitud: Double
        get() = coordinates.getOrNull(0) ?: 0.0
}