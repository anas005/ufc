package com.anas.ufc.data

import com.anas.ufc.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType

interface UfcApi {
    @GET("v1/events")
    suspend fun events(
        @Query("org") organization: String = "ufc",
        @Query("status") status: String? = null,
        @Query("order") order: String? = null,
        @Query("limit") limit: Int = 100,
    ): EventsResponseDto

    @GET("v1/events/{idOrSlug}")
    suspend fun event(
        @Path("idOrSlug") idOrSlug: String,
        @Query("include") include: String = "eta",
    ): EventResponseDto

    @GET("v1/fighters/{idOrSlug}")
    suspend fun fighter(@Path("idOrSlug") idOrSlug: String): FighterResponseDto
}

class MissingApiKeyException : IllegalStateException()

object UfcApiFactory {
    fun create(): UfcApi {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
            if (BuildConfig.UFCAL_API_KEY.isNotBlank()) {
                request.header("Authorization", "Bearer ${BuildConfig.UFCAL_API_KEY}")
            }
            chain.proceed(request.build())
        }
        val logging = HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.UFCAL_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(UfcApi::class.java)
    }
}
