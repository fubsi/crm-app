package com.mobsys.crm_app.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mobsys.crm_app.AppointmentDetailActivity
import com.mobsys.crm_app.R
import com.mobsys.crm_app.model.Appointment
import java.text.SimpleDateFormat
import java.util.*

class AppointmentAdapter(private val appointments: List<Appointment>) :
    RecyclerView.Adapter<AppointmentAdapter.ViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMANY).apply {
        timeZone = TimeZone.getTimeZone("Europe/Berlin")
    }

    private val dateOnlyFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).apply {
        timeZone = TimeZone.getTimeZone("Europe/Berlin")
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appointmentType: TextView = itemView.findViewById(R.id.appointment_type)
        val appointmentDateTime: TextView = itemView.findViewById(R.id.appointment_datetime)
        val appointmentLocation: TextView = itemView.findViewById(R.id.appointment_location)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_appointment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val appointment = appointments[position]

        holder.appointmentType.text = appointment.art.name
        holder.appointmentLocation.text = appointment.ort

        // Format datetime
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMANY).apply {
                timeZone = TimeZone.getTimeZone("Europe/Berlin")
            }

            val startDate = inputFormat.parse(appointment.start)
            val endDate = inputFormat.parse(appointment.ende)

            if (startDate != null && endDate != null) {
                val dateStr = dateOnlyFormat.format(startDate)
                val startTimeStr = timeFormat.format(startDate)
                val endTimeStr = timeFormat.format(endDate)

                holder.appointmentDateTime.text = "$dateStr, $startTimeStr - $endTimeStr Uhr"
            } else {
                holder.appointmentDateTime.text = "${appointment.start} - ${appointment.ende}"
            }
        } catch (e: Exception) {
            holder.appointmentDateTime.text = "${appointment.start} - ${appointment.ende}"
        }

        // Set click listener to open detail activity
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, AppointmentDetailActivity::class.java)
            intent.putExtra("appointment", appointment)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = appointments.size
}


