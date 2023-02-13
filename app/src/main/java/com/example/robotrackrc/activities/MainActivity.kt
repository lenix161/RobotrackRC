package com.example.robotrackrc.activities

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.example.robotrackrc.BtConnector
import com.example.robotrackrc.R
import com.example.robotrackrc.databinding.ActivityMainBinding
import com.example.robotrackrc.threads.ConnectThread

class MainActivity : AppCompatActivity(), ConnectThread.Listener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var btAdapter: BluetoothAdapter
    private lateinit var launcher: ActivityResultLauncher<Intent>
    private lateinit var btConnector: BtConnector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /**Инициализация bluetooth adapter*/
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        btAdapter = btManager.adapter

        /**Всплывающее окно для включения bluetooth, если он выключен*/
        if (!btAdapter.isEnabled){
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            try {
                startActivityForResult(enableBtIntent, RESULT_OK)
            } catch (e: SecurityException){
                Toast.makeText(this, "Ошибка включения bluetooth", Toast.LENGTH_SHORT).show()
            }
        }
        initBtConnector()
        registerActivityLauncher()

        /**Обработчик нажатий на кнопку bluetooth*/
        binding.bluetoothButton.setOnClickListener {
            if (!btAdapter.isEnabled){
                Toast.makeText(this, "Включите bluetooth"
                    , Toast.LENGTH_SHORT).show()
            } else {
                launcher.launch(Intent(this, BluetoothSettingsActivity::class.java))
            }
        }

        //binding.leftJoystick.isAutoReCenterButton = false
        binding.leftJoystick.setOnMoveListener { angle, strength ->
            binding.leftAngle.text = "Angle: $angle"
            binding.leftStrength.text = "Strength: $strength"
            if(angle > 180){
                btConnector.sendMessage(-strength)
            } else {
                btConnector.sendMessage(strength)
            }

        }

        binding.rightJoystick.setOnMoveListener { angle, strength ->
            binding.rightAngle.text = "Angle: $angle"
            binding.rightStrength.text = "Strength: $strength"
        }

    }

    /**Launcher для activityForResult (получение названия выбранного bluetooth устройства)*/
    private fun registerActivityLauncher(){
        launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            if (it.resultCode == RESULT_OK){
                val deviceName = it.data?.getStringExtra("deviceName")
                val deviceMac = it.data?.getStringExtra("deviceMac")
                //Получаем данные о устройстве и подключаемся по MAC адресу
                btConnector.connect(deviceMac.toString())
                binding.connectedDeviceName.text = deviceName
                binding.connectedDeviceName.setTextColor(resources.getColor(R.color.green))
                binding.bluetoothButton.setColorFilter(resources.getColor(R.color.blue))
                binding.connectionStatus.text = deviceMac
            }
        }
    }

    /**Инициализация bluetooth коннектора*/
    private fun initBtConnector(){
        btConnector = BtConnector(btAdapter, this)
    }

    /**Изменение статуса подключения*/
    override fun onConnect(status: String) {
        runOnUiThread{
           if (status == "Отключено") with(binding){
               connectionStatus.text = status
               connectedDeviceName.text = "Подключите устройство"
               connectedDeviceName.setTextColor(resources.getColor(R.color.red))
           } else{
                binding.connectionStatus.text = status
           }

        }
    }


}