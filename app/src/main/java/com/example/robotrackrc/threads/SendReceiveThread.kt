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
        Log.d("MyLog", "SendReciveThread started")
        val buf = ByteArray(4)
        while (true){
            try {
                val size = inputStream.read(buf)
                val msg = String(buf, 0, size)
                Log.d("MyLog", "Полученное сообщение: $msg")
            } catch (e: IOException){
                listener.onConnect("Cоединениe разорвано")
                Log.d("MyLog", "Отключено SendReciveThread")
                break
            }
        }
    }

    /** Запись сообщения в output stream ввиде набора байтиов */
    fun sendMessage(list: List<Int>){
        try {
            val arr = byteArrayOf(list[0].toByte(), list[1].toByte(), list[2].toByte(),
                list[3].toByte(), (0x04).toByte(), (0x05).toByte(), (0x00).toByte(), (0x00).toByte(),
                (0x00).toByte(), (0x00).toByte(), (0x00).toByte(), (0x0A).toByte(), (0x0D).toByte())
            outputStream.write(arr)
            outputStream.flush()
            Log.d("MyLog", "Outputstream: ${list[0].toByte()}, ${list[1].toByte()}, ${list[2]}, ${list[3]}, ${list[4]}" +
                    ", ${list[5]}, ${list[6]}, ${list[7]}, ${list[8]}, ${list[9]}")
        } catch(e: IOException){
            Log.e("MyLog", "Ошибка записи в output stream")
        }
    }
}