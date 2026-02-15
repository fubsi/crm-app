package com.mobsys.crm_app

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.mobsys.crm_app.model.Appointment
import java.text.SimpleDateFormat
import java.util.*

class AppointmentDetailActivity : AppCompatActivity() {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMANY).apply {
        timeZone = TimeZone.getTimeZone("Europe/Berlin")
    }

    private val dateOnlyFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).apply {
        timeZone = TimeZone.getTimeZone("Europe/Berlin")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_appointment_detail)

        // Setup toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.appointment_detail_title)

        // Apply status bar inset padding (edge-to-edge)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top)
            insets
        }

        // Get appointment from intent
        val appointment = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("appointment", Appointment::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("appointment")
        }

        if (appointment == null) {
            Log.e("AppointmentDetail", "No appointment data provided")
            finish()
            return
        }

        // Display appointment details
        displayAppointmentDetails(appointment)
    }

    private fun displayAppointmentDetails(appointment: Appointment) {
        // Set appointment type as title
        supportActionBar?.title = appointment.art.name

        // Find views
        val typeTextView = findViewById<TextView>(R.id.detail_appointment_type)
        val titleTextView = findViewById<TextView>(R.id.detail_appointment_title)
        val dateTimeTextView = findViewById<TextView>(R.id.detail_appointment_datetime)
        val locationTextView = findViewById<TextView>(R.id.detail_appointment_location)
        val idTextView = findViewById<TextView>(R.id.detail_appointment_id)

        // Set values
        typeTextView.text = appointment.art.name
        titleTextView.text = appointment.title
        locationTextView.text = appointment.ort
        idTextView.text = appointment.id.toString()

        // Format datetime - same as in AppointmentAdapter
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

                dateTimeTextView.text = "$dateStr, $startTimeStr - $endTimeStr Uhr"
            } else {
                dateTimeTextView.text = "${appointment.start} - ${appointment.ende}"
            }
        } catch (e: Exception) {
            Log.e("AppointmentDetail", "Error formatting date", e)
            dateTimeTextView.text = "${appointment.start} - ${appointment.ende}"
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

