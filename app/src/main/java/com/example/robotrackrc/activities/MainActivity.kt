package com.example.robotrackrc.activities

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.codertainment.dpadview.DPadView
import com.example.robotrackrc.BtConnector
import com.example.robotrackrc.R
import com.example.robotrackrc.databinding.ActivityMainBinding
import com.example.robotrackrc.threads.ConnectThread


class MainActivity : AppCompatActivity(), ConnectThread.Listener, SensorEventListener, OnTouchListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var btAdapter: BluetoothAdapter
    private lateinit var launcherBtSettingsActivity: ActivityResultLauncher<Intent>
    private lateinit var launcherBtEnableActivity: ActivityResultLauncher<Intent>
    private lateinit var btConnector: BtConnector

    // Датчик гироскопа
    private lateinit var sensorManager: SensorManager
    private lateinit var sensor: Sensor

    // Положение левого и правого джойстиков
    private var leftX = 0
    private var leftY = 0
    private var rightX = 0
    private var rightY = 0

    // Показания гироскопа
    private var ax = 0.0f
    private var ay = 0.0f
    private var az = 0.0f

    private var offsetax = 0.0f
    private var offsetay = 0.0f
    private var offsetaz = 0.0f

    // Кнопки F1-F6
    private var f1 = false
    private var f2 = false
    private var f3 = false
    private var f4 = false
    private var f5 = false
    private var f6 = false

    // Флаг того, что показания гироскопа переключены на вид с камеры
    private var isCameraStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val webView: WebView = binding.webView
        webView.webViewClient = WebViewClient()
        webView.loadUrl("https://www.youtube.com")

        webView.settings.javaScriptEnabled = true

        binding.resetGyroPositionButton.setOnClickListener {
            offsetax += ax
            offsetay += ay
            offsetaz += az
            Log.d("MyLog", "ax: $offsetax, ay: $offsetay, az: $offsetaz")
        }

        /** Инициализация обработчика нажатий кнопки переключения гироскоп - камера */
        binding.switchGyroAndCamButton.setOnClickListener { switchGyroAndCamButtonListener()}


        /** Инициализация обработчика нажатий кнопок F1-F3 */
        binding.f1Button.setOnTouchListener(this)
        binding.f2Button.setOnTouchListener(this)
        binding.f3Button.setOnTouchListener(this)

        /** Инициализация обработчика нажатий кнопок F4-F6 */
        binding.f4Button.setOnCheckedChangeListener { _, isChecked -> f4 = isChecked }
        binding.f5Button.setOnCheckedChangeListener { _, isChecked -> f5 = isChecked }
        binding.f6Button.setOnCheckedChangeListener { _, isChecked -> f6 = isChecked }


        /** Инициализация менеджера  датчиков гироскопа */
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)


        /** Инициализация bluetooth adapter */
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        btAdapter = btManager.adapter



        /** Callback для  всплывающего окна включения BT*/
        registerBtEnableActivityLauncher()

        /** Всплывающее окно для запроса разрешений и включения bluetooth, если он выключен */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestMultiplePermissions.launch(arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT))
        }
        else{
            if (!btAdapter.isEnabled){
                try {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    launcherBtEnableActivity.launch(Intent(enableBtIntent))
                } catch (e: SecurityException){
                    Toast.makeText(this, "Ошибка включения bluetooth", Toast.LENGTH_SHORT).show()
                }
            }
        }



        initBtConnector()
        /** Callback для BtSettingsActivity */
        registerBtSettingsActivityLauncher()



        /** Обработчик нажатий на кнопку bluetooth */
        binding.bluetoothButton.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED){
                requestMultiplePermissions.launch(arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT))
            }
            else if (!btAdapter.isEnabled){
                // Включить  bluetooth, если он выключен
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                launcherBtEnableActivity.launch(enableBtIntent)
            } else {
                // Проверка на то, что подключение уже установлено
                if(!ConnectThread.isConnected){
                    //Если подключение еще не установлено
                    if (!btAdapter.isEnabled){
                        Toast.makeText(this, "Включите bluetooth"
                            , Toast.LENGTH_SHORT).show()
                    } else {
                        launcherBtSettingsActivity
                            .launch(Intent(this, BluetoothSettingsActivity::class.java))
                    }
                } else {
                    // Если подключение уже установлено, появится диалоговое окно
                    //  для уточнения намерений пользователя
                    val builder = AlertDialog.Builder(this)
                    builder.setMessage("Отключиться?")
                        .setPositiveButton("Да") { dialog, id ->
                            btConnector.disconnect()
                        }
                        .setNegativeButton("Нет") { dialog, id ->
                            // User cancelled the dialog
                        }
                    // Create the AlertDialog object and return it
                    builder.create()
                    builder.show()

                }
            }
        }

        // Размеры внутреннего круга Dpad
        binding.leftDpad.centerCircleRatio = 4.0f
        binding.rightDpad.centerCircleRatio = 4.0f

        /** Обработка взаимодействия с левым Dpad контроллером */
        binding.leftDpad.onDirectionPressListener = { direction, action ->
            if (direction != null) {
                dpadListener(direction, action, "left")
            }
        }

        /** Обработка взаимодействия с правым Dpad контроллером */
        binding.rightDpad.onDirectionPressListener = {direction, action ->
            if (direction != null) {
                dpadListener(direction, action, "right")
            }
        }

        /** Инициализация прослушивателя взаимодействий с левым переключателем */
        binding.switchLeftControllerType.setOnCheckedChangeListener { _, isChecked ->
            switchJoystickTypeListener(isChecked, "left")
        }

        /** Инициализация прослушивателя взаимодействий с правым переключателем */
        binding.switchRightControllerType.setOnCheckedChangeListener { _, isChecked ->
            switchJoystickTypeListener(isChecked, "right")
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

    override fun onResume() {
        super.onResume()
        // Включение датчика гироскопа
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
    }

    override fun onPause() {
        super.onPause()
        // Выключение датчика гироскопа
        sensorManager.unregisterListener(this)
    }


    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                if (it.key == "android.permission.BLUETOOTH_CONNECT" && !it.value){
                    Toast.makeText(this, "Необходимо выдать разрешение для работы приложения",
                        Toast.LENGTH_SHORT).show()
                }
                Log.d("test006", "${it.key} = ${it.value}")
            }
        }


    /** Обработка нажатий на кнопку переключения гироскоп - камера */
    private fun switchGyroAndCamButtonListener(){
        if (!isCameraStarted){
            isCameraStarted = true
            binding.coordinatesContainer.visibility = View.GONE
            binding.webView.visibility = View.VISIBLE
            binding.switchGyroAndCamButton.setImageDrawable(resources.getDrawable(R.drawable.cam_to_gyro_btn))
        } else {
            isCameraStarted = false
            binding.webView.visibility = View.GONE
            binding.coordinatesContainer.visibility = View.VISIBLE
            binding.switchGyroAndCamButton.setImageDrawable(resources.getDrawable(R.drawable.gyro_to_cam_btn))
        }
    }

    /** Обработка взаимодействий с переключателем типов джойстика */
    private fun switchJoystickTypeListener(isChecked:Boolean, side: String){
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
    /** Обработка взаимодействий с Dpad */
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
            }else{
                Toast.makeText(this, "Необходимо включить bluetooth для работы приложения",
                    Toast.LENGTH_SHORT).show()
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

    /** Снятие показаний с гироскопа */
    override fun onSensorChanged(event: SensorEvent?) {
        val rotationMatrix = FloatArray(16)
        if (event != null) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix,event.values)
        }

        val remappedRotationMatrix = FloatArray(16)
        SensorManager.remapCoordinateSystem(rotationMatrix,
            SensorManager.AXIS_X,
            SensorManager.AXIS_Z,
            remappedRotationMatrix)

        val orientation = FloatArray(3)
        SensorManager.getOrientation(remappedRotationMatrix, orientation)

        for ( i in orientation.indices){
            orientation[i] = Math.toDegrees(orientation[i].toDouble()).toFloat()
        }


        ax = (orientation[0] - offsetax) % 180.0f
        ay = (orientation[1] - offsetay) % 180.0f
        az = (orientation[2] - offsetaz) % 180.0f


        binding.ax.text = "AX: ${ax.toInt()}"
        binding.ay.text = "AY: ${ay.toInt()}"
        binding.az.text = "AZ: ${az.toInt()}"
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** Обработчик нажатий для кнопок F1-F3 */
    override fun onTouch(view: View?, event: MotionEvent?): Boolean {
        when (view) {
            binding.f1Button -> {
                if (event != null) {
                    when (event.action){
                        MotionEvent.ACTION_DOWN -> {
                            f1 = true
                        }
                        MotionEvent.ACTION_UP -> {
                            view.performClick()
                            f1 = false
                        }
                    }
                }
            }

            binding.f2Button -> {
                if (event != null) {
                    when (event.action){
                        MotionEvent.ACTION_DOWN -> {
                            f2 = true
                        }
                        MotionEvent.ACTION_UP -> {
                            view.performClick()
                            f2 = false
                        }
                    }
                }
            }

            binding.f3Button -> {
                if (event != null) {
                    when (event.action){
                        MotionEvent.ACTION_DOWN -> {
                            f3 = true
                        }
                        MotionEvent.ACTION_UP -> {
                            view.performClick()
                            f3 = false
                        }
                    }
                }
            }
        }
        return false
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