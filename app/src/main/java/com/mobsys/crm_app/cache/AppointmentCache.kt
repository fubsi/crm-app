package com.mobsys.crm_app.cache

import android.util.Log
import com.mobsys.crm_app.database.AppDatabase
import com.mobsys.crm_app.database.AppointmentEntity
import com.mobsys.crm_app.model.Appointment

class AppointmentCache(private val db: AppDatabase) {

    suspend fun getCachedAppointments(uid: String): List<Appointment> {
        return db.appointmentDao().getAppointmentsByUid(uid).map { it.toAppointment() }
    }

    suspend fun updateCache(uid: String, appointments: List<Appointment>) {
        val entities = appointments.map { AppointmentEntity.fromAppointment(it) }
        db.appointmentDao().deleteByUid(uid)
        db.appointmentDao().insertAll(entities)
        Log.d("AppointmentCache", "Cache updated with ${entities.size} appointments for UID: $uid")
    }
}

