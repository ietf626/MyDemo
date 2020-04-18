package com.arron.mydemo

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.arron.mydemo.bluetooth.view.BluetoothActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setListeners()
    }

    private fun setListeners(){
        c_bluetooth.setOnClickListener { startActivity(Intent(this,
            BluetoothActivity::class.java)) }
    }
}
