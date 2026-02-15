package com.mobsys.crm_app

import android.content.Intent
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
import com.google.android.material.button.MaterialButton
import com.mobsys.crm_app.model.Protocol
import java.text.SimpleDateFormat
import java.util.*

class ProtocolDetailActivity : AppCompatActivity() {

    private val dateTimeFormat = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.GERMANY).apply {
        timeZone = TimeZone.getTimeZone("Europe/Berlin")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_protocol_detail)

        // Setup toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.protocol_detail_title)

        // Apply status bar inset padding (edge-to-edge)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top)
            insets
        }

        // Get protocol from intent
        val protocol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("protocol", Protocol::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("protocol")
        }

        if (protocol == null) {
            Log.e("ProtocolDetail", "No protocol data provided")
            finish()
            return
        }

        // Display protocol details
        displayProtocolDetails(protocol)

        // Setup edit button
        setupEditButton(protocol)
    }

    private fun displayProtocolDetails(protocol: Protocol) {
        // Find views
        val tldrTextView = findViewById<TextView>(R.id.protocol_tldr)
        val dateTextView = findViewById<TextView>(R.id.protocol_date)
        val durationTextView = findViewById<TextView>(R.id.protocol_duration)
        val textTextView = findViewById<TextView>(R.id.protocol_text)

        // Set values
        tldrTextView.text = protocol.tldr
        textTextView.text = protocol.text
        durationTextView.text = getString(R.string.protocol_duration_format, protocol.dauer)

        // Format datetime
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMANY).apply {
                timeZone = TimeZone.getTimeZone("Europe/Berlin")
            }

            val date = inputFormat.parse(protocol.datum)
            if (date != null) {
                dateTextView.text = dateTimeFormat.format(date)
            } else {
                dateTextView.text = protocol.datum
            }
        } catch (e: Exception) {
            Log.e("ProtocolDetail", "Error formatting date", e)
            dateTextView.text = protocol.datum
        }
    }

    private fun setupEditButton(protocol: Protocol) {
        val editButton = findViewById<MaterialButton>(R.id.btn_edit_protocol)

        editButton.setOnClickListener {
            // Open ProtocolCreateActivity in edit mode
            val intent = Intent(this, ProtocolCreateActivity::class.java)
            intent.putExtra("protocol", protocol)
            startActivity(intent)
            // Close this activity so user returns to appointment detail after saving
            finish()
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

