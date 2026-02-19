package com.mobsys.crm_app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KontaktInfo(
    val id: Int,
    val email: String,
    val telefonnummer: String,
    val rolle: String,
    @SerialName("ref_typ")
    val refTyp: String,
    @SerialName("person_Id")
    val personId: Int? = null,
    @SerialName("unternehmen_Id")
    val unternehmenId: Int? = null
)

@Serializable
data class Participant(
    val id: Int,
    @SerialName("termin_id")
    val terminId: Int,
    @SerialName("kontakt_id")
    val kontaktId: Int,
    val kontakt: KontaktInfo
) {
    // Helper properties for easier access
    val name: String
        get() = kontakt.rolle // Use role as name since actual name is not in the response

    val email: String
        get() = kontakt.email

    val telefon: String
        get() = kontakt.telefonnummer
}

@Serializable
data class ParticipantsResponse(
    val participants: List<Participant>,
    val count: Int
)

