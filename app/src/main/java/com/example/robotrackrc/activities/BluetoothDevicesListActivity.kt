package com.example.robotrackrc.activities

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.robotrackrc.data.Data
import com.example.robotrackrc.databinding.ActivityBluetoothDevicesListBinding
import com.example.robotrackrc.datamodel.BluetoothDeviceItem
import com.example.robotrackrc.recyclerview.BtDevicesListAdapter

class BluetoothDevicesListActivity: AppCompatActivity(), BtDevicesListAdapter.Listener {
    private lateinit var binding: ActivityBluetoothDevicesListBinding
    private lateinit var rcadapter: BtDevicesListAdapter
    private lateinit var btAdapter: BluetoothAdapter

    // Настройки приложения
    private lateinit var appSettings: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBluetoothDevicesListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /** Инициализация Shared Preferences */
        appSettings = getSharedPreferences("Settings", MODE_PRIVATE)
        editor = appSettings.edit()

        /** Инициализация bluetooth adapter */
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        btAdapter = btManager.adapter

        /** Обработка нажатий на кнопку для перехода в настройки bluetooth */
        binding.addNewDeviceButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }

        /** Появление подсказки */
        Toast.makeText(this,
            "Если нужного устройства нет в списке, нажмите на кнопку справа",
            Toast.LENGTH_LONG).show()

        /** Закрыть страницу выбора устройства */
        binding.closeButton.setOnClickListener {
            finish()
            overridePendingTransition(0,0)
        }

    }

    override fun onResume() {
        super.onResume()

        getPairedDevices()

        /** Инициализация RecyclerView(списка bluetooth устройств) */
        rcadapter = BtDevicesListAdapter(this)
        binding.bluetoothRcView.layoutManager = LinearLayoutManager(this)
        binding.bluetoothRcView.adapter = rcadapter
        rcadapter.submitList(Data.BtDevicesList)

        /** Чтение настроек: не отключать подсветку экрана */
        if (appSettings.contains("keepScreenOn")){
            if (appSettings.getBoolean("keepScreenOn", false)){
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    /** Получение  списка ранее подключенных bluetooth устройств */
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

    /** Обработчик нажатий на элементы списка */
    override fun onClick(item: BluetoothDeviceItem) {
        val intent = Intent()
            .putExtra("deviceName", item.deviceName)
            .putExtra("deviceMac", item.deviceMac)
        setResult(RESULT_OK, intent)// Передаем информацию о устройстве на главную активити
        finish()
    }


}