package com.mobsys.crm_app

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.android.material.navigation.NavigationView
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.mobsys.crm_app.adapter.AppointmentAdapter
import com.mobsys.crm_app.model.AppointmentsResponse
import com.mobsys.crm_app.cache.AppointmentCache
import com.mobsys.crm_app.database.AppDatabase
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.plugins.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {



    private val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract(),
    ) { res ->
        this.onSignInResult(res)
    }
    private lateinit var auth : FirebaseAuth

    // Drawer-related
    private var drawerLayout: DrawerLayout? = null
    private var toggle: ActionBarDrawerToggle? = null

    // Offline cache
    private lateinit var appointmentCache: AppointmentCache

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
        setContentView(R.layout.activity_main)
        FirebaseApp.initializeApp(this)

        // Initialize offline cache
        appointmentCache = AppointmentCache(AppDatabase.getInstance(this))

        // Initialize Firebase Auth and skip sign-in if already signed in
        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        setupDrawer()

        if (currentUser != null) {
            Log.d("Authentication", "Already signed in: ${currentUser.email}")
            Log.d("Authentication", "User UID: ${currentUser.uid}")
            // Update header with user info
            updateNavHeader(currentUser.email)
            // User is signed in — proceed with app logic
            performNetworkRequest()
        } else {
            Log.d("Authentication", "No user signed in, showing sign-in screen")
            createSignInIntent()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh appointments when returning to this activity
        val currentUser = auth.currentUser
        if (currentUser != null) {
            performNetworkRequest()
        }
    }

    private fun setupDrawer() {
        // Find views
        drawerLayout = findViewById(R.id.drawer_layout)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        val content = findViewById<View>(R.id.content_frame)

        // Setup toolbar as ActionBar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Apply status bar inset padding (edge-to-edge)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top)
            insets
        }

        // Ensure nav header is pushed below status bar as well
        val headerView = navigationView.getHeaderView(0)
        headerView?.let { hv ->
            ViewCompat.setOnApplyWindowInsetsListener(hv) { v, insets ->
                val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                v.updatePadding(top = statusBars.top)
                insets
            }
        }

        // Optionally keep content_frame below toolbar (usually not needed if toolbar height already accounted)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            // keep content top at 0 so toolbar overlaps content as usual; remove or adjust if needed:
            v.updatePadding(top = 0)
            insets
        }

        // Setup toggle
        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout?.addDrawerListener(toggle!!)
        toggle?.syncState()

        // Navigation listener
        navigationView.setNavigationItemSelectedListener(this)
    }


    private fun updateNavHeader(email: String?) {
        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        val header: View? = navigationView.getHeaderView(0)
        val emailView = header?.findViewById<TextView>(R.id.nav_header_email)
        emailView?.text = email ?: getString(R.string.nav_header_title)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_add_appointment -> {
                // Open AppointmentCreateActivity
                val intent = Intent(this, AppointmentCreateActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_logout -> {
                // Perform sign out
                AuthUI.getInstance().signOut(this).addOnCompleteListener {
                    Log.d("Authentication", "User signed out")
                    // After sign-out, start sign-in flow again
                    createSignInIntent()
                }
            }
        }
        // close drawer
        drawerLayout?.closeDrawers()
        return true
    }

    private fun createSignInIntent() {
        // [START auth_fui_create_intent]
        // Choose authentication providers
        val providers = arrayListOf(
            AuthUI.IdpConfig.EmailBuilder().build(),
        )

        // Create and launch sign-in intent
        val signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .setTheme(R.style.LoginStyle)
            .build()
        signInLauncher.launch(signInIntent)
        // [END auth_fui_create_intent]
    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        val response = result.idpResponse
        if (result.resultCode == RESULT_OK) {
            // Successfully signed in
            auth = FirebaseAuth.getInstance()
            val user = auth.currentUser
            Log.d("Authentication", "User signed in: ${user?.email}")
            Log.d("Authentication", "User UID: ${user?.uid}")

            // Update header after sign-in
            updateNavHeader(user?.email)

            // Fetch appointments after successful sign-in
            performNetworkRequest()
        } else {
            // Sign in failed. If response is null the user canceled the
            // sign-in flow using the back button. Otherwise check
            // response.getError().getErrorCode() and handle the error.
            Log.d("Authentication", "Sign-in failed: $response")
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        // Sync toggle state after onRestoreInstanceState has occurred.
        toggle?.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        toggle?.onConfigurationChanged(newConfig)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Let the toggle handle the home button
        if (toggle?.onOptionsItemSelected(item) == true) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun performNetworkRequest() {
        val loadingSpinner = findViewById<ProgressBar>(R.id.loading_spinner)
        val recyclerView = findViewById<RecyclerView>(R.id.appointments_recycler_view)

        // Show loading spinner
        loadingSpinner.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        // Get current user's UID
        val currentUser = auth.currentUser
        val currentUid = currentUser?.uid

        if (currentUid == null) {
            Log.e("HTTP Client", "No user logged in, cannot fetch appointments")
            // Hide loading spinner and show empty list
            loadingSpinner.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
            recyclerView.adapter = AppointmentAdapter(emptyList())
            return
        }

        Log.d("HTTP Client", "Fetching appointments for UID: $currentUid")

        // Perform network request in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Add delay to avoid rate limiting on initial load
                delay(250)

                val response: AppointmentsResponse = httpClient.get("http://192.168.2.34:5000/api/termine").body()
                Log.d("HTTP Client", "Successfully fetched ${response.count} appointments from API")

                // Log all appointments with their UIDs for debugging
                response.appointments.forEachIndexed { index, appointment ->
                    Log.d("HTTP Client", "Appointment $index: ID=${appointment.id}, UID='${appointment.uid}', Title='${appointment.title}'")
                }
                Log.d("HTTP Client", "Current user UID: '$currentUid'")

                // Filter appointments by current user's UID
                val filteredAppointments = response.appointments.filter { appointment ->
                    val matches = appointment.uid == currentUid
                    Log.d("HTTP Client", "Comparing '${appointment.uid}' == '$currentUid': $matches")
                    matches
                }
                Log.d("HTTP Client", "Filtered to ${filteredAppointments.size} appointments for current user")

                // Persist fetched appointments to cache
                appointmentCache.updateCache(currentUid, filteredAppointments)

                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    // Hide loading spinner
                    loadingSpinner.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE

                    // Setup RecyclerView with filtered appointments
                    recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
                    recyclerView.adapter = AppointmentAdapter(filteredAppointments)
                }
            } catch (e: Exception) {
                Log.e("HTTP Client", "Error performing network request — trying offline cache", e)

                // Load cached appointments as fallback
                val cachedAppointments = appointmentCache.getCachedAppointments(currentUid)
                Log.d("HTTP Client", "Loaded ${cachedAppointments.size} appointments from cache")

                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    loadingSpinner.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
                    recyclerView.adapter = AppointmentAdapter(cachedAppointments)
                }
            }
        }
    }

}