package com.porscheilya.wearhome.data

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.*
import kotlinx.coroutines.tasks.await

class CompanionAuthManager(private val context: Context) {
    
    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)
    
    companion object {
        private const val TAG = "CompanionAuthManager"
        private const val MESSAGE_PATH = "/request_auth"
    }

    suspend fun requestAuthFromPhone(): Boolean {
        Log.d(TAG, "Requesting auth from phone...")
        return try {
            Log.d(TAG, "Getting connected nodes...")
            val nodes = nodeClient.connectedNodes.await()
            Log.d(TAG, "Found ${nodes.size} connected nodes")
            
            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected nodes found!")
                false // Нет подключенных устройств
            } else {
                // Отправляем запрос на все подключенные устройства (обычно это телефон)
                for (node in nodes) {
                    Log.d(TAG, "Sending auth request to node: ${node.displayName} (${node.id})")
                    messageClient.sendMessage(
                        node.id,
                        MESSAGE_PATH,
                        ByteArray(0)
                    ).await()
                    Log.d(TAG, "Auth request sent successfully to ${node.displayName}")
                }
                Log.d(TAG, "Auth requests sent to all ${nodes.size} nodes")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request auth from phone", e)
            e.printStackTrace()
            false
        }
    }
}
