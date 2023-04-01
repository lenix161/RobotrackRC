package com.example.robotrackrc.threads

import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


class SendReceiveThread(socket: BluetoothSocket, private val listener: ConnectThread.Listener): Thread() {
    private lateinit var inputStream: InputStream
    private lateinit var outputStream: OutputStream

    init {
        try {
            inputStream = socket.inputStream
        } catch (e: IOException){

        }

        try {
            outputStream = socket.outputStream
        } catch (e: IOException){

        }
    }

    override fun run() {
        val buf = ByteArray(4)
        while (true){
            try {
                val size = inputStream.read(buf)
                val msg = String(buf, 0, size)
                Log.d("MyLog", "Полученное сообщение: $msg")
            } catch (e: IOException){
                listener.onConnect("Отключено")
                Log.d("MyLog", "Отключено")
                break
            }
        }
    }

    /** Запись сообщения в output stream ввиде набора байтиов */
    fun sendMessage(list: List<Int>){
        try {
            val arr = byteArrayOf(list[0].toByte(), list[1].toByte(), list[2].toByte(), list[3].toByte(), 0x04, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
            outputStream.write(arr)
            Log.d("MyLog", "Outputstream: ${list[0]}, ${list[1]}, ${list[2]}, ${list[3]}, arr size ${arr.size}")
        } catch(e: IOException){
            Log.e("MyLog", "Ошибка записи в output stream")
        }
    }
}