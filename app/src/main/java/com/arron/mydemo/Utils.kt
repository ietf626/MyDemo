package com.arron.mydemo

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import com.arron.mydemo.bluetooth.view.NOTIFY_DESCRIPTOR
import java.util.*
import kotlin.experimental.and


/**

 * @Author Arron
 * @Date 2020/4/16 0016-21:34
 * @Email
 */
fun toast(msg:String){
    Toast.makeText(App.context,msg, Toast.LENGTH_SHORT).show()
}

fun log(tag:String,msg:String){
    logLiveData.value = "$tag-$msg"
    Log.d(tag,msg)
}

val logLiveData = MutableLiveData<String>()

/**
 * 将字节 转换为字符串
 *
 * @param src 需要转换的字节数组
 * @return 返回转换完之后的数据
 */
fun bytesToHexString(src: ByteArray?): String? {
    val stringBuilder = StringBuilder("")
    if (src == null || src.size <= 0) {
        return null
    }
    for (i in src.indices) {
        val v: Int = (src[i] and 0xFF.toByte()).toInt()
        val hv = Integer.toHexString(v)
        if (hv.length < 2) {
            stringBuilder.append(0)
        }
        stringBuilder.append(hv)
    }
    return stringBuilder.toString()
}

/**
 * 将字符串转化为16进制的字节
 *
 * @param message
 * 需要被转换的字符
 * @return
 */
fun getHexBytes(message: String): ByteArray? {
    val len = message.length / 2
    val chars = message.toCharArray()
    val hexStr = arrayOfNulls<String>(len)
    val bytes = ByteArray(len)
    var i = 0
    var j = 0
    while (j < len) {
        hexStr[j] = "" + chars[i] + chars[i + 1]
        bytes[j] = hexStr[j]!!.toInt(16).toByte()
        i += 2
        j++
    }
    return bytes
}
class Utils {

}