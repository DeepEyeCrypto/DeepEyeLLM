package com.deepeye.agent.core.skill

import com.google.gson.annotations.SerializedName

data class Skill(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("author")
    val author: String,
    
    @SerializedName("version")
    val version: String,
    
    @SerializedName("downloadUrl")
    val downloadUrl: String,

    @SerializedName("isInstalled")
    val isInstalled: Boolean = false
)
