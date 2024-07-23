package com.likelion.yello

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.likelion.yello.ui.theme.YelloTheme
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var advertiser: BluetoothLeAdvertiser
    private lateinit var scanner: BluetoothLeScanner
    private lateinit var advertiseCallback: AdvertiseCallback
    private lateinit var scanCallback: ScanCallback
    private var isBluetoothActive = false
    private val requestEnableBt = 1
    private lateinit var webView: WebView
    private val appServiceUuid = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")

    private val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value
                if (!isGranted) {
                    println("$permissionName 권한이 거부되었습니다.")
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupBluetooth()

        setContent {
            YelloTheme {
                MainScreen()
            }
        }
    }

    private fun setupBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (bluetoothManager == null) {
            println("블루투스 매니저를 가져오지 못했습니다. 이 장치는 블루투스를 지원하지 않습니다.")
            return
        }

        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            println("블루투스 어댑터를 가져오지 못했습니다. 이 장치는 블루투스를 지원하지 않습니다.")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestBluetooth.launch(enableBtIntent)
        } else {
            requestMultiplePermissions.launch(permissions)
            advertiser = bluetoothAdapter.bluetoothLeAdvertiser
            scanner = bluetoothAdapter.bluetoothLeScanner
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            requestEnableBt -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    setupBluetooth()
                } else {
                    println("필요한 권한이 부여되지 않았습니다.")
                }
                return
            }
        }
    }

    private val requestBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                requestMultiplePermissions.launch(permissions)
                advertiser = bluetoothAdapter.bluetoothLeAdvertiser
                scanner = bluetoothAdapter.bluetoothLeScanner
            }
        }

    private fun startAdvertising() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE), requestEnableBt)
                    return
                }
            }

            val advertiseSettings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build()

            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid(appServiceUuid))
                .build()

            advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    super.onStartSuccess(settingsInEffect)
                    println("Advertising started successfully")
                }

                override fun onStartFailure(errorCode: Int) {
                    super.onStartFailure(errorCode)
                    println("Advertising failed with error code: $errorCode")
                }
            }

            advertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun stopAdvertising() {
        try {
            advertiser.stopAdvertising(advertiseCallback)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun startScanning() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_SCAN), requestEnableBt)
                    return
                }
            }

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(appServiceUuid))
                .build()

            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    super.onScanResult(callbackType, result)
                    runOnUiThread {
                        webView.evaluateJavascript("javascript:handleDetectedDevice('${result.device.address}')", null)
                    }
                    println("Device detected: ${result.device.address}")
                }

                override fun onBatchScanResults(results: List<ScanResult>) {
                    super.onBatchScanResults(results)
                    results.forEach { result ->
                        runOnUiThread {
                            webView.evaluateJavascript("javascript:handleDetectedDevice('${result.device.address}')", null)
                        }
                    }
                    println("Batch devices detected")
                }

                override fun onScanFailed(errorCode: Int) {
                    super.onScanFailed(errorCode)
                    println("Scan failed with error code: $errorCode")
                }
            }

            scanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun stopScanning() {
        try {
            scanner.stopScan(scanCallback)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    inner class BluetoothInterface {
        @JavascriptInterface
        fun startBluetooth() {
            if (!isBluetoothActive) {
                isBluetoothActive = true
                startAdvertising()
                startScanning()
            }
        }

        @JavascriptInterface
        fun stopBluetooth() {
            if (isBluetoothActive) {
                isBluetoothActive = false
                stopAdvertising()
                stopScanning()
                runOnUiThread {
                    webView.evaluateJavascript("javascript:clearDetectedDevices()", null)
                }
            }
        }
    }

    @Composable
    fun MainScreen() {
        val context = LocalContext.current
        val isBluetoothActive = remember { mutableStateOf(false) }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
//                Button(onClick = {
//                    if (ContextCompat.checkSelfPermission(
//                            context,
//                            Manifest.permission.BLUETOOTH
//                        ) == PackageManager.PERMISSION_GRANTED &&
//                        ContextCompat.checkSelfPermission(
//                            context,
//                            Manifest.permission.BLUETOOTH_ADMIN
//                        ) == PackageManager.PERMISSION_GRANTED &&
//                        ContextCompat.checkSelfPermission(
//                            context,
//                            Manifest.permission.ACCESS_FINE_LOCATION
//                        ) == PackageManager.PERMISSION_GRANTED &&
//                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || (
//                                ContextCompat.checkSelfPermission(
//                                    context,
//                                    Manifest.permission.BLUETOOTH_SCAN
//                                ) == PackageManager.PERMISSION_GRANTED &&
//                                        ContextCompat.checkSelfPermission(
//                                            context,
//                                            Manifest.permission.BLUETOOTH_ADVERTISE
//                                        ) == PackageManager.PERMISSION_GRANTED &&
//                                        ContextCompat.checkSelfPermission(
//                                            context,
//                                            Manifest.permission.BLUETOOTH_CONNECT
//                                        ) == PackageManager.PERMISSION_GRANTED
//                                ))
//                    ) {
//                        if (isBluetoothActive.value) {
//                            webView.evaluateJavascript("javascript:stopBluetooth()", null)
//                        } else {
//                            webView.evaluateJavascript("javascript:startBluetooth()", null)
//                        }
//                        isBluetoothActive.value = !isBluetoothActive.value
//                    } else {
//                        ActivityCompat.requestPermissions(
//                            context as Activity,
//                            permissions,
//                            requestEnableBt
//                        )
//                    }
//                }) {
//                    Text(text = if (isBluetoothActive.value) "블루투스 통신 끝" else "블루투스 통신 시작")
//                }
                AndroidView(factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        webViewClient = WebViewClient()
                        loadUrl("https://bletest-repo.vercel.app/")
                        addJavascriptInterface(BluetoothInterface(), "BluetoothInterface")
                        webView = this
                    }
                })
            }
        }
    }
}
