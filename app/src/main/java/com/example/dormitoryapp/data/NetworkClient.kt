package com.example.dormitoryapp.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Единый сетевой клиент для работы с сервером.
 * Обеспечивает общий пул соединений (Connection Pooling) и единые настройки таймаутов.
 */
object NetworkClient {

    const val SUPABASE_URL = "https://pkiirswvrhmnnjzxwztz.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBraWlyc3d2cmhtbm5qenh3enR6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTA0NDg5NzYsImV4cCI6MjEwNjAyNDk3Nn0.wlVjds7IRCfzo_Z4vlMSLGtGEPnrNqjY_MEytdri_vM"

    val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
