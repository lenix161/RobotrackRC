package com.example.robotrackrc.activities

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.codertainment.dpadview.DPadView
import com.example.robotrackrc.BtConnector
import com.example.robotrackrc.R
import com.example.robotrackrc.databinding.ActivityMainBinding
import com.example.robotrackrc.threads.ConnectThread


class MainActivity : AppCompatActivity(), ConnectThread.Listener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var btAdapter: BluetoothAdapter
    private lateinit var launcherBtSettingsActivity: ActivityResultLauncher<Intent>
    private lateinit var launcherBtEnableActivity: ActivityResultLauncher<Intent>
    private lateinit var btConnector: BtConnector
    private var leftX = 0
    private var leftY = 0
    private var rightX = 0
    private var rightY = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /** Инициализация bluetooth adapter */
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        btAdapter = btManager.adapter

        registerBtEnableActivityLauncher()

        /** Всплывающее окно для включения bluetooth, если он выключен */
        if (!btAdapter.isEnabled){
            try {
                launcherBtEnableActivity.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (e: SecurityException){
                Toast.makeText(this, "Ошибка включения bluetooth", Toast.LENGTH_SHORT).show()
            }
        }

        initBtConnector()
        registerBtSettingsActivityLauncher()

        /** Обработчик нажатий на кнопку bluetooth */
        binding.bluetoothButton.setOnClickListener {
            if (!btAdapter.isEnabled){
                Toast.makeText(this, "Включите bluetooth"
                    , Toast.LENGTH_SHORT).show()
            } else {
                launcherBtSettingsActivity
                    .launch(Intent(this, BluetoothSettingsActivity::class.java))
            }
        }

        binding.leftDpad.centerCircleRatio = 4.0f
        binding.rightDpad.centerCircleRatio = 4.0f

        /** Обработка взаимодействия с левым Dpad контроллером */
        binding.leftDpad.onDirectionPressListener = { direction, action ->
            if (direction != null) {
                dpadListener(direction, action, "left")
            }
        }

        binding.rightDpad.onDirectionPressListener = {direction, action ->
            if (direction != null) {
                dpadListener(direction, action, "right")
            }
        }

        /** Инициализация прослушивателя взаимодействий с левым переключателем */
        binding.switchLeftControllerType.setOnCheckedChangeListener { buttonView, isChecked ->
            switchListener(isChecked, "left")
        }

        /** Инициализация прослушивателя взаимодействий с правым переключателем */
        binding.switchRightControllerType.setOnCheckedChangeListener { buttonView, isChecked ->
            switchListener(isChecked, "right")
        }

        /** Инициализация прослушивателя взаимодействий с левым джойстиком */
        binding.leftJoystick.isAutoReCenterButton = false
        binding.leftJoystick.setOnMoveListener { angle, strength ->
            joystickListener(angle,strength, "left")
        }

        /** Инициализация прослушивателя взаимодействий с правым джойстиком */
        binding.rightJoystick.setOnMoveListener { angle, strength ->
            joystickListener(angle,strength, "right")
        }

        /** Запуск потока для отправки данных по bluetooth */
        val thread = Thread(task, "BtSendThread")
        thread.start()
    }

    /** Обработка взаимодействий с переключателем */
    private fun switchListener(isChecked:Boolean, side: String){
        when(side){
            "left" -> {
                if (isChecked){
                    binding.leftJoystick.visibility = View.GONE
                    binding.leftDpad.visibility = View.VISIBLE
                    leftX = 0
                    leftY = 0
                    // Обновляем UI
                    binding.leftX.text = "X: $leftX"
                    binding.leftY.text = "Y: $leftY"
                } else {
                    binding.leftJoystick.visibility = View.VISIBLE
                    binding.leftJoystick.resetButtonPosition()
                    binding.leftDpad.visibility = View.GONE
                }
            }

            "right" -> {
                if (isChecked){
                    binding.rightJoystick.visibility = View.GONE
                    binding.rightDpad.visibility = View.VISIBLE
                    rightX = 0
                    rightY = 0
                    // Обновляем UI
                    binding.rightX.text = "X: $rightX"
                    binding.rightY.text = "Y: $rightY"
                } else {
                    binding.rightJoystick.visibility = View.VISIBLE
                    binding.rightJoystick.resetButtonPosition()
                    binding.rightDpad.visibility = View.GONE
                }
            }
        }
    }

    private fun dpadListener(direction: DPadView.Direction, action: Int, side: String){
        when (side){
            "left" -> {
                if (action == MotionEvent.ACTION_DOWN){
                    when(direction){
                        DPadView.Direction.UP -> {leftX = 0; leftY = 100}
                        DPadView.Direction.DOWN -> {leftX = 0; leftY = -100}
                        DPadView.Direction.LEFT -> {leftX = -100; leftY = 0}
                        DPadView.Direction.RIGHT -> {leftX = 100; leftY = 0}
                        else -> {}
                    }
                    // Обновляем UI
                    binding.leftX.text = "X: $leftX"
                    binding.leftY.text = "Y: $leftY"
                } else if (action == MotionEvent.ACTION_UP){
                    leftX = 0
                    leftY = 0
                    // Обновляем UI
                    binding.leftX.text = "X: $leftX"
                    binding.leftY.text = "Y: $leftY"
                }
            }

            "right" -> {
                if (action == MotionEvent.ACTION_DOWN){
                    when(direction){
                        DPadView.Direction.UP -> {rightX = 0; rightY = 100}
                        DPadView.Direction.DOWN -> {rightX = 0; rightY = -100}
                        DPadView.Direction.LEFT -> {rightX = -100; rightY = 0}
                        DPadView.Direction.RIGHT -> {rightX = 100; rightY = 0}
                        else -> {}
                    }
                    // Обновляем UI
                    binding.rightX.text = "X: $rightX"
                    binding.rightY.text = "Y: $rightY"
                } else if (action == MotionEvent.ACTION_UP){
                    rightX = 0
                    rightY = 0
                    // Обновляем UI
                    binding.rightX.text = "X: $rightX"
                    binding.rightY.text = "Y: $rightY"
                }
            }
        }
    }

    /** Обработка взаимодействий с джойстиком */
    private fun joystickListener(angle: Int, strength: Int, side: String){
        when(side){
            "left" -> {
                val xy = getJoystickXYCoordinate(angle, strength)
                leftX = xy.first.toInt()
                leftY = xy.second.toInt()

                // Обновляем UI
                binding.leftX.text = "X: $leftX"
                binding.leftY.text = "Y: $leftY"
            }

            "right" -> {
                val xy = getJoystickXYCoordinate(angle, strength)
                rightX = xy.first.toInt()
                rightY = xy.second.toInt()

                // Обновляем UI
                binding.rightX.text = "X: $rightX"
                binding.rightY.text = "Y: $rightY"
            }
        }

    }


    /** Получить координаты джойстика */
    fun getJoystickXYCoordinate(angle: Int, strength: Int): Pair<Double, Double>{
        // Находим sin & cos для дальнейшего нахождения координат
        val u = (Math.cos(angle.toDouble() * Math.PI/180)) +
                (if (Math.cos(angle.toDouble() * Math.PI/180) < 0.0) 0.0001 else -0.0001)

        val v = (Math.sin(angle.toDouble() * Math.PI/180)) +
                (if (Math.sin(angle.toDouble() * Math.PI/180) < 0.0) 0.0001 else -0.0001)

        // Преобразование квадратной координатной плоскости в координатную полскость окружности
        // В угле 45 градусов было x:60 y:60, стало в угле 45 градусов x:100 y:100
        // Поместили квадрат в окружность
        val x = (Math.abs(u*v) / (u*v) / (v * Math.sqrt(2.0))) *
                Math.sqrt(u*u + v*v - Math.sqrt((u*u + v*v) * (u*u + v*v - 4 * u*u * v*v))) *
                strength

        val y = (Math.abs(u*v) / (u*v) / (u * Math.sqrt(2.0))) *
                Math.sqrt(u*u + v*v - Math.sqrt((u*u + v*v) * (u*u + v*v - 4 * u*u * v*v))) *
                strength

        return Pair(x,y)
    }

    /** Launcher для activityForResult (включение bluetooth, если он выключен) */
    private fun registerBtEnableActivityLauncher(){
        launcherBtEnableActivity =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            if (it.resultCode == RESULT_OK){
                binding.bluetoothButton.setColorFilter(resources.getColor(R.color.blue))
            }
        }
    }

    /** Launcher для activityForResult (получение названия выбранного bluetooth устройства) */
    private fun registerBtSettingsActivityLauncher(){
        launcherBtSettingsActivity =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            if (it.resultCode == RESULT_OK){
                val deviceName = it.data?.getStringExtra("deviceName")
                val deviceMac = it.data?.getStringExtra("deviceMac")
                // Получаем данные о устройстве и подключаемся по MAC адресу
                btConnector.connect(deviceMac.toString())

                // Обновляем UI
                binding.connectedDeviceName.text = deviceName
                binding.connectedDeviceName.setTextColor(resources.getColor(R.color.green))
                binding.bluetoothButton.setColorFilter(resources.getColor(R.color.blue))
                binding.connectionStatus.text = deviceMac
            }
        }
    }

    /** Инициализация bluetooth коннектора */
    private fun initBtConnector(){
        btConnector = BtConnector(btAdapter, this)
    }

    /** Изменение статуса подключения */
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

    /** Поток для отправоления данных: координаты XY с двух джойстиков */
    private val task = Runnable {
        Log.d("MyLog", "Start send thread")
        while (!Thread.interrupted()) {
            if (ConnectThread.isConnected) {
                btConnector.sendMessage(listOf(leftX, leftY, rightX, rightY))
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
    }

}