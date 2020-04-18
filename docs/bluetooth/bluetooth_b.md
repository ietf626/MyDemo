## Android 蓝牙技术（二）- 低功耗蓝牙
[Android 蓝牙技术（一）- 经典蓝牙](bluetooth_a.md)

[Android 蓝牙技术（二）- 低功耗蓝牙](bluetooth_b.md)

[配套设备配对](bluetooth_c.md)

Android 4.3（API 级别 18）开始内置支持。与传统蓝牙不同，蓝牙低功耗 (BLE) 旨在提供显著降低的功耗。这使 Android 应用可与功率要求更严格的 BLE 设备（例如近程传感器、心率监测仪和健身设备）通信。

###  使用场景有：
- 在临近设备间传输少量数据。
- 
- 与 Google Beacons 等近程传感器交互，以便为用户提供基于其当前位置的自定义体验。

**注意：** 当用户使用 BLE 将其设备与其他设备配对时，用户设备上的所有应用都可以访问在这两个设备间传输的数据。
因此，如果您的应用捕获敏感数据，您应实现应用层安全以保护此类数据的私密性。

### 关键概念：
- 通用属性配置文件 (GATT) — GATT 配置文件是一种通用规范，内容针对在 BLE 链路上发送和接收称为“属性”的简短数据片段。目前所有低功耗应用配置文件均以 GATT 为基础。
蓝牙特别兴趣小组 (Bluetooth SIG) 为低功耗设备定义诸多配置文件。配置文件是描述设备如何在特定应用中工作的规范。请注意，一台设备可以实现多个配置文件。例如，一台设备可能包含心率监测仪和电池电量检测器。

- 属性协议 (ATT) — 属性协议 (ATT) 是 GATT 的构建基础，二者的关系也被称为 GATT/ATT。ATT 经过优化，可在 BLE 设备上运行。为此，该协议尽可能少地使用字节。每个属性均由通用唯一标识符 (UUID) 进行唯一标识，后者是用于对信息进行唯一标识的字符串 ID 的 128 位标准化格式。由 ATT 传输的属性采用特征和服务格式。

- Characteristic — 特征包含一个值和 0 至多个描述特征值的描述符。您可将特征理解为类型，后者与类类似。

- Descriptor — 描述符是描述特征值的已定义属性。例如，描述符可指定人类可读的描述、特征值的可接受范围或特定于特征值的度量单位。

- Service — 服务是一系列特征。例如，您可能拥有名为“心率监测器”的服务，其中包括“心率测量”等特征。您可以在 bluetooth.org 上找到基于 GATT 的现有配置文件和服务的列表。

### 角色和职责
以下是 Android 设备与 BLE 设备交互时应用的角色和职责：
- 中央与外围。这适用于 BLE 连接本身。担任中央角色的设备进行扫描、寻找广播；外围设备发出广播。
- GATT 服务器与 GATT 客户端。这确定两个设备建立连接后如何相互通信。
- 
简单得说，要建立 BLE 连接必须具备这两个两个角色—如果两个设备都仅支持中央或外围角色，则无法相互通信，交互数据时
谁提供数据谁就充当 GATT 服务器，客户端从服务器拿取数据。


### 声明权限
权限声明和经典蓝牙类似，如果要使用设备发现或操作蓝牙设置要声明 BLUETOOTH_ADMIN 权限，此权限不能单独声明，要先声明
BLUETOOTH 权限。
```
<uses-permission android:name="android.permission.BLUETOOTH"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>

<!-- targets Android 9 或以下, 可以用
     ACCESS_COARSE_LOCATION 代替. -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```
声明应用仅适用支持BLE的设备：
```
<uses-feature android:name="android.hardware.bluetooth_le" android:required="true"/>
```
如果需要不支持BLE的设备也能安装，则设置 required="false" ，然后在代码中运行时判断是否支持BLE。
```
private fun PackageManager.missingSystemFeature(name: String): Boolean = !hasSystemFeature(name)
...
packageManager.takeIf { it.missingSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) }?.also {
    Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show()
    finish()
}
```

### 设置 BLE
在您的应用程序可以通过 BLE 进行通信之前，您需验证设备是否支持 BLE，如果支持，请确保该功能已启用。请注意，仅当 <uses-feature.../> 设置为 false 时才需要进行此检查。
1. 获取 BluetoothAdapter
```
private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
    val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    bluetoothManager.adapter
}
```
2. 启用蓝牙
```
private val BluetoothAdapter.isDisabled: Boolean
    get() = !isEnabled
...

// Ensures Bluetooth is available on the device and it is enabled. If not,
// displays a dialog requesting user permission to enable Bluetooth.
bluetoothAdapter?.takeIf { it.isDisabled }?.apply {
    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
}
```
### 查找 BLE 设备
需要注意：
- 找到所需设备后，立即停止扫描。
- 绝对不进行循环扫描，并设置扫描时间限制。之前可用的设备可能已超出范围，继续扫描会耗尽电池电量。

startLeScan() API 已经过时，建议使用以下 API：
```
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
            }
            else -> {
                mScanning = false
                mBluetoothAdapter?.bluetoothLeScanner?.stopScan(leScanCallback)
            }
        }
    }
```
扫描方法支持传入 UUID 参数，扫描特定类型外围设备，以下时扫描结果回调：
```
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
```
**注意：** 仅能扫描蓝牙 LE 设备或传统蓝牙设备，无法同时扫描蓝牙 LE 设备和传统蓝牙设备。

### 连接到 GATT 服务器
```
var bluetoothGatt: BluetoothGatt? = null
...

bluetoothGatt = device.connectGatt(this, false, gattCallback)
```
### 连接结果回调
```
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
```

###  读取 BLE 属性
应用成功连接到 GATT 服务器并发现服务后，应用便可在支持的位置读取和写入属性。例如，以下代码段遍历服务器的服务和特征，并在界面中将其显示出来：
```
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
```

### 接收 GATT 通知
使用 setCharacteristicNotification() 方法设置特征的通知：
```
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
```
为某个设备开启通知后，如果远程设备上的特征发生更改，则会触发 onCharacteristicChanged() 回调：
```
       override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic!!)
        }
```

### 关闭客户端应用
当应用完成对 BLE 设备的使用后，其应调用 close()，以便系统可以适当地释放资源：
```
fun close() {
    bluetoothGatt?.close()
    bluetoothGatt = null
}
```

