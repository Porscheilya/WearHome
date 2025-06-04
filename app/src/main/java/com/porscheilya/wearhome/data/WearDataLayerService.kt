package com.porscheilya.wearhome.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.wearable.*

class WearDataLayerService : WearableListenerService() {

    companion object {
        private const val TAG = "WearDataLayerService"
        private const val TOKEN_PATH = "/yandex_token"
        private const val MESSAGE_PATH = "/request_auth"
        private const val PREFS_NAME = "YandexAuth"
        private const val TOKEN_KEY = "yandex_token"
    }    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        super.onDataChanged(dataEventBuffer)
        Log.d(TAG, "onDataChanged called with ${dataEventBuffer.count} events")

        for (event in dataEventBuffer) {
            Log.d(TAG, "DataEvent: type=${event.type}, uri=${event.dataItem.uri}")
            if (event.type == DataEvent.TYPE_CHANGED) {
                val item = event.dataItem
                if (item.uri.path == TOKEN_PATH) {
                    Log.d(TAG, "Received token data at path: ${item.uri.path}")
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    val token = dataMap.getString("token")
                    
                    if (token != null) {
                        Log.d(TAG, "Token extracted: ${token.take(10)}...")
                        saveTokenToPreferences(token)
                        notifyTokenReceived(token)
                    } else {
                        Log.w(TAG, "Token is null in received data")
                    }
                } else {
                    Log.d(TAG, "Received data for different path: ${item.uri.path}")
                }
            }
        }
    }    private fun saveTokenToPreferences(token: String) {
        Log.d(TAG, "Saving token to preferences: ${token.take(10)}...")
        val prefs: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val success = prefs.edit().putString(TOKEN_KEY, token).commit()
        Log.d(TAG, "Token save result: $success")
    }    private fun notifyTokenReceived(token: String) {
        Log.d(TAG, "Notifying token received via broadcast")
        // Отправляем broadcast для обновления UI
        val intent = android.content.Intent("com.porscheilya.wearhome.TOKEN_RECEIVED")
        intent.setPackage(packageName) // Make intent explicit for security
        intent.putExtra("token", token)
        sendBroadcast(intent)
        Log.d(TAG, "Broadcast sent with token: ${token.take(10)}...")
    }    fun requestAuthFromPhone() {
        Log.d(TAG, "Requesting auth from phone")
        val messageClient = Wearable.getMessageClient(this)
        
        // Отправляем сообщение на телефон для запроса авторизации
        messageClient.sendMessage(
            "", // nodeId будет определен автоматически для подключенного телефона
            MESSAGE_PATH,
            ByteArray(0)
        ).addOnSuccessListener {
            Log.d(TAG, "Auth request message sent successfully")
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Failed to send auth request message", exception)
        }
    }
}
