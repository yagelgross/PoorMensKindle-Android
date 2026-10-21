package com.poorMenKindle.android.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.poorMenKindle.android.BuildConfig
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object NetworkManager {
    // Configured per-machine via API_BASE_URL in local.properties (see app/build.gradle.kts)
    val BASE_URL: String = BuildConfig.API_BASE_URL

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

    // Offline: serve cached GETs up to 7 days old instead of failing immediately
    private val offlineCacheInterceptor = Interceptor { chain ->
        var request = chain.request()
        if (request.method == "GET" && !hasNetwork(appContext)) {
            request = request.newBuilder()
                .header("Cache-Control", "public, only-if-cached, max-stale=" + 60 * 60 * 24 * 7)
                .build()
        }
        chain.proceed(request)
    }

    // Cache immutable book content (chapters, covers, TOC) for an hour. This must be a
    // network interceptor: OkHttp stores responses in its cache before application
    // interceptors see them, so rewriting headers in an application interceptor has no
    // effect on caching. Mutable endpoints (books list, progress) are left uncached.
    private val cacheRewriteInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val path = chain.request().url.encodedPath
        val isBookContent = path.contains("/chapters/") || path.endsWith("/cover") || path.endsWith("/toc")
        if (isBookContent) {
            response.newBuilder()
                .header("Cache-Control", "public, max-age=" + 60 * 60)
                .removeHeader("Pragma")
                .build()
        } else {
            response
        }
    }

    // Bodies contain credentials and bearer tokens — never log them in release builds
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    // Standard OkHttpClient setup
    private val okHttpClient: OkHttpClient by lazy {
        val cacheSize = (50 * 1024 * 1024).toLong()
        val myCache = appContext?.let { Cache(File(it.cacheDir, "book_cache"), cacheSize) }

        OkHttpClient.Builder()
            .cache(myCache)
            .addInterceptor(authInterceptor)
            .addInterceptor(offlineCacheInterceptor)
            .addNetworkInterceptor(cacheRewriteInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(480, TimeUnit.SECONDS)
            .readTimeout(480, TimeUnit.SECONDS)
            .writeTimeout(480, TimeUnit.SECONDS)
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