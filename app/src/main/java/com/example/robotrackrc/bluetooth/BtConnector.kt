package com.example.robotrackrc.bluetooth

import android.bluetooth.BluetoothAdapter

class BtConnector(private val adapter: BluetoothAdapter, private val listener: ConnectThread.Listener) {
    lateinit var connectThread: ConnectThread

    /** Подключение к bluetooth устройству на отдельном потоке Connect Thread */
    fun connect(mac: String){
        if (adapter.isEnabled && mac.isNotBlank()){
            val device = adapter.getRemoteDevice(mac)
            connectThread = ConnectThread(device, listener)
            connectThread.start()
        }
    }

    /** Отправка сообщения на bluetooth устройство */
    fun sendMessage(list: List<Int>){
        if (ConnectThread.isConnected){
            connectThread.sendReceiveThread.sendMessage(list)
        }

    }

    fun disconnect(){
        connectThread.closeSocket()
        connectThread.interrupt()
    }
}