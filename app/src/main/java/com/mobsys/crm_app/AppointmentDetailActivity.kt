package com.mobsys.crm_app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.mobsys.crm_app.model.Appointment
import com.mobsys.crm_app.model.ParticipantsResponse
import com.mobsys.crm_app.model.ProtocolsResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

class AppointmentDetailActivity : AppCompatActivity() {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMANY).apply {
        timeZone = TimeZone.getTimeZone("Europe/Berlin")
    }

    private val dateOnlyFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).apply {
        timeZone = TimeZone.getTimeZone("Europe/Berlin")
    }

    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15000
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
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

        // Load and display participants
        loadParticipants(appointment.id)

        // Setup protocol button with delay to avoid rate limiting
        CoroutineScope(Dispatchers.Main).launch {
            delay(800) // Wait 800ms after participants load started
            setupProtocolButton(appointment.id)
        }
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

    private fun loadParticipants(terminId: Int) {
        val loadingSpinner = findViewById<ProgressBar>(R.id.participants_loading_spinner)
        val participantsContainer = findViewById<LinearLayout>(R.id.participants_container)
        val noParticipantsText = findViewById<TextView>(R.id.no_participants_text)

        // Show loading spinner
        loadingSpinner.visibility = View.VISIBLE
        participantsContainer.visibility = View.GONE
        noParticipantsText.visibility = View.GONE

        // Perform network request in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Add delay to avoid rate limiting
                delay(300)

                Log.d("ParticipantLoader", "Fetching participants for termin_id: $terminId")
                val response: ParticipantsResponse = httpClient.get("http://192.168.2.34:5000/api/teilnehmer").body()
                Log.d("ParticipantLoader", "Successfully fetched ${response.count} participants from API")

                // Filter participants by current appointment's ID
                val filteredParticipants = response.participants.filter { participant ->
                    participant.terminId == terminId
                }
                Log.d("ParticipantLoader", "Filtered to ${filteredParticipants.size} participants for termin_id: $terminId")

                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    loadingSpinner.visibility = View.GONE

                    if (filteredParticipants.isEmpty()) {
                        noParticipantsText.visibility = View.VISIBLE
                        participantsContainer.visibility = View.GONE
                    } else {
                        noParticipantsText.visibility = View.GONE
                        participantsContainer.visibility = View.VISIBLE
                        displayParticipants(participantsContainer, filteredParticipants)
                    }
                }
            } catch (e: Exception) {
                Log.e("ParticipantLoader", "Error loading participants", e)

                // Update UI on main thread - show error/no participants
                withContext(Dispatchers.Main) {
                    loadingSpinner.visibility = View.GONE
                    noParticipantsText.visibility = View.VISIBLE
                    participantsContainer.visibility = View.GONE
                }
            }
        }
    }

    private fun displayParticipants(container: LinearLayout, participants: List<com.mobsys.crm_app.model.Participant>) {
        container.removeAllViews()

        participants.forEachIndexed { index, participant ->
            val participantView = LayoutInflater.from(this).inflate(R.layout.item_participant, container, false)

            val nameTextView = participantView.findViewById<TextView>(R.id.participant_name)
            val emailTextView = participantView.findViewById<TextView>(R.id.participant_email)
            val phoneTextView = participantView.findViewById<TextView>(R.id.participant_phone)

            nameTextView.text = participant.name
            emailTextView.text = participant.email
            phoneTextView.text = participant.telefon

            // Remove the divider from the last item
            if (index == participants.size - 1) {
                val divider = participantView.findViewById<View>(R.id.participant_divider)
                divider?.visibility = View.GONE
            }

            container.addView(participantView)
        }
    }

    private fun setupProtocolButton(terminId: Int) {
        val protocolButton = findViewById<Button>(R.id.btn_view_protocol)

        protocolButton.setOnClickListener {
            // Disable button during loading
            protocolButton.isEnabled = false
            protocolButton.text = "Lädt..."

            // Load protocol in background
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Add delay to avoid rate limiting
                    delay(400)

                    Log.d("ProtocolLoader", "Fetching protocols for termin_id: $terminId")
                    val response: ProtocolsResponse = httpClient.get("http://192.168.2.34:5000/api/protokoll").body()
                    Log.d("ProtocolLoader", "Successfully fetched ${response.count} protocols from API")

                    // Filter protocol by current appointment's ID
                    val protocol = response.protocols.find { it.terminId == terminId }

                    // Update UI on main thread
                    withContext(Dispatchers.Main) {
                        protocolButton.isEnabled = true
                        protocolButton.text = getString(R.string.btn_view_protocol)

                        if (protocol != null) {
                            Log.d("ProtocolLoader", "Found protocol with id: ${protocol.id}")
                            // Open protocol detail activity
                            val intent = Intent(this@AppointmentDetailActivity, ProtocolDetailActivity::class.java)
                            intent.putExtra("protocol", protocol)
                            startActivity(intent)
                        } else {
                            Log.w("ProtocolLoader", "No protocol found for termin_id: $terminId")
                            // Open protocol create activity to add a new protocol
                            val intent = Intent(this@AppointmentDetailActivity, ProtocolCreateActivity::class.java)
                            intent.putExtra("termin_id", terminId)
                            startActivity(intent)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ProtocolLoader", "Error loading protocol", e)

                    // Update UI on main thread - show error
                    withContext(Dispatchers.Main) {
                        protocolButton.isEnabled = true
                        protocolButton.text = getString(R.string.btn_view_protocol)
                        Toast.makeText(
                            this@AppointmentDetailActivity,
                            "Fehler beim Laden des Protokolls",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
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

