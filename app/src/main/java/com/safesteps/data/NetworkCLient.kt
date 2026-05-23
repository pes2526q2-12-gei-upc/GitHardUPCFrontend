package com.safesteps.data

internal val sharedOkHttpClient by lazy {
    okhttp3.OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("X-API-KEY", com.safesteps.BuildConfig.API_KEY)
                .build()
            chain.proceed(request)
        }
        .build()
}