package com.arron.mydemo.bluetooth.model

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattService
import android.text.TextUtils

/**

 * @Author Arron
 * @Date 2020/4/17 0017-13:51
 * @Email
 */
class BluetoothDeviceBean {
    var name:String? = null
    var mac:String? = null
    var isPaired:Boolean = false
    var device:BluetoothDevice? = null
    var services:MutableList<ServiceBean>? = null
    var isExpand = false
    constructor(name:String?,mac:String?,isPaired:Boolean,device:BluetoothDevice?){
        this.name = name
        this.mac = mac
        this.isPaired = isPaired
        this.device = device
    }
    constructor(name:String?,mac:String?,services:MutableList<ServiceBean>){
        this.name = name
        this.mac = mac
        this.services = services
    }

    override fun equals(other: Any?): Boolean {
        return TextUtils.equals(mac,(other as BluetoothDeviceBean).mac)
    }
}

data class ServiceBean(val uuid:String?,val characteristics:List<CharacteristicBean>,
                       val service:BluetoothGattService,var isExpand:Boolean = false){

}

class CharacteristicBean(val uuid:String?){

}