package com.example.barcenaslacactivity42

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.CancellationTokenSource

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var parkedButton: Button
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var googleMap: GoogleMap? = null
    private var carMarker: Marker? = null

    // Register ActivityResultLauncher for location permission request
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                onLocationPermissionGranted()
            } else {
                Toast.makeText(
                    this,
                    "Location permission denied. Map positioning and parking saving are limited.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Adjust bottom button spacing for system insets (edge-to-edge)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val button = findViewById<Button>(R.id.parkedButton)
            val params = button.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = systemBars.bottom + 32
            button.layoutParams = params
            insets
        }

        parkedButton = findViewById(R.id.parkedButton)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize Google Maps SupportMapFragment
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Handle "I'm parked here" button click
        parkedButton.setOnClickListener {
            handleParkedButtonClick()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.uiSettings?.isZoomControlsEnabled = true

        // Restore saved parking location on startup if present
        restoreLocation()?.let { savedLocation ->
            updateCarMarker(savedLocation)
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(savedLocation, 17f))
        }

        // Check location permission once map is ready
        checkAndRequestLocationPermission()
    }

    // Runtime location permission check with rationale dialog
    private fun checkAndRequestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED -> {
                onLocationPermissionGranted()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                AlertDialog.Builder(this)
                    .setTitle("Location Permission Needed")
                    .setMessage("This app needs location access to display your location on the map and mark where your car is parked.")
                    .setPositiveButton("Grant") { _, _ ->
                        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                        Toast.makeText(this, "Permission request cancelled.", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    // Enable MyLocation layer and center map on user
    @SuppressLint("MissingPermission")
    private fun onLocationPermissionGranted() {
        googleMap?.isMyLocationEnabled = true

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val userLatLng = LatLng(location.latitude, location.longitude)
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 17f))
            } else {
                val cancellationToken = CancellationTokenSource()
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken.token)
                    .addOnSuccessListener { currentLocation ->
                        if (currentLocation != null) {
                            val userLatLng = LatLng(currentLocation.latitude, currentLocation.longitude)
                            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 17f))
                        }
                    }
            }
        }
    }

    // Action when "I'm parked here" button is tapped
    private fun handleParkedButtonClick() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            parkCarAtCurrentLocation()
        } else {
            checkAndRequestLocationPermission()
        }
    }

    // Get current location with high accuracy, move car marker, save, and animate camera
    @SuppressLint("MissingPermission")
    private fun parkCarAtCurrentLocation() {
        val cancellationToken = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    updateCarMarker(latLng)
                    saveLocation(latLng)
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
                    Toast.makeText(this, "Parked location saved!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this,
                        "Unable to get current location. Ensure location services are enabled.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to get location: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Create or move the single car Marker instance on Google Maps
    private fun updateCarMarker(latLng: LatLng) {
        val map = googleMap ?: return
        if (carMarker == null) {
            val iconDescriptor = bitmapDescriptorFromVector(this, R.drawable.ic_car)
            val markerOptions = MarkerOptions()
                .position(latLng)
                .title("Parked Car")
                .icon(iconDescriptor)
            carMarker = map.addMarker(markerOptions)
        } else {
            carMarker?.position = latLng
        }
    }

    // Convert vector drawable asset into BitmapDescriptor for Google Maps Marker
    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor? {
        val vectorDrawable = ContextCompat.getDrawable(context, vectorResId) ?: return null
        vectorDrawable.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
        val bitmap = Bitmap.createBitmap(
            vectorDrawable.intrinsicWidth,
            vectorDrawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    // Persist car location to SharedPreferences
    private fun saveLocation(latLng: LatLng) =
        getPreferences(MODE_PRIVATE)?.edit()?.apply {
            putString("latitude", latLng.latitude.toString())
            putString("longitude", latLng.longitude.toString())
            apply()
        }

    // Restore car location from SharedPreferences
    private fun restoreLocation(): LatLng? {
        val sharedPreferences = getPreferences(MODE_PRIVATE) ?: return null
        val latitude = sharedPreferences.getString("latitude", null)?.toDoubleOrNull() ?: return null
        val longitude = sharedPreferences.getString("longitude", null)?.toDoubleOrNull() ?: return null
        return LatLng(latitude, longitude)
    }
}