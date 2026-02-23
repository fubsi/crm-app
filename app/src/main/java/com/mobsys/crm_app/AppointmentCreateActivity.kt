package com.mobsys.crm_app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.mobsys.crm_app.model.ArtType
import com.mobsys.crm_app.model.Contact
import com.mobsys.crm_app.model.ContactsResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.*

@Serializable
data class AppointmentTypesResponse(
    @SerialName("appointment_types")
    val types: List<ArtType>,
    val count: Int
)

class AppointmentCreateActivity : AppCompatActivity() {

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

    private lateinit var auth: FirebaseAuth
    private var appointmentTypes: List<ArtType> = emptyList()
    private var selectedTypeId: Int? = null

    private var contacts: List<Contact> = emptyList()
    private var selectedParticipants: MutableList<Contact> = mutableListOf()

    private var startDateCalendar: Calendar = Calendar.getInstance()
    private var endDateCalendar: Calendar = Calendar.getInstance()

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMANY)
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMANY).apply {
        timeZone = TimeZone.getTimeZone("Europe/Berlin")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_appointment_create)

        auth = FirebaseAuth.getInstance()

        // Setup toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.appointment_create_title)

        // Apply status bar inset padding (edge-to-edge)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top)
            insets
        }

        // Load appointment types
        loadAppointmentTypes()

        // Add delay before loading contacts to avoid rate limiting
        CoroutineScope(Dispatchers.IO).launch {
            delay(500) // Wait 500ms before loading contacts
            loadContacts()
        }

        // Setup date/time pickers
        setupDateTimePickers()

        // Setup save button
        setupSaveButton()
    }

    private fun loadAppointmentTypes() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("AppointmentCreate", "Fetching appointment types")
                val response: AppointmentTypesResponse = httpClient.get("${getString(R.string.api_base_url)}/api/terminart").body()
                Log.d("AppointmentCreate", "Successfully fetched ${response.count} appointment types")

                appointmentTypes = response.types

                // Add small delay to avoid rate limiting
                delay(300)

                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    setupTypeSpinner()
                }
            } catch (e: Exception) {
                Log.e("AppointmentCreate", "Error loading appointment types", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AppointmentCreateActivity,
                        "Fehler beim Laden der Terminarten",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun setupTypeSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinner_appointment_type)

        val typeNames = appointmentTypes.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, typeNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedTypeId = appointmentTypes[position].id
                Log.d("AppointmentCreate", "Selected type: ${appointmentTypes[position].name} (ID: ${appointmentTypes[position].id})")
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedTypeId = null
            }
        }
    }

    private fun loadContacts() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("AppointmentCreate", "Fetching contacts from /api/kontakt")

                // Add delay before making request to avoid rate limiting
                delay(200)

                // Get raw response first to see what we're getting
                val httpResponse = httpClient.get("${getString(R.string.api_base_url)}/api/kontakt")
                val responseBody = httpResponse.bodyAsText()
                Log.d("AppointmentCreate", "Raw response: $responseBody")
                Log.d("AppointmentCreate", "Response status: ${httpResponse.status}")

                // Check if response is successful
                if (!httpResponse.status.isSuccess()) {
                    Log.e("AppointmentCreate", "Backend returned error status: ${httpResponse.status}")
                    Log.e("AppointmentCreate", "Error response body: $responseBody")

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@AppointmentCreateActivity,
                            "Backend-Fehler beim Laden der Kontakte (Status: ${httpResponse.status.value})",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                // Add small delay before parsing
                delay(100)

                // Try to parse the response
                val response: ContactsResponse = httpClient.get("${getString(R.string.api_base_url)}/api/kontakt").body()
                Log.d("AppointmentCreate", "Successfully fetched ${response.count} contacts")

                contacts = response.contacts

                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    setupContactSpinner()
                }
            } catch (e: Exception) {
                Log.e("AppointmentCreate", "Error loading contacts", e)
                Log.e("AppointmentCreate", "Exception details: ${e.message}")
                Log.e("AppointmentCreate", "Exception cause: ${e.cause}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AppointmentCreateActivity,
                        "Fehler beim Laden der Kontakte: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun setupContactSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinner_contacts)
        val addButton = findViewById<MaterialButton>(R.id.btn_add_participant)

        // Add placeholder at beginning
        val contactNames = listOf("Kontakt auswählen...") + contacts.map { it.displayName }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, contactNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        addButton.setOnClickListener {
            val selectedPosition = spinner.selectedItemPosition

            if (selectedPosition == 0) {
                // First item is placeholder
                Toast.makeText(this, getString(R.string.select_contact_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val contact = contacts[selectedPosition - 1] // -1 because of placeholder

            // Check if already added
            if (selectedParticipants.any { it.id == contact.id }) {
                Toast.makeText(this, getString(R.string.participant_already_added), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Add to list
            selectedParticipants.add(contact)
            updateParticipantsList()

            // Reset spinner to placeholder
            spinner.setSelection(0)
        }
    }

    private fun updateParticipantsList() {
        val container = findViewById<LinearLayout>(R.id.selected_participants_container)
        container.removeAllViews()

        selectedParticipants.forEach { contact ->
            val participantView = LayoutInflater.from(this).inflate(
                android.R.layout.simple_list_item_2,
                container,
                false
            ).apply {
                findViewById<TextView>(android.R.id.text1).text = contact.rolle
                findViewById<TextView>(android.R.id.text2).text = contact.email

                // Add remove functionality
                setOnClickListener {
                    selectedParticipants.remove(contact)
                    updateParticipantsList()
                }

                // Make it look clickable
                isClickable = true
                isFocusable = true
                background = getDrawable(android.R.drawable.list_selector_background)
            }

            container.addView(participantView)
        }
    }

    private fun setupDateTimePickers() {
        val startDateInput = findViewById<TextInputEditText>(R.id.input_start_date)
        val startTimeInput = findViewById<TextInputEditText>(R.id.input_start_time)
        val endDateInput = findViewById<TextInputEditText>(R.id.input_end_date)
        val endTimeInput = findViewById<TextInputEditText>(R.id.input_end_time)

        // Start Date Picker
        startDateInput.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    startDateCalendar.set(year, month, dayOfMonth)
                    startDateInput.setText(dateFormat.format(startDateCalendar.time))
                },
                startDateCalendar.get(Calendar.YEAR),
                startDateCalendar.get(Calendar.MONTH),
                startDateCalendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // Start Time Picker
        startTimeInput.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    startDateCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    startDateCalendar.set(Calendar.MINUTE, minute)
                    startTimeInput.setText(timeFormat.format(startDateCalendar.time))
                },
                startDateCalendar.get(Calendar.HOUR_OF_DAY),
                startDateCalendar.get(Calendar.MINUTE),
                true
            ).show()
        }

        // End Date Picker
        endDateInput.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    endDateCalendar.set(year, month, dayOfMonth)
                    endDateInput.setText(dateFormat.format(endDateCalendar.time))
                },
                endDateCalendar.get(Calendar.YEAR),
                endDateCalendar.get(Calendar.MONTH),
                endDateCalendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // End Time Picker
        endTimeInput.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    endDateCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    endDateCalendar.set(Calendar.MINUTE, minute)
                    endTimeInput.setText(timeFormat.format(endDateCalendar.time))
                },
                endDateCalendar.get(Calendar.HOUR_OF_DAY),
                endDateCalendar.get(Calendar.MINUTE),
                true
            ).show()
        }
    }

    private fun setupSaveButton() {
        val saveButton = findViewById<MaterialButton>(R.id.btn_save_appointment)
        val titleInput = findViewById<TextInputEditText>(R.id.input_appointment_title)
        val locationInput = findViewById<TextInputEditText>(R.id.input_appointment_location)
        val startDateInput = findViewById<TextInputEditText>(R.id.input_start_date)
        val startTimeInput = findViewById<TextInputEditText>(R.id.input_start_time)
        val endDateInput = findViewById<TextInputEditText>(R.id.input_end_date)
        val endTimeInput = findViewById<TextInputEditText>(R.id.input_end_time)

        saveButton.setOnClickListener {
            val title = titleInput.text?.toString()?.trim() ?: ""
            val location = locationInput.text?.toString()?.trim() ?: ""
            val startDate = startDateInput.text?.toString()?.trim() ?: ""
            val startTime = startTimeInput.text?.toString()?.trim() ?: ""
            val endDate = endDateInput.text?.toString()?.trim() ?: ""
            val endTime = endTimeInput.text?.toString()?.trim() ?: ""

            // Validation
            if (selectedTypeId == null) {
                Toast.makeText(this, getString(R.string.error_no_type), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (location.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_empty_location), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (startDate.isEmpty() || startTime.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_empty_start), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (endDate.isEmpty() || endTime.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_empty_end), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save appointment
            saveAppointment(title, location)
        }
    }

    private fun saveAppointment(title: String, location: String) {
        val saveButton = findViewById<MaterialButton>(R.id.btn_save_appointment)
        val currentUser = auth.currentUser

        if (currentUser == null) {
            Toast.makeText(this, "Nicht angemeldet", Toast.LENGTH_SHORT).show()
            return
        }

        // Disable button during saving
        saveButton.isEnabled = false
        saveButton.text = "Speichert..."

        // Format dates to ISO format
        val startDateTime = isoFormat.format(startDateCalendar.time)
        val endDateTime = isoFormat.format(endDateCalendar.time)

        // Perform network request in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("AppointmentCreate", "Saving appointment")

                val requestBody = buildJsonObject {
                    put("art_id", selectedTypeId!!)
                    put("start", startDateTime)
                    put("ende", endDateTime)
                    put("ort", location)
                    put("title", title)
                    put("uid", currentUser.uid)
                }

                Log.d("AppointmentCreate", "Request body: $requestBody")

                val response: HttpResponse = httpClient.post("${getString(R.string.api_base_url)}/api/termine") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody.toString())
                }

                if (response.status.isSuccess()) {
                    Log.d("AppointmentCreate", "Appointment saved successfully")

                    // Get the response body to extract the created appointment ID
                    val responseBody = response.bodyAsText()
                    Log.d("AppointmentCreate", "Response body: $responseBody")

                    // Try to extract termin_id from response
                    var terminId: Int? = null
                    try {
                        val jsonResponse = Json.parseToJsonElement(responseBody).jsonObject
                        terminId = jsonResponse["id"]?.toString()?.toIntOrNull()
                        Log.d("AppointmentCreate", "Extracted termin_id: $terminId")
                    } catch (e: Exception) {
                        Log.e("AppointmentCreate", "Error extracting termin_id from response", e)
                    }

                    // Save participants if any and we have termin_id
                    if (selectedParticipants.isNotEmpty() && terminId != null) {
                        Log.d("AppointmentCreate", "Saving ${selectedParticipants.size} participants for termin_id: $terminId")
                        saveParticipants(terminId)
                        saveOrder(terminId)
                        Log.d("AppointmentCreate", "Saving dummy order for termin_id: $terminId")

                    } else if (selectedParticipants.isNotEmpty()) {
                        Log.w("AppointmentCreate", "Cannot save participants: termin_id not found in response")
                    }

                    withContext(Dispatchers.Main) {
                        saveButton.isEnabled = true
                        saveButton.text = getString(R.string.btn_save_appointment)
                        Toast.makeText(
                            this@AppointmentCreateActivity,
                            getString(R.string.appointment_saved_success),
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                } else {
                    Log.e("AppointmentCreate", "Failed to save appointment: ${response.status}")
                    withContext(Dispatchers.Main) {
                        saveButton.isEnabled = true
                        saveButton.text = getString(R.string.btn_save_appointment)
                        Toast.makeText(
                            this@AppointmentCreateActivity,
                            getString(R.string.appointment_save_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("AppointmentCreate", "Error saving appointment", e)

                // Update UI on main thread - show error
                withContext(Dispatchers.Main) {
                    saveButton.isEnabled = true
                    saveButton.text = getString(R.string.btn_save_appointment)
                    Toast.makeText(
                        this@AppointmentCreateActivity,
                        getString(R.string.appointment_save_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private suspend fun saveParticipants(terminId: Int) {
        selectedParticipants.forEachIndexed { index, contact ->
            try {
                // Add delay between participant saves to avoid rate limiting
                if (index > 0) {
                    delay(200)
                }

                val participantBody = buildJsonObject {
                    put("kontakt_id", contact.id)
                    put("termin_id", terminId)
                    put("istHaupt", index == 0) // First participant is main contact
                }

                Log.d("AppointmentCreate", "Saving participant: ${contact.rolle} (ID: ${contact.id})")

                val participantResponse: HttpResponse = httpClient.post("${getString(R.string.api_base_url)}/api/teilnehmer") {
                    contentType(ContentType.Application.Json)
                    setBody(participantBody.toString())
                }

                if (participantResponse.status.isSuccess()) {
                    Log.d("AppointmentCreate", "Participant saved successfully: ${contact.rolle}")
                } else {
                    Log.e("AppointmentCreate", "Failed to save participant: ${participantResponse.status}")
                }
            } catch (e: Exception) {
                Log.e("AppointmentCreate", "Error saving participant: ${contact.rolle}", e)
            }
        }
    }

    private suspend fun saveOrder(terminId: Int) {
        val orderParticipant = selectedParticipants[0]
        try {


            val orderBody = buildJsonObject {
                put("bezeichnung", "Termin ${terminId}")
                put("wichtigkeit_id", 2) // Placeholter for "normal" importance
                put("kontakt_id", orderParticipant.id) // participant
                put("termin_id", terminId) // associated appointment
            }

            Log.d("AppointmentCreate", "Saving order: ${orderBody.get("bezeichnung")}")

            val participantResponse: HttpResponse = httpClient.post("${getString(R.string.api_base_url)}/api/auftrag") {
                contentType(ContentType.Application.Json)
                setBody(orderBody.toString())
            }

            if (participantResponse.status.isSuccess()) {
                Log.d("AppointmentCreate", "Order saved successfully: ${orderBody.get("bezeichnung")}")
            } else {
                Log.e("AppointmentCreate", "Failed to save order: ${participantResponse.status}")
            }
        } catch (e: Exception) {
            Log.e("AppointmentCreate", "Error saving order for ${terminId}", e)
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

    override fun onDestroy() {
        super.onDestroy()
        httpClient.close()
    }
}




