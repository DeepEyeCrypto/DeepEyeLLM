package com.deepeye.agent.data.local

import androidx.room.TypeConverter
import com.deepeye.agent.core.model.ToolCallModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Room type converters for serializing complex list structures (image URIs, Tool Calls)
 * to JSON strings in SQLite.
 */
class RoomTypeConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        if (value.isNullOrEmpty()) return "[]"
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank() || value == "[]") return emptyList()
        val listType = object : TypeToken<List<String>>() {}.type
        return try {
            gson.fromJson(value, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromToolCallList(value: List<ToolCallModel>?): String {
        if (value.isNullOrEmpty()) return "[]"
        return gson.toJson(value)
    }

    @TypeConverter
    fun toToolCallList(value: String?): List<ToolCallModel> {
        if (value.isNullOrBlank() || value == "[]") return emptyList()
        val listType = object : TypeToken<List<ToolCallModel>>() {}.type
        return try {
            gson.fromJson(value, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}