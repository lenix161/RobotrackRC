package com.example.robotrackrc.activities

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.robotrackrc.databinding.ActivityAppSettingsBinding

class AppSettingsActivity: AppCompatActivity() {
    private lateinit var binding: ActivityAppSettingsBinding
    private lateinit var appSettings: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /** Инициализация Shared Preferences */
        appSettings = getSharedPreferences("Settings", MODE_PRIVATE)
        editor = appSettings.edit()


        /** Закрыть настройки */
        binding.closeButton.setOnClickListener {
            finish()
            overridePendingTransition(0,0)
        }

        /** Возвращение левого джойстика в центр */
        binding.leftAutoRecenterCheckbox.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("leftAutoRecenter", isChecked)
            editor.apply()
        }

        /** Возвращение правого джойстика в центр */
        binding.rightAutoRecenterCheckbox.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("rightAutoRecenter", isChecked)
            editor.apply()
        }

        /** Автоматическое подключение к последнему устройсиву */
        binding.autoConnectCheckbox.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("autoConnectToLastDevice", isChecked)
            editor.apply()
        }

        /** Не выключать подсветку экрана */
        binding.keepScreenOnCheckbox.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("keepScreenOn", isChecked)
            editor.apply()
        }
    }

    override fun onResume() {
        super.onResume()

        // Чтение настроек для корректного отображения чекбоксов
        if (appSettings.contains("leftAutoRecenter")){
            binding.leftAutoRecenterCheckbox.isChecked =
                appSettings.getBoolean("leftAutoRecenter", true)
        } else {
            binding.leftAutoRecenterCheckbox.isChecked = true
        }

        if (appSettings.contains("rightAutoRecenter")){
            binding.rightAutoRecenterCheckbox.isChecked =
                appSettings.getBoolean("rightAutoRecenter", true)
        } else {
            binding.rightAutoRecenterCheckbox.isChecked = true
        }

        if (appSettings.contains("autoConnectToLastDevice")){
            binding.autoConnectCheckbox.isChecked =
                appSettings.getBoolean("autoConnectToLastDevice", false)
        } else {
            binding.autoConnectCheckbox.isChecked = false
        }

        if (appSettings.contains("keepScreenOn")){
            binding.keepScreenOnCheckbox.isChecked =
                appSettings.getBoolean("keepScreenOn", false)
        } else {
            binding.keepScreenOnCheckbox.isChecked = false
        }
    }
}