package com.example.Q102009411

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL
import java.net.HttpURLConnection
import java.net.URLEncoder
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Close
import androidx.core.graphics.drawable.DrawableCompat

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

    data class PointOfInterest(
        val id: Int = 0,
        val name: String,
        val type: String,
        val description: String,
        val latitude: Double,
        val longitude: Double
    )

    private val poiList = mutableStateListOf<PointOfInterest>()
    private val allPois = mutableStateListOf<PointOfInterest>()
    private val webPoiUrl = "http://10.0.2.2:3000/poi/all"
    private val createPoiUrl = "http://10.0.2.2:3000/poi/create"
    private val poiReviews = mutableStateMapOf<Int, String>()

    @OptIn(ExperimentalMaterial3Api::class)
    //Needed to use the TopAppBar import for some reason
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().userAgentValue = packageName
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        databaseHelper = PoiDatabaseHelper(this)
        allPois.addAll(databaseHelper.getAllPois())
        poiList.addAll(allPois)
        checkLocationPermission()

        setContent {
            val navController = rememberNavController()
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = false,
                //gestures interfere with pinching the map, hamburger only works better.
                drawerContent = {
                    ModalDrawerSheet {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Points of Interest")
                            IconButton(
                                onClick = {
                                    scope.launch {drawerState.close()}
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }}

                        NavigationDrawerItem(
                            label = {Text("Map")},
                            selected = false,
                            onClick = {
                                scope.launch {drawerState.close()}
                                navController.navigate("map")
                            }
                        )

                        NavigationDrawerItem(
                            label = {Text("Search")},
                            selected = false,
                            onClick = {
                                scope.launch {drawerState.close()}
                                navController.navigate("searchPoi")
                            }
                        )

                        NavigationDrawerItem(
                            label = {Text("Download Web POIs")},
                            selected = false,
                            onClick = {
                                scope.launch {drawerState.close()}
                                loadWebPois()
                            }
                        )
                    }}
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {Text("Points of Interest")},
                            navigationIcon = {
                                IconButton(
                                    onClick = {
                                        scope.launch {drawerState.open()}
                                    }
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                                }}
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = false,
                                onClick = {
                                    navController.navigate("map")
                                },
                                icon = {
                                    Icon(Icons.Default.Place, contentDescription = "Map")
                                },
                                label = {
                                    Text("Map")
                                }
                            )

                            NavigationBarItem(
                                selected = false,
                                onClick = {
                                    navController.navigate("searchPoi")
                                },
                                icon = {
                                    Icon(Icons.Default.Search, contentDescription = "Search")
                                },
                                label = {
                                    Text("Search")
                                }
                            )
                        }
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = {
                                navController.navigate("addPoi")
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add POI")
                        }}
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        NavHost(
                            navController = navController,
                            startDestination = "map"
                        ) {
                            composable("map") {
                                MapScreen()
                            }

                composable("addPoi") {
                    AddPoiScreen(
                        onSavePoi = { name, type, description ->
                            val location = currentLocation

                            if (location != null) {
                                uploadPoiToWeb(
                                    name = name,
                                    type = type,
                                    description = description,
                                    latitude = location.latitude,
                                    longitude = location.longitude
                                ) { webId ->

                                val newPoi = PointOfInterest(
                                    id = webId,
                                    name = name,
                                    type = type,
                                    description = description,
                                    latitude = location.latitude,
                                    longitude = location.longitude
                                )
                                poiList.add(newPoi)
                                allPois.add(newPoi)

                                databaseHelper.addPoi(
                                    webId,
                                    name,
                                    type,
                                    description,
                                    location.latitude,
                                    location.longitude
                                )

                            navController.popBackStack()
                        }}},
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
                                navController.navigate("map")
                            }
                        },
                        onBackClick = {
                            navController.navigate("map")
                        }
                    )
                }}}}}}}

    @Composable
    fun MapScreen()
    {var selectedPoi by remember {mutableStateOf<PointOfInterest?>(null)}
     var reviewText by remember {mutableStateOf("")}
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
                        userMarker!!.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        userMarker!!.icon = map.context.getDrawable(android.R.drawable.ic_menu_mylocation)
                        DrawableCompat.setTint(userMarker!!.icon!!, android.graphics.Color.BLUE)

                        map.overlays.add(userMarker)
                        map.controller.setCenter(userPoint)
                    }

                    for (poi in poiList) {
                        val marker = Marker(map)
                        marker.position = GeoPoint(poi.latitude, poi.longitude)
                        marker.title = poi.name

                        marker.setOnMarkerClickListener { _, _ ->
                            selectedPoi = poi
                            reviewText = poiReviews[poi.id] ?:""
                            true
                        }
                        map.overlays.add(marker)
                    }
                    map.invalidate()
                }
            )

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                Button(onClick = {
                    poiList.clear()
                    poiList.addAll(allPois)
                }) {
                    Text("Clear Search")
                }}

            if (selectedPoi != null){
                AlertDialog(
                    onDismissRequest = {
                        selectedPoi = null
                    },
                    title = {
                        Text(selectedPoi!!.name)
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Type: ${selectedPoi!!.type}")
                            Text("Description: ${selectedPoi!!.description}")

                            OutlinedTextField(
                                value = reviewText,
                                onValueChange = {reviewText = it},
                                label = {Text("Review")},
                                modifier = Modifier.fillMaxWidth()
                            )
                        }},
                    confirmButton = {
                        TextButton(
                            onClick = {
                                poiReviews[selectedPoi!!.id] = reviewText

                                Toast.makeText(
                                    applicationContext,
                                    "Review Saved",
                                    Toast.LENGTH_SHORT
                                ).show()
                                selectedPoi = null
                            }
                        ) {
                            Text("Save Review")
                        }},
                    dismissButton = {
                        TextButton(
                            onClick = {
                                selectedPoi = null
                            }
                        ) {
                            Text("Close")
                        }}
                )
            }}}

    @Composable
    fun AddPoiScreen(
        onSavePoi: (String, String, String) -> Unit,
        onBackClick: () -> Unit
    ) {
        var name by remember {mutableStateOf("")}
        var type by remember { mutableStateOf("") }
        var description by remember {mutableStateOf("")}

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add Point of Interest.")

            OutlinedTextField(
                value = name,
                onValueChange = {name = it},
                label = {Text("Name")},
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = type,
                onValueChange = {type = it},
                label = {Text("Type")},
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = {description = it},
                label = {Text("Description")},
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
                onValueChange = {typeSearch = it},
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

    private fun loadWebPois(){
        lifecycleScope.launch {
            try {
                val webPois = withContext(Dispatchers.IO) {
                    val connection = URL(webPoiUrl).openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    val jsonText = connection.inputStream.bufferedReader().readText()
                    connection.disconnect()
                    val jsonArray = JSONArray(jsonText)
                    val results = mutableListOf<PointOfInterest>()

                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)

                        val poi = PointOfInterest(
                            id = item.getInt("id"),
                            name = item.getString("name"),
                            type = item.getString("type"),
                            description = item.getString("description"),
                            latitude = item.getDouble("lat"),
                            longitude = item.getDouble("lon")
                        )
                        results.add(poi)

                            databaseHelper.addPoi(
                                poi.id,
                                poi.name,
                                poi.type,
                                poi.description,
                                poi.latitude,
                                poi.longitude
                            )
                        }
                    results
                }

                allPois.clear()
                allPois.addAll(databaseHelper.getAllPois())
                allPois.addAll(webPois)
                poiList.clear()
                poiList.addAll(allPois)

                Toast.makeText(
                    applicationContext,
                    "Loaded ${webPois.size} web POIs.",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (_: Exception) {
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

    private fun uploadPoiToWeb(
        name: String,
        type: String,
        description: String,
        latitude: Double,
        longitude: Double,
        onSuccess: (Int) -> Unit
    ) {
        lifecycleScope.launch {
            try {
                val webId = withContext(Dispatchers.IO) {
                    val postData =
                        "name=${URLEncoder.encode(name, "UTF-8")}" +
                                "&type=${URLEncoder.encode(type, "UTF-8")}" +
                                "&description=${URLEncoder.encode(description, "UTF-8")}" +
                                "&lat=$latitude" +
                                "&lon=$longitude"

                    val connection = URL(createPoiUrl).openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.setRequestProperty(
                        "Content-Type",
                        "application/x-www-form-urlencoded"
                    )

                    connection.outputStream.use { output ->
                        output.write(postData.toByteArray())
                    }

                    val response = connection.inputStream.bufferedReader().readText()
                    connection.disconnect()
                    response.toInt()
                }
                onSuccess(webId)

            } catch (_: Exception) {
                Toast.makeText(
                    applicationContext,
                    "Upload failed. Check server connection.",
                    Toast.LENGTH_SHORT
                ).show()
            }}}



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