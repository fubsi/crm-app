package com.mobsys.crm_app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Adresse(
    val id: Int,
    val strasse: String,
    val hausnr: Int,
    val plz: Int,
    val ortsname: String
)

@Serializable
data class ReferenzData(
    val id: Int,
    val name: String,
    val titel: String,
    val geburtsdatum: String,
    @SerialName("adresse_id")
    val adresseId: Int,
    val adresse: Adresse
)

@Serializable
data class Contact(
    val id: Int,
    val email: String,
    val telefonnummer: String,
    val rolle: String,
    @SerialName("ref_typ")
    val refTyp: String,
    val referenz: Int,
    @SerialName("referenz_data")
    val referenzData: ReferenzData
) {
    // Helper property to display in dropdown
    val displayName: String
        get() = "$rolle - $email"
}

@Serializable
data class ContactsResponse(
    val contacts: List<Contact>,
    val count: Int
)





