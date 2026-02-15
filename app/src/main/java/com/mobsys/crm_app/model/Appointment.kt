package com.mobsys.crm_app.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class ArtType(
    val id: Int,
    val name: String
) : Parcelable

@Parcelize
@Serializable
data class Appointment(
    val id: Int,
    val art: ArtType,
    @SerialName("art_id")
    val artId: Int,
    val start: String,
    val ende: String,
    val ort: String,
    val title: String,
    val uid: String
) : Parcelable

@Serializable
data class AppointmentsResponse(
    val appointments: List<Appointment>,
    val count: Int
)

