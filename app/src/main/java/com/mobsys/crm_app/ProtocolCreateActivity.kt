package com.mobsys.crm_app

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.mobsys.crm_app.model.Protocol
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.*

class ProtocolCreateActivity : AppCompatActivity() {

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

    private var terminId: Int = -1
    private var existingProtocol: Protocol? = null
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_protocol_create)

        // Check if editing existing protocol
        existingProtocol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("protocol", Protocol::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("protocol")
        }

        isEditMode = existingProtocol != null

        // Setup toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(
            if (isEditMode) R.string.protocol_edit_title else R.string.protocol_create_title
        )

        // Apply status bar inset padding (edge-to-edge)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top)
            insets
        }

        // Get termin_id from intent or existing protocol
        terminId = if (isEditMode) {
            existingProtocol!!.terminId
        } else {
            intent.getIntExtra("termin_id", -1)
        }

        if (terminId == -1) {
            Log.e("ProtocolCreate", "No termin_id provided")
            Toast.makeText(this, "Fehler: Keine Termin-ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Load existing data if in edit mode
        if (isEditMode) {
            loadExistingData()
        }

        // Setup save button
        setupSaveButton()
    }

    private fun loadExistingData() {
        existingProtocol?.let { protocol ->
            val tldrInput = findViewById<TextInputEditText>(R.id.input_protocol_tldr)
            val durationInput = findViewById<TextInputEditText>(R.id.input_protocol_duration)
            val textInput = findViewById<TextInputEditText>(R.id.input_protocol_text)

            tldrInput.setText(protocol.tldr)
            durationInput.setText(protocol.dauer.toString())
            textInput.setText(protocol.text)
        }
    }

    private fun setupSaveButton() {
        val saveButton = findViewById<MaterialButton>(R.id.btn_save_protocol)
        val tldrInput = findViewById<TextInputEditText>(R.id.input_protocol_tldr)
        val durationInput = findViewById<TextInputEditText>(R.id.input_protocol_duration)
        val textInput = findViewById<TextInputEditText>(R.id.input_protocol_text)

        saveButton.setOnClickListener {
            val tldr = tldrInput.text?.toString()?.trim() ?: ""
            val durationStr = durationInput.text?.toString()?.trim() ?: ""
            val text = textInput.text?.toString()?.trim() ?: ""

            // Validation
            if (tldr.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_empty_tldr), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (durationStr.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_empty_duration), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (text.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_empty_text), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val duration = try {
                durationStr.toInt()
            } catch (_: NumberFormatException) {
                Toast.makeText(this, "Ungültige Dauer", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save protocol
            saveProtocol(tldr, duration, text)
        }
    }

    private fun saveProtocol(tldr: String, duration: Int, text: String) {
        val saveButton = findViewById<MaterialButton>(R.id.btn_save_protocol)

        // Disable button during saving
        saveButton.isEnabled = false
        saveButton.text = "Speichert..."

        // Get current date/time in ISO format
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMANY).apply {
            timeZone = TimeZone.getTimeZone("Europe/Berlin")
        }
        val currentDateTime = dateFormat.format(Date())

        // Perform network request in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (isEditMode) {
                    // Update existing protocol
                    Log.d("ProtocolCreate", "Updating protocol id: ${existingProtocol!!.id}")

                    val requestBody = buildJsonObject {
                        put("termin_id", terminId)
                        put("datum", currentDateTime)
                        put("dauer", duration)
                        put("tldr", tldr)
                        put("text", text)
                    }

                    val response: HttpResponse = httpClient.put("http://192.168.2.34:5000/api/protokoll/${existingProtocol!!.id}") {
                        contentType(ContentType.Application.Json)
                        setBody(requestBody.toString())
                    }

                    // Update UI on main thread
                    withContext(Dispatchers.Main) {
                        saveButton.isEnabled = true
                        saveButton.text = getString(R.string.btn_save_protocol)

                        if (response.status.isSuccess()) {
                            Log.d("ProtocolCreate", "Protocol updated successfully")
                            Toast.makeText(
                                this@ProtocolCreateActivity,
                                getString(R.string.protocol_updated_success),
                                Toast.LENGTH_SHORT
                            ).show()
                            // Close activity and return to detail view
                            finish()
                        } else {
                            Log.e("ProtocolCreate", "Failed to update protocol: ${response.status}")
                            Toast.makeText(
                                this@ProtocolCreateActivity,
                                getString(R.string.protocol_save_error),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    // Create new protocol
                    Log.d("ProtocolCreate", "Saving protocol for termin_id: $terminId")

                    val requestBody = buildJsonObject {
                        put("termin_id", terminId)
                        put("datum", currentDateTime)
                        put("dauer", duration)
                        put("tldr", tldr)
                        put("text", text)
                    }

                    val response: HttpResponse = httpClient.post("http://192.168.2.34:5000/api/protokoll") {
                        contentType(ContentType.Application.Json)
                        setBody(requestBody.toString())
                    }

                    // Update UI on main thread
                    withContext(Dispatchers.Main) {
                        saveButton.isEnabled = true
                        saveButton.text = getString(R.string.btn_save_protocol)

                        if (response.status.isSuccess()) {
                            Log.d("ProtocolCreate", "Protocol saved successfully")
                            Toast.makeText(
                                this@ProtocolCreateActivity,
                                getString(R.string.protocol_saved_success),
                                Toast.LENGTH_SHORT
                            ).show()
                            // Close activity and return to detail view
                            finish()
                        } else {
                            Log.e("ProtocolCreate", "Failed to save protocol: ${response.status}")
                            Toast.makeText(
                                this@ProtocolCreateActivity,
                                getString(R.string.protocol_save_error),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ProtocolCreate", "Error saving protocol", e)

                // Update UI on main thread - show error
                withContext(Dispatchers.Main) {
                    saveButton.isEnabled = true
                    saveButton.text = getString(R.string.btn_save_protocol)
                    Toast.makeText(
                        this@ProtocolCreateActivity,
                        getString(R.string.protocol_save_error),
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


