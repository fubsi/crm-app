package com.mobsys.crm_app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
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

        // Setup date/time pickers
        setupDateTimePickers()

        // Setup save button
        setupSaveButton()
    }

    private fun loadAppointmentTypes() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("AppointmentCreate", "Fetching appointment types")
                val response: AppointmentTypesResponse = httpClient.get("http://192.168.2.34:5000/api/terminart").body()
                Log.d("AppointmentCreate", "Successfully fetched ${response.count} appointment types")

                appointmentTypes = response.types

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

                val response: HttpResponse = httpClient.post("http://192.168.2.34:5000/api/termine") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody.toString())
                }

                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    saveButton.isEnabled = true
                    saveButton.text = getString(R.string.btn_save_appointment)

                    if (response.status.isSuccess()) {
                        Log.d("AppointmentCreate", "Appointment saved successfully")
                        Toast.makeText(
                            this@AppointmentCreateActivity,
                            getString(R.string.appointment_saved_success),
                            Toast.LENGTH_SHORT
                        ).show()
                        // Close activity and return to main view
                        finish()
                    } else {
                        Log.e("AppointmentCreate", "Failed to save appointment: ${response.status}")
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




