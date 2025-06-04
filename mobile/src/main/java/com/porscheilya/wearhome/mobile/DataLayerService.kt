package com.porscheilya.wearhome.mobile

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.*

class DataLayerService : WearableListenerService() {

    companion object {
        private const val TAG = "DataLayerService"
        const val ACTION_AUTH_REQUEST = "com.porscheilya.wearhome.AUTH_REQUEST"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        
        Log.d(TAG, "Message received: path=${messageEvent.path}, sourceNodeId=${messageEvent.sourceNodeId}")
        
        when (messageEvent.path) {
            "/request_auth" -> {
                Log.d(TAG, "Auth request received from watch")
                
                // Отправляем broadcast для уведомления MainActivity
                val intent = Intent(ACTION_AUTH_REQUEST)
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                
                Log.d(TAG, "Auth request broadcast sent")
            }
            else -> {
                Log.d(TAG, "Unknown message path: ${messageEvent.path}")
            }
        }
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        super.onDataChanged(dataEventBuffer)
        
        Log.d(TAG, "Data changed event received")
        
        for (event in dataEventBuffer) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val item = event.dataItem
                Log.d(TAG, "Data item changed: ${item.uri.path}")
                
                if (item.uri.path?.compareTo("/yandex_token") == 0) {
                    Log.d(TAG, "Yandex token was updated")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DataLayerService created")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "DataLayerService destroyed")
    }
}
