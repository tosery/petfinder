package com.team.project.KakaoMapAPI

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Context.LOCATION_SERVICE
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.team.project.MainActivity
import com.team.project.R
import android.content.pm.PackageManager
import android.location.*
import android.location.LocationListener
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.tasks.OnFailureListener
import com.team.project.SplashActivity

import com.team.project.databinding.FragmentMapBinding
import net.daum.mf.map.api.MapPOIItem
import net.daum.mf.map.api.MapPoint
import net.daum.mf.map.api.MapView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import net.daum.mf.map.api.CameraUpdateFactory

class MapFragment : Fragment() {

    private lateinit var binding: FragmentMapBinding

    private lateinit var mainActivity:MainActivity


    private val TAG = "googlemap_example"
    var mLocationManager: LocationManager? = null
    var mLocationListener: LocationListener? = null

    private var fusedLocationProviderClient: FusedLocationProviderClient? = null
    private var locationRequest: LocationRequest? = null
    private var longitude = 0.0
    private var latitude = 0.0

    companion object {
        private val BASE_URL = "https://dapi.kakao.com/"
        const val API_KEY = "KakaoAK c4dc56e62c47c6173e7c78f71f59f279"  // REST API ???\

        private val TAG = SplashActivity::class.java.simpleName
        private const val GPS_UTIL_LOCATION_PERMISSION_REQUEST_CODE = 100
        private const val GPS_UTIL_LOCATION_RESOLUTION_REQUEST_CODE = 101
        const val DEFAULT_LOCATION_REQUEST_PRIORITY =
            LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        const val DEFAULT_LOCATION_REQUEST_INTERVAL = 20000L
        const val DEFAULT_LOCATION_REQUEST_FAST_INTERVAL = 10000L
    }

    private val ACCESS_FINE_LOCATION = 1000     // Request Code

    private val listItems = arrayListOf<MapListModel>()   // ??????????????? ??? ?????????
    private val listAdapter = MapListAdapter(listItems)    // ??????????????? ??? ?????????
    private var pageNumber = 1      // ?????? ????????? ??????
    private var keyword = ""        // ?????? ?????????

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mainActivity = getActivity() as MainActivity

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        checkLocationPermission()
        // binding ??????
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_map, container, false)

        longitude = mainActivity.longitude
        latitude = mainActivity.latitude


        // ??????????????? ???
        binding.rvList.layoutManager = LinearLayoutManager(mainActivity, LinearLayoutManager.VERTICAL, false)
        binding.rvList.adapter = listAdapter

//         binding.mapView.currentLocationTrackingMode = MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading

        if (checkLocationService()) {
            // GPS??? ???????????? ??????
            permissionCheck()
        } else {
            // GPS??? ???????????? ??????
            Toast.makeText(mainActivity, "GPS??? ????????????", Toast.LENGTH_SHORT).show()
        }

        // ????????? ?????? ??????
        startTracking()


        // ????????? ????????? ?????? ??? ?????? ????????? ??????
        listAdapter.setItemClickListener(object: MapListAdapter.OnItemClickListener {
            override fun onClick(v: View, position: Int) {
                // ????????? ?????? ??????
                stopTracking()
                val mapPoint = MapPoint.mapPointWithGeoCoord(listItems[position].y, listItems[position].x)
                binding.mapView.setMapCenterPointAndZoomLevel(mapPoint, 1, true)
            }
        })

        /*** ***/

        searchKeyword("??????",pageNumber)

        // binding.mapView.currentLocationTrackingMode = MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading


        Log.d(TAG,"??????:"+MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading)

        onClick(binding.btnPetHospital,"????????????")
        onClick(binding.btnPetCafe,"????????????")
        onClick(binding.btnPetFood,"????????????")
        onClick(binding.btnPetHotel,"??????????????????")
        onClick(binding.btnPetStore,"????????????")
        onClick(binding.btnAll,"??????")

        return binding.root
    }

    fun onClick(btn:Button,keyword: String){
        btn.setOnClickListener {
            searchKeyword(keyword,pageNumber)
        }

    }

    // ????????? ?????? ??????
    private fun searchKeyword(keyword: String, page: Int) {
        val retrofit = Retrofit.Builder()          // Retrofit ??????
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(KakaoAPI::class.java)            // ?????? ?????????????????? ????????? ??????
        val call = api.getSearchKeyword(API_KEY, keyword, page,longitude,latitude)    // ?????? ?????? ??????
        
        // API ????????? ??????
        call.enqueue(object: Callback<ResultSearchKeyword> {
            override fun onResponse(call: Call<ResultSearchKeyword>, response: Response<ResultSearchKeyword>) {
                // ?????? ??????
                addItemsAndMarkers(response.body(),keyword)
                // ?????? ?????? (?????? ????????? response.body()??? ????????????)
                Log.d("Test", "Raw: ${response.raw()}")
                Log.d("Test", "Body: ${response.body()}")
            }

            override fun onFailure(call: Call<ResultSearchKeyword>, t: Throwable) {
                // ?????? ??????
                Log.w("LocalSearch", "?????? ??????: ${t.message}")
            }
        })
    }

//    override fun onStart() {
//        super.onStart()
//        checkLocationPermission()
//    }

    private fun checkLocationPermission() {
        val accessLocation =
            ActivityCompat.checkSelfPermission(mainActivity, Manifest.permission.ACCESS_FINE_LOCATION)
        if (accessLocation == PackageManager.PERMISSION_GRANTED) {
            checkLocationSetting()
        } else {
            ActivityCompat.requestPermissions(
                mainActivity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                MapFragment.GPS_UTIL_LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    // ?????? ?????? ?????? ??????
    private fun addItemsAndMarkers(searchResult: ResultSearchKeyword?,keyword:String) {
        if (!searchResult?.documents.isNullOrEmpty()) {
            // ?????? ?????? ??????
            listItems.clear()                   // ????????? ?????????
            binding.mapView.removeAllPOIItems() // ????????? ?????? ?????? ??????
            for (document in searchResult!!.documents) {

                var distanc = when { document.distanc == null -> ""  else -> document.distanc }


                // ????????? ??????????????? ?????? ??????
                val item = MapListModel(document.place_name,
                    document.road_address_name,
                    distanc,
                    document.place_url,
                    document.phone,
                    document.category_name,
                    document.x.toDouble(),
                    document.y.toDouble(),
                    keyword,
                    document.category_group_code)


                listItems.add(item)

                // ????????? ?????? ??????
                // val point = MapPOIItem()

                val customMarker = MapPOIItem()

                customMarker.setItemName("Default Marker");
                customMarker.setTag(0);
                customMarker.setCustomImageResourceId(R.drawable.pethospital); // ?????? ?????????.
                customMarker.setMarkerType(MapPOIItem.MarkerType.BluePin); // ???????????? ???????????? BluePin ?????? ??????.
                customMarker.setSelectedMarkerType(MapPOIItem.MarkerType.RedPin); // ????????? ???????????????, ???????????? ???????????? RedPin ?????? ??????.



                customMarker.apply {
                    itemName = document.place_name
                    mapPoint = MapPoint.mapPointWithGeoCoord(document.y.toDouble(),
                        document.x.toDouble())
                    markerType = MapPOIItem.MarkerType.BluePin
                    selectedMarkerType = MapPOIItem.MarkerType.RedPin

                    // TODO("?????? ????????? ??????.. ")
                    // ????????? ?????? ?????????
                    if(document.category_group_code.equals("HP8")){ // ??????
                        customImageResourceId = R.drawable.pethospitals
                        customSelectedImageResourceId = R.drawable.pethospitals
                    }else if(document.category_group_code.equals("CE7")){ // ??????
                        customImageResourceId = R.drawable.petcafes
                        customSelectedImageResourceId = R.drawable.petcafes
                    }else if(document.category_group_code.equals("FD6")){ //  ??????
                        customImageResourceId = R.drawable.petfoods
                        customSelectedImageResourceId = R.drawable.petfoods
                    }else if(document.category_group_code.equals("AD5")){ // ??????/??????
                        customImageResourceId = R.drawable.pethotels
                        customSelectedImageResourceId = R.drawable.pethotels
                    }else if(document.category_group_code.equals("") && keyword.equals("????????????")){
                        customImageResourceId = R.drawable.petmds
                        customSelectedImageResourceId = R.drawable.petmds
                    }else{
                        customImageResourceId = R.drawable.btnall
                        customSelectedImageResourceId = R.drawable.btnall
                    }


                    markerType = MapPOIItem.MarkerType.CustomImage          // ?????? ?????? (?????????)

                    selectedMarkerType = MapPOIItem.MarkerType.CustomImage  // ?????? ??? ?????? ?????? (?????????)

                    isCustomImageAutoscale = false      // ????????? ?????? ????????? ?????? ?????? ??????
                    setCustomImageAnchor(0.5f, 1.0f)    // ?????? ????????? ?????????
                }


                binding.mapView.addPOIItem(customMarker)
            }
            listAdapter.notifyDataSetChanged()


        } else {
            // ?????? ?????? ??????
            Toast.makeText(mainActivity, "?????? ????????? ????????????", Toast.LENGTH_SHORT).show()
        }
    }

    // ?????? ?????? ??????
    private fun permissionCheck() {
        val preference = mainActivity.getPreferences(MODE_PRIVATE)
        val isFirstCheck = preference.getBoolean("isFirstPermissionCheck", true)

        if (ContextCompat.checkSelfPermission(mainActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // ????????? ?????? ??????
            if (ActivityCompat.shouldShowRequestPermissionRationale(mainActivity, Manifest.permission.ACCESS_FINE_LOCATION)) {
                // ?????? ?????? (?????? ??? ??? ?????????)
                val builder = AlertDialog.Builder(mainActivity)
                builder.setMessage("?????? ????????? ?????????????????? ?????? ????????? ??????????????????.")
                builder.setPositiveButton("??????") { dialog, which ->
                    ActivityCompat.requestPermissions(mainActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), ACCESS_FINE_LOCATION)
                }
                builder.setNegativeButton("??????") { dialog, which ->

                }
                builder.show()
            } else {
                if (isFirstCheck) {
                    // ?????? ?????? ??????
                    preference.edit().putBoolean("isFirstPermissionCheck", false).apply()
                    ActivityCompat.requestPermissions(mainActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), ACCESS_FINE_LOCATION)
                } else {
                    // ?????? ?????? ?????? ?????? (??? ?????? ???????????? ??????)
                    val builder = AlertDialog.Builder(mainActivity)
                    builder.setMessage("?????? ????????? ?????????????????? ???????????? ?????? ????????? ??????????????????.")
                    builder.setPositiveButton("???????????? ??????") { dialog, which ->
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.team.project"))
                        startActivity(intent)
                    }
                    builder.setNegativeButton("??????") { dialog, which ->

                    }
                    builder.show()
                }
            }
        } else {

        }
    }

    // ???????????? ??????
    private fun startTracking() {
        binding.mapView.currentLocationTrackingMode = MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading
    }

    // ???????????? ??????
    private fun stopTracking() {
        binding.mapView.currentLocationTrackingMode = MapView.CurrentLocationTrackingMode.TrackingModeOff
    }

    // GPS??? ??????????????? ??????
    private fun checkLocationService(): Boolean {
        val locationManager = mainActivity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }


    private fun checkLocationSetting() {
        locationRequest = LocationRequest.create()
        locationRequest!!.setPriority(DEFAULT_LOCATION_REQUEST_PRIORITY)
        locationRequest!!.setInterval(DEFAULT_LOCATION_REQUEST_INTERVAL)
        locationRequest!!.setFastestInterval(DEFAULT_LOCATION_REQUEST_FAST_INTERVAL)
        val settingsClient = LocationServices.getSettingsClient(mainActivity)
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
            .setAlwaysShow(true)
        settingsClient.checkLocationSettings(builder.build())
            .addOnSuccessListener(mainActivity) {
                fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(mainActivity)

                if (ActivityCompat.checkSelfPermission(
                        mainActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        mainActivity,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {

                }
                fusedLocationProviderClient!!.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    null
                )
            }
            .addOnFailureListener(mainActivity, OnFailureListener { e ->
                val statusCode = (e as ApiException).statusCode
                when (statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> try {
                        val rae = e as ResolvableApiException
                        rae.startResolutionForResult(
                            mainActivity,
                            GPS_UTIL_LOCATION_RESOLUTION_REQUEST_CODE
                        )
                    } catch (sie: IntentSender.SendIntentException) {
                        Log.w(
                            TAG,
                            "unable to start resolution for result due to " + sie.localizedMessage
                        )
                    }
                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                        val errorMessage =
                            "location settings are inadequate, and cannot be fixed here. Fix in Settings."
                        Log.e(TAG, errorMessage)
                    }
                }
            })
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GPS_UTIL_LOCATION_RESOLUTION_REQUEST_CODE) {
            if (resultCode == AppCompatActivity.RESULT_OK) {
                checkLocationSetting()
            } else {
            }
        }
    }

    private val locationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            longitude = locationResult.lastLocation.longitude
            latitude = locationResult.lastLocation.latitude
            fusedLocationProviderClient!!.removeLocationUpdates(this)

            Log.d(TAG,"??????2222.."+longitude)

        }

        override fun onLocationAvailability(locationAvailability: LocationAvailability) {
            super.onLocationAvailability(locationAvailability)
            Log.i(TAG, "onLocationAvailability - $locationAvailability")
        }
    }



}