package com.example.robotrackrc.activities

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.robotrackrc.data.Data
import com.example.robotrackrc.databinding.ActivityBluetoothSettingsBinding
import com.example.robotrackrc.model.BluetoothDeviceItem
import com.example.robotrackrc.recyclerview.BtDevicesListAdapter

class BluetoothSettingsActivity: AppCompatActivity(), BtDevicesListAdapter.Listener {
    private lateinit var binding: ActivityBluetoothSettingsBinding
    private lateinit var rcadapter: BtDevicesListAdapter
    private lateinit var btAdapter: BluetoothAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBluetoothSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /**Инициализация bluetooth adapter*/
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        btAdapter = btManager.adapter

        getPairedDevices()

        /**Инициализация RecyclerView(списка bluetooth устройств)*/
        rcadapter = BtDevicesListAdapter(this)
        binding.bluetoothRcView.layoutManager = LinearLayoutManager(this)
        binding.bluetoothRcView.adapter = rcadapter
        rcadapter.submitList(Data.BtDevicesList)

        binding.addNewDeviceButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }

    }

    /**Получение  списка ранее подключенных bluetooth устройств*/
    private fun getPairedDevices() {
        try {
            if (Data.BtDevicesList.size > 0) Data.BtDevicesList.clear()
            val pairedDevices: Set<BluetoothDevice> = btAdapter.bondedDevices
            pairedDevices.forEach {
                Data.BtDevicesList.add(BluetoothDeviceItem(it.name, it.address))
            }
        } catch (e: SecurityException){
            Toast.makeText(this, "Ошибка получения списка устройств", Toast.LENGTH_SHORT).show()
        }

    }

    /**Обработчик нажатий на элементы списка*/
    override fun onClick(item: BluetoothDeviceItem) {
        val intent = Intent()
            .putExtra("deviceName", item.deviceName)
            .putExtra("deviceMac", item.deviceMac)
        setResult(RESULT_OK, intent)// Передаем информацию о устройстве на главную активити
        finish()
    }


}