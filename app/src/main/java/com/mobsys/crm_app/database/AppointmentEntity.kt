package com.mobsys.crm_app.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mobsys.crm_app.model.Appointment
import com.mobsys.crm_app.model.ArtType

@Entity(tableName = "appointments")
data class AppointmentEntity(
    @PrimaryKey val id: Int,
    val artId: Int,
    val artName: String,
    val start: String,
    val ende: String,
    val ort: String,
    val title: String,
    val uid: String
) {
    fun toAppointment() = Appointment(
        id = id,
        art = ArtType(id = artId, name = artName),
        artId = artId,
        start = start,
        ende = ende,
        ort = ort,
        title = title,
        uid = uid
    )

    companion object {
        fun fromAppointment(a: Appointment) = AppointmentEntity(
            id = a.id,
            artId = a.artId,
            artName = a.art.name,
            start = a.start,
            ende = a.ende,
            ort = a.ort,
            title = a.title,
            uid = a.uid
        )
    }
}

