package com.example.robotrackrc.threads

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.util.*

class ConnectThread(device: BluetoothDevice, private val listener: Listener): Thread() {
    val uuid = "00001101-0000-1000-8000-00805F9B34FB"
    lateinit var socket: BluetoothSocket
    lateinit var sendReceiveThread: SendReceiveThread

    init {
        try {
            socket = device.createRfcommSocketToServiceRecord(UUID.fromString(uuid))
        } catch (e: IOException){

        } catch (e: SecurityException){

        }

    }

    override fun run() {
        try {
            listener.onConnect("Подключение...")
            Log.d("MyLog", "Подключение..")
            socket.connect()
            listener.onConnect("Подключено")
            Log.d("MyLog", "Подключено")

            sendReceiveThread = SendReceiveThread(socket, listener)
            sendReceiveThread.start()
        } catch (e: SecurityException){
            closeSocket()
            Log.e("MyLog", "Нет прав для подключения по bluetooth")
        } catch (e: IOException){
            closeSocket()
            Log.e("MyLog", "Невозможно подключиться к устройству")
        }
    }


    fun closeSocket(){
        try {
            socket.close()
            listener.onConnect("Отключено")
            Log.d("MyLog", "Отключено")
        } catch (e: IOException){
            Log.e("MyLog", "Невозможно закрыть сокет")
        }
    }

    interface Listener{
        fun onConnect(status: String)
    }
}