package com.example.dormitoryapp.data

import android.content.Context

/**
 * Менеджер очереди оффлайн-синхронизации.
 * Отслеживает локальные изменения (чистота, тишина, численность, дежурства),
 * которые были сделаны без интернета, и защищает их от удаления при синхронизации с облаком.
 * Гарантирует отправку в Supabase при первом же появлении связи.
 */
object SyncQueueManager {

    private const val PREFS_NAME = "dorm_sync_queue_prefs"
    private const val KEY_PENDING_SET = "pending_record_keys"

    /**
     * Пометить запись как ожидающую отправки в облако (оффлайн-изменение).
     */
    fun markPending(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_PENDING_SET, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(key)
        prefs.edit().putStringSet(KEY_PENDING_SET, current).apply()
    }

    /**
     * Пометить запись как успешно отправленную в Supabase.
     */
    fun markSynced(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_PENDING_SET, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (current.remove(key)) {
            prefs.edit().putStringSet(KEY_PENDING_SET, current).apply()
        }
    }

    /**
     * Проверить, ожидает ли запись отправки в облако.
     */
    fun isPending(context: Context, key: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_PENDING_SET, emptySet()) ?: emptySet()
        return key in current
    }

    /**
     * Получить все ключи записей, ожидающих отправки.
     */
    fun getAllPending(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_PENDING_SET, emptySet()) ?: emptySet()
    }

    /**
     * Очистить очередь (например, при выходе из аккаунта).
     */
    fun clearQueue(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_PENDING_SET).apply()
    }
}
