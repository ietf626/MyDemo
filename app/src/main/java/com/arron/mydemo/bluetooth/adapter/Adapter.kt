package com.arron.mydemo.bluetooth.adapter

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arron.mydemo.R
import com.arron.mydemo.bluetooth.view.bluetoothGattServiceLiveData
import com.arron.mydemo.bluetooth.model.BluetoothDeviceBean
import com.arron.mydemo.bluetooth.model.CharacteristicBean
import com.arron.mydemo.bluetooth.model.ServiceBean
import kotlinx.android.synthetic.main.item_bluetooth.view.*
import kotlinx.android.synthetic.main.item_characteristic.view.*
import kotlinx.android.synthetic.main.item_service.view.*

/**

 * @Author Arron
 * @Date 2020/4/18 0018-12:56
 * @Email
 */
class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
class MyAdapter(
    val data: MutableList<BluetoothDeviceBean>,
    val itemClick: (item: BluetoothDeviceBean) -> Unit
) : RecyclerView.Adapter<MyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        return MyViewHolder(View.inflate(parent.context, R.layout.item_bluetooth, null))
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val bean = data[position]
        holder.itemView.apply {
            tv_name.text = bean.name
            tv_mac.text = bean.mac
            tv_status.text = if (bean.isPaired) "已配对" else ""
            recycler_services.layoutManager = LinearLayoutManager(context)
            val data = if (bean.services == null) arrayListOf() else bean.services
            recycler_services.adapter = if (bean.isExpand) {
                ServicesAdapter(data, {})
            } else {
                ServicesAdapter(arrayListOf(), {})
            }
            setOnClickListener {
                if (bean.services != null) {
                    bean.isExpand = !bean.isExpand
                    notifyItemChanged(position)
                } else {
                    itemClick.invoke(bean)
                }
            }
        }
    }
}

class ServicesAdapter(
    val data: MutableList<ServiceBean>?,
    val itemClick: (item: ServiceBean) -> Unit
) : RecyclerView.Adapter<MyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        return MyViewHolder(View.inflate(parent.context, R.layout.item_service, null))
    }

    override fun getItemCount(): Int {
        return if (data == null) 0 else data?.size
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val bean = data!![position]
        holder.itemView.apply {
            tv_service.text = "Service: ${bean.uuid}"
            recycler_character.layoutManager = LinearLayoutManager(context)
            val data = if (bean.characteristics == null) arrayListOf() else bean.characteristics
            recycler_character.adapter = if (bean.isExpand) {
                CharacteristicsAdapter(data.toMutableList(), {})
            } else {
                CharacteristicsAdapter(arrayListOf(), {})
            }
            setOnClickListener {
                bean.isExpand = !bean.isExpand
                bluetoothGattServiceLiveData.value = bean.service
                notifyItemChanged(position)
            }
        }
    }
}

class CharacteristicsAdapter(
    val data: MutableList<CharacteristicBean>,
    val itemClick: (item: CharacteristicBean) -> Unit
) : RecyclerView.Adapter<MyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        return MyViewHolder(View.inflate(parent.context, R.layout.item_characteristic, null))
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val bean = data[position]
        holder.itemView.apply {
            tv_character.text = "Characteristic: ${bean.uuid}"
        }
    }
}