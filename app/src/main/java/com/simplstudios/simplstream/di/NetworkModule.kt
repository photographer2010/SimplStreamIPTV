package com.simplstudios.simplstream.di

import com.simplstudios.simplstream.BuildConfig
import com.simplstudios.simplstream.data.remote.api.ConsumetApi
import com.simplstudios.simplstream.data.remote.api.StremSrcApi
import com.simplstudios.simplstream.data.remote.api.StreamApi
import com.simplstudios.simplstream.data.remote.api.TmdbApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val originalUrl = original.url
                
                // Add API key to all requests
                val url = originalUrl.newBuilder()
                    .addQueryParameter("api_key", BuildConfig.TMDB_API_KEY)
                    .build()
                
                val request = original.newBuilder()
                    .url(url)
                    .addHeader("Accept", "application/json")
                    .build()
                
                chain.proceed(request)
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }
            .build()
    }
    
    @Provides
    @Singleton
    @Named("streamApiClient")
    fun provideStreamApiClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        
        // For debug builds, trust all certificates (fixes emulator SSL issues)
        if (BuildConfig.DEBUG) {
            try {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
                
                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, SecureRandom())
                
                builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }
            } catch (e: Exception) {
                // Ignore SSL setup errors
            }
            
            builder.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
        }
        
        return builder.build()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.TMDB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
    
    @Provides
    @Singleton
    @Named("streamApiRetrofit")
    fun provideStreamApiRetrofit(
        @Named("streamApiClient") okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.STREAM_API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
    
    @Provides
    @Singleton
    fun provideTmdbApi(retrofit: Retrofit): TmdbApi {
        return retrofit.create(TmdbApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideStreamApi(@Named("streamApiRetrofit") retrofit: Retrofit): StreamApi {
        return retrofit.create(StreamApi::class.java)
    }
    
    @Provides
    @Singleton
    @Named("consumetApiRetrofit")
    fun provideConsumetApiRetrofit(
        @Named("streamApiClient") okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.CONSUMET_API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
    
    @Provides
    @Singleton
    fun provideConsumetApi(@Named("consumetApiRetrofit") retrofit: Retrofit): ConsumetApi {
        return retrofit.create(ConsumetApi::class.java)
    }
    
    @Provides
    @Singleton
    @Named("stremSrcApiRetrofit")
    fun provideStremSrcApiRetrofit(
        @Named("streamApiClient") okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.STREMSRC_API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
    
    @Provides
    @Singleton
    fun provideStremSrcApi(@Named("stremSrcApiRetrofit") retrofit: Retrofit): StremSrcApi {
        return retrofit.create(StremSrcApi::class.java)
    }
}
