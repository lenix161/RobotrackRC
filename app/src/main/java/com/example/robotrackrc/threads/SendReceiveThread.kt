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
        val buf = ByteArray(1)
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
    fun sedMessage(list: List<Int>){

        try {
            outputStream.write(byteArrayOf(list[0].toByte(), 0x01, 0x02, 0x03, 0x04, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
            Log.d("MyLog", "Outputstream: ${list[0]}, ${list[1]}, ${list[2]}, ${list[3]}")
        } catch(e: IOException){
            Log.e("MyLog", "Ошибка записи в output stream")
        }
    }
}