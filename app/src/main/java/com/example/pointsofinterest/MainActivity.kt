package com.example.Q102009411

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL

class MainActivity : ComponentActivity(), LocationListener {

    private lateinit var locationManager: LocationManager

    private lateinit var databaseHelper: PoiDatabaseHelper
    private var currentLocation: Location? = null
    private var mapView: MapView? = null
    private var userMarker: Marker? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startLocationUpdates()
            }
        }
    private val prefsName = "poi_preferences"

    data class PointOfInterest(
        val name: String,
        val type: String,
        val description: String,
        val latitude: Double,
        val longitude: Double
    )

    private val poiList = mutableStateListOf<PointOfInterest>()
    private val allPois = mutableStateListOf<PointOfInterest>()
    private val webPoiUrl = "http://192.168.1.43:3000/poi/all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().userAgentValue = packageName
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        databaseHelper = PoiDatabaseHelper(this)
        allPois.addAll(databaseHelper.getAllPois())
        poiList.addAll(allPois)
        checkLocationPermission()

        setContent {
            val navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = "map"
            ) {
                composable("map") {
                    MapScreen(
                        onAddPoiClick = {
                            navController.navigate("addPoi")
                        },
                        onSearchPoiClick = {
                            navController.navigate("searchPoi")
                        }
                    )
                }

                composable("addPoi") {
                    AddPoiScreen(
                        onSavePoi = { name, type, description ->
                            val location = currentLocation

                            if (location != null) {
                                val newPoi = PointOfInterest(
                                    name = name,
                                    type = type,
                                    description = description,
                                    latitude = location.latitude,
                                    longitude = location.longitude
                                )
                                poiList.add(newPoi)
                                allPois.add(newPoi)

                                databaseHelper.addPoi(
                                    name,
                                    type,
                                    description,
                                    location.latitude,
                                    location.longitude
                                )
                            }
                            navController.popBackStack()
                        },
                        onBackClick = {
                            navController.popBackStack()
                        }
                    )
                }

                composable("searchPoi") {
                    SearchPoiScreen(
                        onSearch = { type ->
                            val results = allPois.filter {
                                it.type.contains(type, ignoreCase = true)
                            }

                            if (results.isEmpty()) {
                                Toast.makeText(
                                    applicationContext,
                                    "No results",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                poiList.clear()
                                poiList.addAll(results)
                                navController.popBackStack()
                            }
                        },
                        onBackClick = {
                            navController.popBackStack()
                        }
                    )
                }}}}

    @Composable
    fun MapScreen(
        onAddPoiClick: () -> Unit,
        onSearchPoiClick: () -> Unit
    ) {
        var showWebDialog by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxSize()) {

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    val map = MapView(context)

                    map.setTileSource(TileSourceFactory.MAPNIK)
                    map.setMultiTouchControls(true)
                    map.controller.setZoom(18.0)
                    mapView = map

                    currentLocation?.let {
                        updateMapLocation(it)
                    }
                    map
                },
                update = { map ->
                    map.overlays.clear()

                    currentLocation?.let { location ->
                        val userPoint = GeoPoint(location.latitude, location.longitude)

                        userMarker = Marker(map)
                        userMarker!!.position = userPoint
                        userMarker!!.title = "You are here"

                        map.overlays.add(userMarker)
                        map.controller.setCenter(userPoint)
                    }

                    for (poi in poiList) {
                        val marker = Marker(map)
                        marker.position = GeoPoint(poi.latitude, poi.longitude)
                        marker.title = "${poi.name}\n\n${poi.type}\n\n${poi.description}"
                        map.overlays.add(marker)
                    }
                    map.invalidate()
                }
            )

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onAddPoiClick) {
                    Text("Add POI")
                }

                Button(onClick = onSearchPoiClick) {
                    Text("Search by Type")
                }

                Button(onClick = {
                    poiList.clear()
                    poiList.addAll(allPois)
                }) {
                    Text("Clear Search")
                }

                Button(onClick = {
                    val prefs = getSharedPreferences("poi_preferences", MODE_PRIVATE)
                    val hasSeenDialog = prefs.getBoolean("hasSeenWebDialog", false)

                    if (!hasSeenDialog) {
                        showWebDialog = true
                    } else {
                        loadWebPois(saveToDatabase = false)
                    }
                }) {
                    Text("Load Web POIs")
                }}
            if (showWebDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showWebDialog = false
                    },
                    title = {
                        Text("Load web POIs")
                    },
                    text = {
                        Text("Would you like to view the web POIs only, or download them into your local database?")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showWebDialog = false

                                val prefs = getSharedPreferences("poi_preferences", MODE_PRIVATE)
                                prefs.edit().putBoolean("hasSeenWebDialog", true).apply()
                                loadWebPois(saveToDatabase = true)
                            }
                        ) {
                            Text("Download")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showWebDialog = false
                                loadWebPois(saveToDatabase = false)
                            }
                        ) {
                            Text("View Only")
                        }
                    }
                )
            }}}

    @Composable
    fun AddPoiScreen(
        onSavePoi: (String, String, String) -> Unit,
        onBackClick: () -> Unit
    ) {
        var name by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add Point of Interest.")

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = type,
                onValueChange = { type = it },
                label = { Text("Type") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    onSavePoi(name, type, description)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save POI.")
            }

            Button(
                onClick = onBackClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back to Map.")
            }
        }}
    @Composable
    fun SearchPoiScreen(
        onSearch: (String) -> Unit,
        onBackClick: () -> Unit
    ){
        var typeSearch by remember { mutableStateOf("")}

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Search Points of Interest by Type.")

            OutlinedTextField(
                value = typeSearch,
                onValueChange = { typeSearch = it },
                label = {Text("Type")},
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    onSearch(typeSearch)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Search")
            }

            Button(
                onClick = onBackClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back to Map")
            }}}

    private fun loadWebPois(saveToDatabase: Boolean) {
        lifecycleScope.launch {
            try {
                val webPois = withContext(Dispatchers.IO) {
                    val jsonText = URL(webPoiUrl).readText()
                    val jsonArray = JSONArray(jsonText)
                    val results = mutableListOf<PointOfInterest>()

                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)

                        val poi = PointOfInterest(
                            name = item.getString("name"),
                            type = item.getString("type"),
                            description = item.getString("description"),
                            latitude = item.getDouble("lat"),
                            longitude = item.getDouble("lon")
                        )
                        results.add(poi)

                        if (saveToDatabase) {
                            databaseHelper.addPoi(
                                poi.name,
                                poi.type,
                                poi.description,
                                poi.latitude,
                                poi.longitude
                            )
                        }}
                    results
                }

                allPois.addAll(webPois)
                poiList.clear()
                poiList.addAll(webPois)

                Toast.makeText(
                    applicationContext,
                    "Loaded ${webPois.size} web POIs.",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                Toast.makeText(
                    applicationContext,
                    "Couldn't load web POIs.",
                    Toast.LENGTH_SHORT
                ).show()
            }}}

    private fun checkLocationPermission() {
        val permissionGranted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {
            startLocationUpdates()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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
        currentLocation = location

        val map = mapView

        if (map != null) {
            val userPoint = GeoPoint(location.latitude, location.longitude)
            map.controller.setCenter(userPoint)
            map.invalidate()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()

        if (::locationManager.isInitialized) {
            checkLocationPermission()
        }
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()

        if (::locationManager.isInitialized) {
            locationManager.removeUpdates(this)
        }
    }}