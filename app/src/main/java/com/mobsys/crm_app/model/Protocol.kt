package com.mobsys.crm_app.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class Protocol(
    val id: Int,
    @SerialName("termin_id")
    val terminId: Int,
    val datum: String,
    val dauer: Int,
    val tldr: String,
    val text: String
) : Parcelable

@Serializable
data class ProtocolsResponse(
    val protocols: List<Protocol>,
    val count: Int
)

