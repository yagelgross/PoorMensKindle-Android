package com.PoorMenKindle.android.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object NetworkManager {
    const val BASE_URL = "https://dietpi.tailfa471e.ts.net"

    var jwtToken: String? = null
    var isAdmin: Boolean = false

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val authInterceptor = Interceptor { chain ->
        val requestBuilder = chain.request().newBuilder()
        jwtToken?.let {
            requestBuilder.addHeader("Authorization", "Bearer $it")
        }
        chain.proceed(requestBuilder.build())
    }

    private val cacheInterceptor = Interceptor { chain ->
        var request = chain.request()

        // If NO internet, use cached data up to 7 days old
        if (!hasNetwork(appContext)) {
            request = request.newBuilder()
                .header("Cache-Control", "public, only-if-cached, max-stale=" + 60 * 60 * 24 * 7)
                .build()
        }

        val response = chain.proceed(request)

        // If YES internet, save data to cache for 1 hour
        if (hasNetwork(appContext)) {
            response.newBuilder()
                .header("Cache-Control", "public, max-age=" + 60 * 60)
                .build()
        } else {
            response
        }
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Clean, secure, standard OkHttpClient. No more unsafe trust managers!
    private val okHttpClient: OkHttpClient by lazy {
        val cacheSize = (50 * 1024 * 1024).toLong()
        val myCache = appContext?.let { Cache(File(it.cacheDir, "book_cache"), cacheSize) }

        OkHttpClient.Builder()
            .cache(myCache)
            .addInterceptor(authInterceptor)
            .addInterceptor(cacheInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    fun disconnect() {
        jwtToken = null
        isAdmin = false
    }

    private fun hasNetwork(context: Context?): Boolean {
        if (context == null) return false
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}