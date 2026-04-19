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
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object NetworkManager {
    // Change this to match your FastAPI server IP
    private const val BASE_URL = "https://100.99.101.1:8000"

    var jwtToken: String? = null
    var isAdmin: Boolean = false

    // --- NEW: Application Context for Cache ---
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // Interceptor to attach the JWT token to every request automatically
    private val authInterceptor = Interceptor { chain ->
        val requestBuilder = chain.request().newBuilder()
        jwtToken?.let {
            requestBuilder.addHeader("Authorization", "Bearer $it")
        }
        chain.proceed(requestBuilder.build())
    }

    // --- NEW: Smart Cache Interceptor ---
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

    // Logging interceptor so you can see API calls in the Android Studio Logcat
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Unsafe OkHttpClient to bypass localhost HTTPS warnings
    private val unsafeOkHttpClient: OkHttpClient by lazy {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            // --- NEW: Set up a 50MB cache folder on the phone ---
            val cacheSize = (50 * 1024 * 1024).toLong()
            val myCache = appContext?.let { Cache(File(it.cacheDir, "book_cache"), cacheSize) }

            OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .cache(myCache) // <-- Attach the cache
                .addInterceptor(authInterceptor)
                .addInterceptor(cacheInterceptor) // <-- Attach the cache rules
                .addInterceptor(loggingInterceptor)
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    // The Retrofit instance
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(unsafeOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // The actual API service you will call from your UI
    val api: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    fun disconnect() {
        jwtToken = null
        isAdmin = false
    }

    // Helper function to check if the phone actually has internet ---
    private fun hasNetwork(context: Context?): Boolean {
        if (context == null) return false
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}