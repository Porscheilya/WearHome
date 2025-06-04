package com.porscheilya.wearhome.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.*
import com.porscheilya.wearhome.mobile.databinding.ActivityMainBinding
import com.yandex.authsdk.YandexAuthException
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthOptions
import com.yandex.authsdk.YandexAuthSdk
import com.yandex.authsdk.YandexAuthToken
import com.yandex.authsdk.YandexAuthResult

class MainActivity : AppCompatActivity(), DataClient.OnDataChangedListener, MessageClient.OnMessageReceivedListener {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var yandexAuthSdk: YandexAuthSdk
    private lateinit var dataClient: DataClient
    private lateinit var messageClient: MessageClient
    private lateinit var authLauncher: androidx.activity.result.ActivityResultLauncher<YandexAuthLoginOptions>
    
    // BroadcastReceiver для получения запросов авторизации от DataLayerService
    private val authRequestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DataLayerService.ACTION_AUTH_REQUEST) {
                Log.d(TAG, "Auth request received via broadcast")
                runOnUiThread {
                    updateStatus("🔐 Запрос авторизации от часов")
                    Toast.makeText(this@MainActivity, "Часы запрашивают авторизацию", Toast.LENGTH_SHORT).show()
                    performYandexLogin()
                }
            }
        }
    }

    companion object {
        private const val TAG = "WearHomeMobile"
        private const val TOKEN_PATH = "/yandex_token"
        private const val MESSAGE_PATH = "/request_auth"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity created")
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Инициализация Yandex Auth SDK
        val yandexAuthOptions = YandexAuthOptions(this)
        yandexAuthSdk = YandexAuthSdk.create(yandexAuthOptions)

        // Инициализация ActivityResultLauncher
        authLauncher = registerForActivityResult(yandexAuthSdk.contract) { result ->
            handleAuthResult(result)
        }

        // Инициализация Wearable API
        dataClient = Wearable.getDataClient(this)
        messageClient = Wearable.getMessageClient(this)

        binding.loginButton.setOnClickListener {
            performYandexLogin()
        }

        binding.testConnectionButton.setOnClickListener {
            testConnection()
        }

        updateStatus("Готов к авторизации")
        
        // Проверяем подключенные устройства при запуске
        checkConnectedNodes()
    }    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - adding listeners")
        dataClient.addListener(this)
        messageClient.addListener(this)
        
        // Регистрируем BroadcastReceiver для получения запросов авторизации
        val filter = IntentFilter(DataLayerService.ACTION_AUTH_REQUEST)
        LocalBroadcastManager.getInstance(this).registerReceiver(authRequestReceiver, filter)
        
        checkConnectedNodes()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause - removing listeners")
        dataClient.removeListener(this)
        messageClient.removeListener(this)
        
        // Отменяем регистрацию BroadcastReceiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(authRequestReceiver)
    }

    private fun performYandexLogin() {
        updateStatus("Выполняется авторизация...")
        binding.loginButton.isEnabled = false

        val loginOptions = YandexAuthLoginOptions()
        authLauncher.launch(loginOptions)
    }

    private fun handleAuthResult(result: com.yandex.authsdk.YandexAuthResult) {
        when (result) {
            is com.yandex.authsdk.YandexAuthResult.Success -> {
                onLoginSuccess(result.token)
            }
            is com.yandex.authsdk.YandexAuthResult.Failure -> {
                onLoginError("Ошибка авторизации: ${result.exception.message}")
            }
            com.yandex.authsdk.YandexAuthResult.Cancelled -> {
                onLoginError("Авторизация отменена пользователем")
            }
        }
    }

    private fun onLoginSuccess(token: YandexAuthToken) {
        updateStatus(getString(R.string.login_success))
        sendTokenToWatch(token.value)
        
        Toast.makeText(this, getString(R.string.login_success), Toast.LENGTH_SHORT).show()
        binding.loginButton.isEnabled = true
    }

    private fun onLoginError(error: String) {
        updateStatus("Ошибка: $error")
        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        binding.loginButton.isEnabled = true
    }

    private fun sendTokenToWatch(token: String) {
        Log.d(TAG, "Attempting to send token to watch: ${token.take(10)}...")
        updateStatus(getString(R.string.sending_to_watch))

        val putDataMapReq = PutDataMapRequest.create(TOKEN_PATH)
        putDataMapReq.dataMap.putString("token", token)
        putDataMapReq.dataMap.putLong("timestamp", System.currentTimeMillis())

        val putDataReq = putDataMapReq.asPutDataRequest()
        putDataReq.setUrgent()

        Log.d(TAG, "Creating DataItem with path: $TOKEN_PATH")

        val task: Task<DataItem> = dataClient.putDataItem(putDataReq)
        task.addOnSuccessListener { dataItem ->
            Log.d(TAG, "Token sent successfully! DataItem: ${dataItem.uri}")
            updateStatus(getString(R.string.sent_to_watch))
        }
        task.addOnFailureListener { exception ->
            Log.e(TAG, "Failed to send token: ${exception.message}", exception)
            updateStatus("Ошибка отправки: ${exception.message}")
        }
    }

    private fun updateStatus(status: String) {
        binding.statusText.text = status
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged called with ${dataEvents.count} events")
        // Обработка изменений данных от часов (если потребуется)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Message received from ${messageEvent.sourceNodeId}, path: ${messageEvent.path}")
        // Если часы запрашивают авторизацию
        if (messageEvent.path == MESSAGE_PATH) {
            Log.d(TAG, "Authentication request from watch")
            runOnUiThread {
                updateStatus("Запрос авторизации от часов")
                performYandexLogin()
            }
        }
    }    private fun checkConnectedNodes() {
        Log.d(TAG, "Checking connected nodes...")
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            Log.d(TAG, "Connected nodes: ${nodes.size}")
            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected nodes found!")
                updateStatus("Часы не подключены")
            } else {
                Log.d(TAG, "Found ${nodes.size} connected nodes")
                updateStatus("Найдено устройств: ${nodes.size}")
                nodes.forEach { node ->
                    Log.d(TAG, "Node: ${node.displayName} (${node.id}) - nearby: ${node.isNearby}")
                }
            }
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Failed to get connected nodes: ${exception.message}", exception)
            updateStatus("Ошибка проверки подключения: ${exception.message}")
        }
    }
      private fun testConnection() {
        Log.d(TAG, "Testing connection to watch...")
        updateStatus("Тестирование связи...")
        
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected nodes found during test!")
                updateStatus("❌ Часы не подключены")
                Toast.makeText(this, "Убедитесь что часы подключены к телефону", Toast.LENGTH_LONG).show()
            } else {
                Log.d(TAG, "Found ${nodes.size} connected nodes during test")
                updateStatus("✅ Найдено устройств: ${nodes.size}")
                
                // Отправим тестовое сообщение
                val testData = PutDataMapRequest.create("/test_connection").run {
                    dataMap.putString("message", "test from mobile")
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                    asPutDataRequest()
                }
                
                testData.setUrgent()
                
                dataClient.putDataItem(testData).addOnSuccessListener { dataItem ->
                    Log.d(TAG, "Test message sent successfully: ${dataItem.uri}")
                    updateStatus("✅ Тестовое сообщение отправлено")
                    Toast.makeText(this, "Связь с часами работает!", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Failed to send test message", e)
                    updateStatus("❌ Ошибка отправки тестового сообщения")
                    Toast.makeText(this, "Ошибка: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
                
                nodes.forEach { node ->
                    Log.d(TAG, "Test - Node: ${node.displayName} (${node.id}) - nearby: ${node.isNearby}")
                }
            }
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Failed to test connection: ${exception.message}", exception)
            updateStatus("❌ Ошибка тестирования: ${exception.message}")
            Toast.makeText(this, "Ошибка: ${exception.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
