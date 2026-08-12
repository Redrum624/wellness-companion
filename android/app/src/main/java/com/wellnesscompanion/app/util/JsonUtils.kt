package com.wellnesscompanion.app.util

import com.google.gson.Gson

inline fun <reified T> Gson.fromJsonSafe(json: String): T? {
    return try {
        fromJson(json, T::class.java)
    } catch (e: Exception) {
        null
    }
}

fun Any.toJsonString(gson: Gson): String = gson.toJson(this)
