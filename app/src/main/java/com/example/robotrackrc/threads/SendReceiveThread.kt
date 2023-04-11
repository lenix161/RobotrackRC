package com.example.robotrackrc.threads

import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.experimental.and


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
        val buf = ByteArray(256)
        while (true){
            try {
                val size = inputStream.read(buf)
                val msg = String(buf, 0, size?:0)
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
            val arr = byteArrayOf(
                list[0].toByte(),
                list[1].toByte(),
                list[2].toByte(),
                list[3].toByte(),
                list[4].toByte(),
                list[5].toByte(),
                list[6].toByte(),
                list[7].toByte(),
                0x00,
                list[8].toByte(),
                list[9].toByte(),
                list[10].toByte())

            val arr2 = byteArrayOf(list[0].toByte(), list[1].toByte(), list[2].toByte(), list[3].toByte(), 0x04, 0x05, 0x00, 0x00, 0x00, 0x00, 0x0D, 0x0A)
            outputStream.write(arr)
            outputStream.flush()
            Log.d("MyLog", "Outputstream: ${list[0].toByte()}, ${list[1].toByte()}, ${list[2]}, ${list[3]}, ${list[4]}" +
                    ", ${list[5]}, ${list[6]}, ${list[7]}, ${list[8]}, ${list[9]}")
        } catch(e: IOException){
            Log.e("MyLog", "Ошибка записи в output stream")
        }
    }
}