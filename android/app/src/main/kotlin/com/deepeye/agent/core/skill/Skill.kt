package com.deepeye.agent.core.skill

import com.google.gson.annotations.SerializedName

/**
 * Production-grade Agent Skill representation conforming to the Agent Skills Standard.
 * Declares explicit tools, lifecycle workflows, verification gates, and anti-rationalization contracts.
 */
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
    val downloadUrl: String = "",

    @SerializedName("category")
    val category: String = "Tools",

    @SerializedName("toolsProvided")
    val toolsProvided: List<String> = emptyList(),

    @SerializedName("verificationGates")
    val verificationGates: List<String> = emptyList(),

    @SerializedName("antiRationalizationRules")
    val antiRationalizationRules: List<String> = emptyList(),

    @SerializedName("permissionsRequired")
    val permissionsRequired: List<String> = emptyList(),

    @SerializedName("documentationMarkdown")
    val documentationMarkdown: String = "",

    @SerializedName("isInstalled")
    val isInstalled: Boolean = false
)
