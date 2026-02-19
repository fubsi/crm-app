package com.mobsys.crm_app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppointmentDao {

    @Query("SELECT * FROM appointments WHERE uid = :uid")
    suspend fun getAppointmentsByUid(uid: String): List<AppointmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(appointments: List<AppointmentEntity>)

    @Query("DELETE FROM appointments WHERE uid = :uid")
    suspend fun deleteByUid(uid: String)
}

