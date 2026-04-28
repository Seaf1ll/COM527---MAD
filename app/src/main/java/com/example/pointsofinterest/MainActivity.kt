package com.example.Q102009411

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.app.Activity
import androidx.core.app.ActivityCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MainActivity : Activity(), LocationListener {

    private lateinit var map: MapView
    private lateinit var locationManager: LocationManager
    private var userMarker: Marker? = null

    private val permissionCode = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_main)

        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(18.0)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        checkLocationPermission()
    }

    private fun checkLocationPermission() {
        val permissionGranted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {
            startLocationUpdates()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                permissionCode
            )
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            5000,
            5f,
            this
        )

        val lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

        if (lastLocation != null) {
            updateMapLocation(lastLocation)
        }
    }

    override fun onLocationChanged(location: Location) {
        updateMapLocation(location)
    }

    private fun updateMapLocation(location: Location) {
        val userPoint = GeoPoint(location.latitude, location.longitude)

        map.controller.animateTo(userPoint)

        if (userMarker == null) {
            userMarker = Marker(map)
            userMarker!!.title = "You are here"
            map.overlays.add(userMarker)
        }
        userMarker!!.position = userPoint
        map.invalidate()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == permissionCode &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        locationManager.removeUpdates(this)
    }
}