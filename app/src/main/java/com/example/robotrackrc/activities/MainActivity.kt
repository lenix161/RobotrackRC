package com.example.robotrackrc.activities

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.View.OnTouchListener
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.codertainment.dpadview.DPadView
import com.example.robotrackrc.R
import com.example.robotrackrc.bluetooth.BtConnector
import com.example.robotrackrc.bluetooth.ConnectThread
import com.example.robotrackrc.databinding.ActivityMainBinding


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

    // Значения гироскопа
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

    // Значения ползунков(seek bar)
    private var pot1 = 0
    private var pot2 = 0
    private var pot3 = 0

    // Флаг того, что показания гироскопа переключены на вид с камеры
    private var isCameraStarted = false
    private var isSeekBarEnable = false

    private var isSensorOk = false

    // Поток для сбора и отправки данных по bluetooth
    private lateinit var collectDataThread: Thread

    // Настройки приложения
    private lateinit var appSettings: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /** Инициализация Shared Preferences */
        appSettings = getSharedPreferences("Settings", MODE_PRIVATE)
        editor = appSettings.edit()

        /** Инициализация менеджера  датчиков гироскопа */
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null){
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            isSensorOk = true
        }


        /** Кнопка обнуления гироскопа */
        binding.resetGyroPositionButton.setOnClickListener {
            offsetax = ((ax + 360) % 360 + offsetax) % 360
            offsetay = ((ay + 360) % 360 + offsetay) % 360
            offsetaz = ((az + 360) % 360 + offsetaz) % 360
            Log.d("RobotrackRC", "ax: $offsetax, ay: $offsetay, az: $offsetaz")
        }

        /** Взаимодействие с левым seek bar */
        binding.leftSeekBar.setOnProgressChangeListener { progressValue ->
            pot1 = progressValue
            binding.leftSeekBarValue.text = "$pot1"
        }

        /** Взаимодействие со средним seek bar */
        binding.middleSeekBar.setOnProgressChangeListener { progressValue ->
            pot2 = progressValue
            binding.middleSeekBarValue.text = "$pot2"
        }

        /** Взаимодействие с правым seek bar */
        binding.rightSeekBar.setOnProgressChangeListener { progressValue ->
            pot3 = progressValue
            binding.rightSeekBarValue.text = "$pot3"
        }

        /** Включение/выключение seek bar(-ов) */
        binding.seekBarEnableButton.setOnClickListener {
            enableSeekBarListener()
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED){
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
                            .launch(Intent(this, BluetoothDevicesListActivity::class.java))
                        overridePendingTransition(0,0)
                    }
                } else {
                    // Если подключение уже установлено, появится диалоговое окно
                    //  для уточнения намерений пользователя
                    val builder = AlertDialog.Builder(this)
                    builder.setTitle("Отключиться?")
                        .setPositiveButton("Да") { dialog, id ->
                            btConnector.disconnect()
                        }
                        .setNeutralButton("Нет") { dialog, id ->
                            // User cancelled the dialog
                        }
                    // Create the AlertDialog object and return it
                    builder.create()
                    builder.show()

                }
            }
        }


        /** Обработчик нажатий на кнопку настроек */
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, AppSettingsActivity::class.java))
            overridePendingTransition(0,0)
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
        binding.leftJoystick.setOnMoveListener { angle, strength ->
            joystickListener(angle,strength, "left")
        }
        if (appSettings.contains("leftAutoRecenter")){
            val a = appSettings.getBoolean("leftAutoRecenter", true)
            binding.leftJoystick.isAutoReCenterButton = a
            Log.d("RobotrackRC", "left $a read")
        }

        /** Инициализация прослушивателя взаимодействий с правым джойстиком */
        binding.rightJoystick.setOnMoveListener { angle, strength ->
            joystickListener(angle,strength, "right")
        }
        if (appSettings.contains("rightAutoRecenter")){
            val a = appSettings.getBoolean("rightAutoRecenter", true)
            binding.rightJoystick.isAutoReCenterButton = a
            Log.d("RobotrackRC", "right $a read")
        }

        /** Запуск потока для отправки данных по bluetooth */
        collectDataThread = Thread(task, "BtSendThread")
        collectDataThread.start()
    }

    override fun onResume() {
        super.onResume()
        // Включение датчика гироскопа
        if (isSensorOk){
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        } else {
            Toast.makeText(this, "На вашем устройстве отсутствуют необходимые датчики для работы гироскопа",
                Toast.LENGTH_SHORT).show()
        }


        // Чтение настроек для девого джойстика
        if (appSettings.contains("leftAutoRecenter")){
            val flag = appSettings.getBoolean("leftAutoRecenter", true)
            if (flag) binding.leftJoystick.resetButtonPosition()
            binding.leftJoystick.isAutoReCenterButton = flag
        }

        // Чтение настроек для правого джойстика
        if (appSettings.contains("rightAutoRecenter")){
            val flag = appSettings.getBoolean("rightAutoRecenter", true)
            if (flag) binding.rightJoystick.resetButtonPosition()
            binding.rightJoystick.isAutoReCenterButton = flag
        }

        // Чтение настроек на автоматическое подключение к последнему устройству и подключение к нему
        if (appSettings.contains("autoConnectToLastDevice") && appSettings.contains("lastDeviceName")
            && appSettings.contains("lastDeviceMac") && !ConnectThread.isConnected){
            val flag = appSettings.getBoolean("autoConnectToLastDevice", false)
            val lastDeviceName = appSettings.getString("lastDeviceName", "")
            val lastDeviceMac = appSettings.getString("lastDeviceMac", "")
            if (flag){
                btConnector.connect(lastDeviceMac.toString())

                // Обновляем UI
                binding.connectedDeviceName.text = lastDeviceName
                binding.connectedDeviceName.setTextColor(resources.getColor(R.color.green))
                binding.bluetoothButton.setColorFilter(resources.getColor(R.color.blue))
                binding.connectionStatus.text = lastDeviceMac
            }
        }

        // Чтение настроек: не отключать подсветку экрана
        if (appSettings.contains("keepScreenOn")){
            if (appSettings.getBoolean("keepScreenOn", false)){
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

    }

    override fun onPause() {
        super.onPause()
        // Выключение датчика гироскопа
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Остановка потока собирающего данные для отправки по bluetooth
        collectDataThread.interrupt()
    }

    /*** Явный запрос прав на bluetoocth подключение */
    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                if (it.key == "android.permission.BLUETOOTH_CONNECT" && !it.value){
                    Toast.makeText(this, "Необходимо выдать разрешение для работы приложения",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }

    /** Обработка нажатий на кнопку включения/выключения seek bar(-ов) */
    private fun enableSeekBarListener(){
        if (!isSeekBarEnable){
            isSeekBarEnable = true

            // Перекрашиваем кнопку
            binding.seekBarEnableButton.setColorFilter(resources.getColor(R.color.orange))

            // Скрываем ненужные View
            binding.rightDpad.visibility = View.GONE
            binding.rightJoystick.visibility = View.GONE
            binding.switchRightControllerType.visibility = View.GONE
            binding.rightX.visibility = View.GONE
            binding.rightY.visibility = View.GONE

            // Переводим правый switch в выключенное состояние
            binding.switchRightControllerType.setOnCheckedChangeListener(null)
            binding.switchRightControllerType.isChecked = false
            binding.switchRightControllerType.setOnCheckedChangeListener { _, isChecked ->
                switchJoystickTypeListener(isChecked, "right")
            }

            // Делаем три seek bar(-а) видимыми
            binding.leftSeekBar.visibility = View.VISIBLE
            binding.middleSeekBar.visibility = View.VISIBLE
            binding.rightSeekBar.visibility = View.VISIBLE
            binding.leftSeekBarValue.visibility = View.VISIBLE
            binding.middleSeekBarValue.visibility = View.VISIBLE
            binding.rightSeekBarValue.visibility = View.VISIBLE
        } else {
            isSeekBarEnable = false

            // Перекрашиваем кнопку
            binding.seekBarEnableButton.setColorFilter(resources.getColor(R.color.black))

            // Скрываем три seek bar(-а)
            binding.leftSeekBar.visibility = View.INVISIBLE
            binding.middleSeekBar.visibility = View.INVISIBLE
            binding.rightSeekBar.visibility = View.INVISIBLE
            binding.leftSeekBarValue.visibility = View.INVISIBLE
            binding.middleSeekBarValue.visibility = View.INVISIBLE
            binding.rightSeekBarValue.visibility = View.INVISIBLE

            // Делаем видимыми ранее скрытые View
            binding.rightJoystick.visibility = View.VISIBLE
            binding.switchRightControllerType.visibility = View.VISIBLE
            binding.rightX.visibility = View.VISIBLE
            binding.rightY.visibility = View.VISIBLE
        }
    }

    /** Обработка нажатий на кнопку переключения гироскоп <--> камера */
    private fun switchGyroAndCamButtonListener(){
        if (!isCameraStarted){
            isCameraStarted = true
            enableCameraView()
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

    /** Подключение изображения с камеры */
    private fun enableCameraView(){
        // Чтение настроек и получение адреса камеры, если ранее уже подключались
        var cameraAddress = ""
        if (appSettings.contains("lastCameraAddress")){
            cameraAddress = appSettings.getString("lastCameraAddress", "").toString()
        }

        val editText = EditText(this)
        editText.textAlignment = View.TEXT_ALIGNMENT_CENTER
        editText.setText(cameraAddress)

        // Окно для ввода адреса
        val builder = AlertDialog.Builder(this)
            .setTitle("Введите адрес камеры")
            .setView(editText)

            .setPositiveButton("Подключиться") { dialog, id ->
                cameraAddress = editText.text.toString()
                editor.putString("lastCameraAddress", cameraAddress)
                editor.apply()

                val webView: WebView = binding.webView
                webView.webViewClient = WebViewClient()
                webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                if (cameraAddress.contains("/stream")){
                    webView.loadUrl(cameraAddress)
                } else {
                    webView.loadUrl("$cameraAddress/stream")
                }
                webView.settings.javaScriptEnabled = true
            }
            .setNeutralButton("Отмена") { dialog, id ->
                isCameraStarted = false
                binding.webView.visibility = View.GONE
                binding.coordinatesContainer.visibility = View.VISIBLE
                binding.switchGyroAndCamButton.setImageDrawable(resources.getDrawable(R.drawable.gyro_to_cam_btn))
            }
            .create()
        builder.show()
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
                    binding.rightX.text = "Z: $rightX"
                    binding.rightY.text = "W: $rightY"
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
                    binding.rightX.text = "Z: $rightX"
                    binding.rightY.text = "W: $rightY"
                } else if (action == MotionEvent.ACTION_UP){
                    rightX = 0
                    rightY = 0
                    // Обновляем UI
                    binding.rightX.text = "Z: $rightX"
                    binding.rightY.text = "W: $rightY"
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
                binding.rightX.text = "Z: $rightX"
                binding.rightY.text = "W: $rightY"
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

                // Запоминаем последнее подключенное устройство
                editor.putString("lastDeviceName", deviceName)
                editor.putString("lastDeviceMac", deviceMac)
                editor.apply()
                Log.d("RobotrackRC", "Устройство $deviceName $deviceMac записано")
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
               if (status == "Разрыв соединения"){
                   binding.connectedDeviceName.setTextColor(resources.getColor(R.color.red))
               }
           }

        }
    }

    /** Снятие показаний с гироскопа */
    override fun onSensorChanged(event: SensorEvent?) {
        val rotationMatrix = FloatArray(16)
        if (event != null) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix,event.values)
        } else {
            Toast.makeText(this, "На вашем отсутствуют нужные датчики для работы гироскопа",
            Toast.LENGTH_SHORT).show()
            sensorManager.unregisterListener(this)
            return
        }

        val remappedRotationMatrix = FloatArray(16)

        if (windowManager.getDefaultDisplay().rotation == Surface.ROTATION_90){
            SensorManager.remapCoordinateSystem(rotationMatrix,
                SensorManager.AXIS_Y,
                SensorManager.AXIS_MINUS_X,
                remappedRotationMatrix)
        } else if (windowManager.getDefaultDisplay().rotation == Surface.ROTATION_270){
            SensorManager.remapCoordinateSystem(rotationMatrix,
                SensorManager.AXIS_MINUS_Y,
                SensorManager.AXIS_X,
                remappedRotationMatrix)
        }


        val orientation = FloatArray(3)
        SensorManager.getOrientation(remappedRotationMatrix, orientation)

        for ( i in orientation.indices){
            orientation[i] = Math.toDegrees(orientation[i].toDouble()).toFloat()
        }


        ax = (orientation[0] + 720 - offsetax) % 360.0f
        if (ax > 180) ax -= 360
        ay = (orientation[1] + 720 - offsetay) % 360.0f
        if (ay > 180) ay -= 360
        az = (orientation[2] + 720 - offsetaz) % 360.0f
        if (az > 180) az -= 360

        binding.ax.text = "AX: ${(ax/1.79).toInt()}"
        binding.ay.text = "AY: ${(ay/1.79).toInt()}"
        binding.az.text = "AZ: ${(az/1.79).toInt()}"

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

    /**
     * Поток для отправоления данных:
     * 1) Координаты XY с двух джойстиков;
     * 2) Данные датчика гироскопа;
     * 3) Значения ползунков.
     *
     * Сначала данные отправляются раз в 1сек, затем скорость повышается до раза в 100 миллесекунд
     * */
    private val task = Runnable {
        var sendSpeed = 1000
        while (!Thread.interrupted()) {
            if (ConnectThread.isConnected) {
                var fSum = 0
                val data = mutableListOf<Int>()
                data.add(leftX)
                data.add(leftY)
                data.add(rightX)
                data.add(rightY)
                data.add((ax/1.79).toInt())
                data.add((ay/1.79).toInt())
                data.add((az/1.79).toInt())
                if (f1) fSum += 2
                if (f2) fSum += 4
                if (f3) fSum += 8
                if (f4) fSum += 16
                if (f5) fSum += 32
                if (f6) fSum += 64
                data.add(fSum)
                data.add(pot1)
                data.add(pot2)
                data.add(pot3)
                btConnector.sendMessage(data)

                try {
                    Thread.sleep(sendSpeed.toLong())
                } catch (e: InterruptedException) {
                    Log.e("RobotrackRC", e.stackTraceToString())
                }
                Log.d("RobotrackRC", "Sending speed = $sendSpeed")
                if (sendSpeed > 100) sendSpeed -= 100
            }
        }
    }

}