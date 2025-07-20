// Task.kt
package com.example.lelychecklist

import com.google.gson.annotations.SerializedName

data class Task(
    @SerializedName("taskOrder") val taskOrder: Double,
    @SerializedName("machine") val machine: String,
    @SerializedName("task") val task: String,
    @SerializedName("lelyTaskNumber") val lelyTaskNumber: Double?,
    @SerializedName("guide") val guide: String?,
    @SerializedName("option") val option: String?,
    @SerializedName("services") val services: List<String>,
    @SerializedName("info") val info: String?
)