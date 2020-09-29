package com.arron.mydemo.bluetooth.view

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.BluetoothAdapter.STATE_CONNECTED
import android.bluetooth.BluetoothAdapter.STATE_DISCONNECTED
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import com.arron.mydemo.*
import com.arron.mydemo.bluetooth.adapter.MyAdapter
import com.arron.mydemo.bluetooth.model.BluetoothDeviceBean
import com.arron.mydemo.bluetooth.model.CharacteristicBean
import com.arron.mydemo.bluetooth.model.ServiceBean
import kotlinx.android.synthetic.main.activity_classic_bluetooth.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.collections.ArrayList

const val MESSAGE_READ: Int = 0
const val MESSAGE_WRITE: Int = 1
const val MESSAGE_TOAST: Int = 2
const val MODE_CLASSIC = 1
const val MODE_BLE = 2

const val ACTION_GATT_CONNECTED = "com.example.bluetooth.le.ACTION_GATT_CONNECTED"
const val ACTION_GATT_DISCONNECTED = "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED"
const val ACTION_GATT_SERVICES_DISCOVERED = "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED"
const val ACTION_DATA_AVAILABLE = "com.example.bluetooth.le.ACTION_DATA_AVAILABLE"
const val EXTRA_DATA = "com.example.bluetooth.le.EXTRA_DATA"
val UUID_HEART_RATE_MEASUREMENT = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")
val bluetoothGattServiceLiveData= MutableLiveData<BluetoothGattService>()

val MY_UUID = UUID.fromString("00001000-0000-1000-8000-00805F9B34FB")

val NOTIFY_DESCRIPTOR = ""
val WRITE_UUID = ""
val SERVICE_UUID = ""
val NOTIFY_UUID = ""
val READUUID = ""

class BluetoothActivity : AppCompatActivity() {
    private val TAG = "ClassicBluetoothActivity"
    private val REQUEST_ENABLE_BT = 0
    private val REQUEST_ENABLE_DISCOVERABILITY = 1
    private val REQUEST_LOCATION_PERMISSION = 2
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private lateinit var mAdapter: MyAdapter
    private val mBoundedDevices = ArrayList<BluetoothDeviceBean>()
    private var connectedThread: ConnectedThread? = null
    private var connectThread: ConnectThread? = null
    private var acceptThread: AcceptThread? = null
    private var mScanning: Boolean = false
    private val SCAN_PERIOD = 10000L
    private var connectionState = STATE_DISCONNECTED
    private var currMode = MODE_CLASSIC
    private var bluetoothGatt: BluetoothGatt? = null
    private var bluetoothGattService:BluetoothGattService? = null

    private var handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                MESSAGE_READ -> {
                    log(TAG, "收到消息：${String(msg.obj as ByteArray)}")
                }
                MESSAGE_WRITE -> {
                    log(TAG, "发送消息：${String(msg.obj as ByteArray)}")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_classic_bluetooth)
        registerBroadcast()
        initRecycler()
        observeLiveData()
        setListeners()
        setTitle("经典蓝牙模式")
    }


    private fun setTitle(title: String) {
        supportActionBar?.title = title
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_1 -> {
                if (currMode == MODE_CLASSIC) {
                    currMode = MODE_BLE
                    setTitle("低功耗蓝牙模式")
                    tv_startle.visibility = View.VISIBLE
                    ll_classic.visibility = View.GONE
                } else {
                    currMode = MODE_CLASSIC
                    setTitle("经典蓝牙模式")
                    tv_startle.visibility = View.GONE
                    ll_classic.visibility = View.VISIBLE
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setListeners() {
        tv_bounded.setOnClickListener {
            if (checkBluetooth() && checkPermission())
                searchPairedDevices()
        }

        tv_discovery.setOnClickListener {
            if (checkBluetooth() && checkPermission())
                startDiscovery()
        }
        tv_send.setOnClickListener {
            val content = et_content.text.toString()
            if (!TextUtils.isEmpty(content)) {
                if(currMode== MODE_CLASSIC)
                    connectedThread?.write(content.toByteArray())
                else
                    writeBleData(bluetoothGattService,content)
            }
        }
        tv_enable_discover.setOnClickListener {
            enableDiscoverability()
        }
        tv_startle.setOnClickListener {
            if (checkBluetooth() && checkPermission())
                scanLeDevice(true)
        }
        tv_create_server.setOnClickListener {
            if(checkBluetooth()){
                acceptThread?.cancel()
                acceptThread = AcceptThread().apply { start() }
            }
        }
    }

    private fun initRecycler() {
        recycler.layoutManager = LinearLayoutManager(this)
        mAdapter = MyAdapter(ArrayList()) {
            it.device ?: return@MyAdapter
            if (currMode == MODE_CLASSIC) {
                connectThread?.cancel()
                if (!it.isPaired)
                    createBond(it.device!!)
                else
                    connectThread = ConnectThread(it.device!!).apply { start() }
            } else {
                if (it.services == null) {
                    gattCallback.deviceBean = it
                    scanLeDevice(false)
                    it.device!!.connectGatt(this, false, gattCallback)
                }
            }
        }
        recycler.adapter = mAdapter
    }

    private fun observeLiveData() {
        logLiveData.observe(this, androidx.lifecycle.Observer {
            val split = it.split("-")
            if (TextUtils.equals(split[0], TAG)) {
                tv_log.text = tv_log.text.toString() + "\n" + split[1]
                scrollView.post {
                    scrollView.fullScroll(View.FOCUS_DOWN)
                }
            }
        })
        bluetoothGattServiceLiveData.observe(this, androidx.lifecycle.Observer {
            toast("已选择指定Service")
            bluetoothGattService = it
        })
    }

    private fun checkBluetooth(): Boolean {
        //检测设备是否支持蓝牙
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (mBluetoothAdapter == null) {
            toast("设备不支持蓝牙功能！")
            return false
        }
        //检测设备是否开启蓝牙
        if (!mBluetoothAdapter!!.isEnabled) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(intent, REQUEST_ENABLE_BT)
            return false
        }
        return true
    }

    private fun checkPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //检查是否已经获取到地理位置权限，如果没有!=，进行权限获取
            if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_LOCATION_PERMISSION
                )
                return false
            } else {
                return true
            }
        } else {
            return true
        }

    }

    private fun registerBroadcast() {
        val intentFilter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        }
        registerReceiver(mBluetoothStateReceiver, intentFilter)

        registerReceiver(gattUpdateReceiver, IntentFilter().apply {
            addAction(ACTION_GATT_CONNECTED)
            addAction(ACTION_GATT_DISCONNECTED)
            addAction(ACTION_DATA_AVAILABLE)
            addAction(ACTION_GATT_SERVICES_DISCOVERED)
        })
    }

    private fun unRegisterBroadcast() {
        unregisterReceiver(mBluetoothStateReceiver)
        unregisterReceiver(gattUpdateReceiver)
    }

    private val mBluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, intent: Intent?) {
            intent?.apply {
                log(TAG, "广播 ACTION:$action")
                if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = getIntExtra(BluetoothAdapter.EXTRA_STATE, 0)
                    val previous_state = getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, 0)
                    when (state) {
                        BluetoothAdapter.STATE_ON -> {
                            log(TAG, "蓝牙已打开")
                        }
                        BluetoothAdapter.STATE_TURNING_ON -> {
                            log(TAG, "蓝牙正在开启...")
                        }
                        BluetoothAdapter.STATE_OFF -> {
                            log(TAG, "蓝牙已关闭")
                        }
                        BluetoothAdapter.STATE_TURNING_OFF -> {
                            log(TAG, "蓝牙正在关闭...")
                        }
                    }
                } else if (action == BluetoothDevice.ACTION_FOUND) {
                    val device =
                        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice?
                    val btClass = getParcelableExtra(BluetoothDevice.EXTRA_CLASS) as BluetoothClass?
                    device ?: return
                    val deviceName = device.name
                    val deviceMac = device.address
                    if (TextUtils.isEmpty(deviceName))
                        return
                    log(TAG, "发现设备：${deviceName} 地址：${deviceMac}")
                    val bean = BluetoothDeviceBean(
                        deviceName,
                        deviceMac,
                        device.bondState == BluetoothDevice.BOND_BONDED,
                        device
                    )
                    if (mAdapter.data.contains(bean))
                        return
                    device.uuids?.forEach {
                        log(TAG, "${deviceName} UUID:${it.uuid}")
                    }
                    mAdapter.data.add(bean)
                    mAdapter.notifyDataSetChanged()
                } else if (action == BluetoothAdapter.ACTION_SCAN_MODE_CHANGED) {
                    val scanMode = getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, 0)
                    val previousScanMode = getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_SCAN_MODE, 0)
                    when (scanMode) {
                        BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE -> {
                            log(TAG, "扫描模式为可连接可被发现")
                        }
                        BluetoothAdapter.SCAN_MODE_CONNECTABLE -> {
                            log(TAG, "扫描模式为可连接")
                        }
                        BluetoothAdapter.SCAN_MODE_NONE -> {
                            log(TAG, "扫描模式为不可连接不可发现")
                        }
                    }
                } else if (action == BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED) {
                    val state = getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, 0)
                    when (state) {
                        BluetoothAdapter.STATE_CONNECTED -> {
                            log(TAG, "蓝牙已连接")
                        }
                        BluetoothAdapter.STATE_CONNECTING -> {
                            log(TAG, "蓝牙连接中")
                        }
                        BluetoothAdapter.STATE_DISCONNECTED -> {
                            log(TAG, "蓝牙断开")
                        }
                        BluetoothAdapter.STATE_DISCONNECTING -> {
                            log(TAG, "蓝牙断开中")
                        }
                    }
                }
            }
        }
    }

    //查找已配对设备
    private fun searchPairedDevices() {
        mBluetoothAdapter?.apply {
            log(TAG, "已配对设备列表")
            mBoundedDevices.clear()
            bondedDevices?.forEach {
                log(TAG, "名称：${it.name} MAC 地址：${it.address}")
                it.uuids?.forEach {
                    log(TAG, "UUID:${it.uuid}")
                }
                mBoundedDevices.add(BluetoothDeviceBean(it.name, it.address, true, it))
            }
            mAdapter.data.clear()
            mAdapter.data.addAll(mBoundedDevices)
            mAdapter.notifyDataSetChanged()
        }
    }

    //开始搜索设备
    private fun startDiscovery() {
        log(TAG, "开始发现设备...")
        mAdapter.data.clear()
        mAdapter.notifyDataSetChanged()
        mBluetoothAdapter?.startDiscovery()
    }

    //启用可检测性
    //如果尚未在设备上启用蓝牙，则启用设备可检测性会自动启用蓝牙。
    private fun enableDiscoverability() {
        val discoverableIntent: Intent =
            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            }
        startActivityForResult(discoverableIntent, REQUEST_ENABLE_DISCOVERABILITY)
    }

    //开始配对
    private fun createBond(mBluetoothDevice: BluetoothDevice) {
        try {
            // 连接建立之前的先配对
            if (mBluetoothDevice.bondState == BluetoothDevice.BOND_NONE) {
                val creMethod = BluetoothDevice::class.java.getMethod("createBond");
                log(TAG, "开始配对");
                creMethod.invoke(mBluetoothDevice);
                startDiscovery()
            }
        } catch (e: Exception) {
            e.printStackTrace();
            throw  Exception("Can't createBond with this device,please try again");
        }
    }

    private inner class AcceptThread : Thread() {

        val NAME = "MyDemo"
        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            mBluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(NAME,
                MY_UUID
            )
        }

        override fun run() {
            // Keep listening until exception occurs or a socket is returned.
            var shouldLoop = true
            while (shouldLoop) {
                val socket: BluetoothSocket? = try {
                    mmServerSocket?.accept()
                } catch (e: IOException) {
                    log(TAG, "Socket's accept() method failed ")
                    shouldLoop = false
                    null
                }
                socket?.also {
                    manageMyConnectedSocket(it)
                    mmServerSocket?.close()
                    shouldLoop = false
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        fun cancel() {
            try {
                mmServerSocket?.close()
            } catch (e: IOException) {
                log(TAG, "Could not close the connect socket")
            }
        }
    }

    private inner class ConnectThread(device: BluetoothDevice) : Thread() {

        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
//            device.createInsecureRfcommSocketToServiceRecord(MY_UUID)
            device.createRfcommSocketToServiceRecord(MY_UUID)
        }

        public override fun run() {
            // Cancel discovery because it otherwise slows down the connection.
            mBluetoothAdapter?.cancelDiscovery()

            mmSocket?.use { socket ->
                try {
                    // Connect to the remote device through the socket. This call blocks
                    // until it succeeds or throws an exception.
                    socket.connect()

                    // The connection attempt succeeded. Perform work associated with
                    // the connection in a separate thread.
                    manageMyConnectedSocket(socket)
                } catch (e: Exception) {
                    log(TAG, "连接失败！")
                }
            }
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                log(TAG, "Could not close the client socket")
            }
        }
    }

    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {

        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private val mmBuffer: ByteArray = ByteArray(1024) // mmBuffer store for the stream

        override fun run() {
            var numBytes: Int // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                // Read from the InputStream.
                numBytes = try {
                    mmInStream.read(mmBuffer)
                } catch (e: IOException) {
                    log(TAG, "Input stream was disconnected ${e.message}")
                    break
                }

                // Send the obtained bytes to the UI activity.
                val readMsg = handler.obtainMessage(
                    MESSAGE_READ, numBytes, -1,
                    mmBuffer
                )
                readMsg.sendToTarget()
            }
        }

        // Call this from the main activity to send data to the remote device.
        fun write(bytes: ByteArray) {
            try {
                mmOutStream.write(bytes)
            } catch (e: IOException) {
                log(TAG, "Error occurred when sending data ${e.message}")
                // Send a failure message back to the activity.
                val writeErrorMsg = handler.obtainMessage(MESSAGE_TOAST)
                val bundle = Bundle().apply {
                    putString("toast", "Couldn't send data to the other device")
                }
                writeErrorMsg.data = bundle
                handler.sendMessage(writeErrorMsg)
                return
            }

            // Share the sent message with the UI activity.
            val writtenMsg = handler.obtainMessage(
                MESSAGE_WRITE, -1, -1, mmBuffer
            )
            writtenMsg.sendToTarget()
        }

        // Call this method from the main activity to shut down the connection.
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                log(TAG, "Could not close the connect socket ${e.message}")
            }
        }
    }

    private fun manageMyConnectedSocket(socket: BluetoothSocket) {
        connectedThread?.cancel()
        connectedThread = ConnectedThread(socket).apply { start() }
    }


    private fun PackageManager.missingSystemFeature(name: String): Boolean = !hasSystemFeature(name)
    private fun checkBLE(): Boolean {
        packageManager.takeIf { it.missingSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) }
            ?.also {
                toast("设备不支持蓝牙BLE")
                return false
            }
        return true
    }

    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val device = result.device
            log(TAG, "低功耗扫描到设备：${device.name} MAC:${device.address}")
            if (TextUtils.isEmpty(device.name))
                return
            runOnUiThread {
                val bean = BluetoothDeviceBean(
                    device.name,
                    device.address,
                    device.bondState == BluetoothDevice.BOND_BONDED,
                    device
                )
                if (mAdapter.data.contains(bean))
                    return@runOnUiThread
                mAdapter.data.add(bean)
                mAdapter.notifyDataSetChanged()
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            log(TAG, "" + results?.size)
        }

        override fun onScanFailed(errorCode: Int) {
            log(TAG, "低功耗扫描失败, errorCode:$errorCode")
        }
    }
    private val leScanCallback2 = object : BluetoothAdapter.LeScanCallback{
        override fun onLeScan(d: BluetoothDevice?, rssi: Int, scanRecord: ByteArray?) {
            d?:return
            val device = d
            log(TAG, "低功耗扫描到设备：${device.name} MAC:${device.address}")
            if (TextUtils.isEmpty(device.name))
                return
            runOnUiThread {
                val bean = BluetoothDeviceBean(
                    device.name,
                    device.address,
                    device.bondState == BluetoothDevice.BOND_BONDED,
                    device
                )
                if (mAdapter.data.contains(bean))
                    return@runOnUiThread
                mAdapter.data.add(bean)
                mAdapter.notifyDataSetChanged()
            }
        }
    }
    //开始低功耗蓝牙扫描
    private fun scanLeDevice(enable: Boolean) {
        when (enable) {
            true -> {
                // Stops scanning after a pre-defined scan period.
                handler.postDelayed(Runnable {
                    mScanning = false
                    mBluetoothAdapter?.bluetoothLeScanner?.stopScan(leScanCallback)
                }, SCAN_PERIOD)
                mScanning = true
                mAdapter.data.clear()
                mAdapter.notifyDataSetChanged()
                mBluetoothAdapter?.bluetoothLeScanner?.startScan(leScanCallback)
//                mBluetoothAdapter?.startLeScan(leScanCallback2)
            }
            else -> {
                mScanning = false
                mBluetoothAdapter?.bluetoothLeScanner?.stopScan(leScanCallback)
            }
        }
    }

    // Various callback methods defined by the BLE API.
    private val gattCallback = object : BluetoothGattCallback() {
        var deviceBean: BluetoothDeviceBean? = null
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            val intentAction: String
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    intentAction =
                        ACTION_GATT_CONNECTED
                    connectionState = STATE_CONNECTED
                    broadcastUpdate(intentAction)
                    bluetoothGatt = gatt
                    runOnUiThread {
                        log(TAG, "Connected to GATT server.")
                        log(
                            TAG,
                            "Attempting to start service discovery: " + bluetoothGatt?.discoverServices()
                        )
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    intentAction =
                        ACTION_GATT_DISCONNECTED
                    connectionState = STATE_DISCONNECTED
                    runOnUiThread {
                        log(TAG, "Disconnected from GATT server.")
                    }
                    broadcastUpdate(intentAction)
                }
            }
        }

        // New services discovered
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    val gattService = gatt.getService(UUID.fromString(SERVICE_UUID));// 获取到服务的通道
//                    获取到Notify的Characteristic通道 这个根据协议来定  如果设备厂家给的协议不是Notify的话  就不用做以下操作了
                    if(gattService!=null) {
                        val notifyCharacteristic: BluetoothGattCharacteristic =
                            gattService.getCharacteristic(UUID.fromString(NOTIFY_UUID))
                        enableNotification(gatt, true, notifyCharacteristic) //注册Notify通知
                    }
                    displayGattServices(deviceBean!!, gatt.services)
                }
                else ->
                    runOnUiThread {
                        log(TAG, "onServicesDiscovered received: $status")
                    }
            }
        }

        // Result of a characteristic read operation
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic!!)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if(status==BluetoothGatt.GATT_SUCCESS) {
                //写入成功 可以读取数据了
//            val readCharact: BluetoothGattCharacteristic = gattService.getCharacteristic(UUID.fromString(READUUID))
//            gatt.readCharacteristic(readCharact);
            }
        }

    }

    //向BLE设备写入数据
    private fun writeBleData(bluetoothGattService: BluetoothGattService?,data: String?): Boolean {
        bluetoothGattService?:return false
        val writeCharact: BluetoothGattCharacteristic = bluetoothGattService.getCharacteristic(UUID.fromString(
            WRITE_UUID
        ))
        bluetoothGatt!!.setCharacteristicNotification(writeCharact, true) // 设置监听
        // 当数据传递到蓝牙之后
        // 会回调BluetoothGattCallback里面的write方法
        writeCharact.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        // 将需要传递的数据 打碎成16进制
        writeCharact.setValue(getHexBytes(data!!))
        return bluetoothGatt!!.writeCharacteristic(writeCharact)
    }

    private fun broadcastUpdate(action: String) {
        val intent = Intent(action)
        sendBroadcast(intent)
    }

    private fun broadcastUpdate(action: String, characteristic: BluetoothGattCharacteristic) {
        val intent = Intent(action)
        // This is special handling for the Heart Rate Measurement profile. Data
        // parsing is carried out as per profile specifications.
        when (characteristic.uuid) {
            UUID_HEART_RATE_MEASUREMENT -> {
                val flag = characteristic.properties
                val format = when (flag and 0x01) {
                    0x01 -> {
                        log(TAG, "Heart rate format UINT16.")
                        BluetoothGattCharacteristic.FORMAT_UINT16
                    }
                    else -> {
                        log(TAG, "Heart rate format UINT8.")
                        BluetoothGattCharacteristic.FORMAT_UINT8
                    }
                }
                val heartRate = characteristic.getIntValue(format, 1)
                log(TAG, String.format("Received heart rate: %d", heartRate))
                intent.putExtra(EXTRA_DATA, (heartRate).toString())
            }
            else -> {
                // For all other profiles, writes the data formatted in HEX.
                val data: ByteArray? = characteristic.value
                if (data?.isNotEmpty() == true) {
                    val hexString: String = data.joinToString(separator = " ") {
                        String.format("%02X", it)
                    }
                    intent.putExtra(EXTRA_DATA, "$data\n$hexString")
                }
            }
        }
        sendBroadcast(intent)
    }

    private val gattUpdateReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            when (action) {
                ACTION_GATT_CONNECTED -> {
                    toast("连接成功")
                }
                ACTION_GATT_DISCONNECTED -> {
                    toast("断开连接")
                }
                ACTION_GATT_SERVICES_DISCOVERED -> {

                }
                ACTION_DATA_AVAILABLE -> {
                    val data = intent.getStringExtra(EXTRA_DATA)
                    log(TAG, "收到数据：$data")
                }
            }
        }
    }

    private fun displayGattServices(
        deviceBean: BluetoothDeviceBean,
        gattServices: List<BluetoothGattService>?
    ) {
        if (gattServices == null) return

        var uuid: String?
        val gattServiceData: MutableList<ServiceBean> = mutableListOf()
        var gattCharacteristicData: MutableList<CharacteristicBean> = mutableListOf()

        // Loops through available GATT Services.
        gattServices.forEach { gattService ->
            uuid = gattService.uuid.toString()
            // Loops through available Characteristics.
            gattCharacteristicData = mutableListOf()
            gattService.characteristics.forEach { gattCharacteristic ->
                uuid = gattCharacteristic.uuid.toString()
                val characteristicBean = CharacteristicBean(uuid)
                gattCharacteristicData.add(characteristicBean)
            }
            val serviceBean = ServiceBean(uuid, gattCharacteristicData,gattService)
            gattServiceData.add(serviceBean)
        }
        deviceBean.services = gattServiceData
        deviceBean.isExpand = true
        runOnUiThread {
            mAdapter.notifyDataSetChanged()
        }
    }


    /**
     * 是否开启蓝牙的通知
     *
     * @param enable
     * @param characteristic
     * @return
     */
    @SuppressLint("NewApi")
    fun enableNotification(
        bluetoothGatt: BluetoothGatt?,
        enable: Boolean,
        characteristic: BluetoothGattCharacteristic?
    ): Boolean {
        if (bluetoothGatt == null || characteristic == null) {
            return false
        }
        if (!bluetoothGatt.setCharacteristicNotification(characteristic, enable)) {
            return false
        }
        //获取到Notify当中的Descriptor通道  然后再进行注册
        val clientConfig = characteristic.getDescriptor(UUID.fromString(NOTIFY_DESCRIPTOR)) ?: return false
        if (enable) {
            clientConfig.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            clientConfig.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        }
        return bluetoothGatt.writeDescriptor(clientConfig)
    }

    private fun closeGatt() {
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toast("已获得位置权限。")
            } else {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                ) {
                    toast("请授予位置权限！")
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                toast("蓝牙开启成功！")
            } else {
                toast("蓝牙开启失败！")
            }
        } else if (requestCode == REQUEST_ENABLE_DISCOVERABILITY) {
            if (resultCode == 300) {
                log(TAG, "开启可检测性成功，时间为${resultCode}秒")
            } else {
                log(TAG, "开启可检测性失败")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unRegisterBroadcast()
        connectThread?.cancel()
        connectedThread?.cancel()
        closeGatt()
    }

}



