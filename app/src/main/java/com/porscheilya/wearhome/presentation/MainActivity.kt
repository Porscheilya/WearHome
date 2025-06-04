/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.porscheilya.wearhome.presentation

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.porscheilya.wearhome.R
import com.porscheilya.wearhome.presentation.theme.WearHomeTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.HorizontalPageIndicator
import androidx.wear.compose.material.PageIndicatorState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthOptions
import com.yandex.authsdk.YandexAuthResult
import com.yandex.authsdk.YandexAuthSdk
import androidx.compose.runtime.*
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import com.porscheilya.wearhome.data.TokenManager
import com.porscheilya.wearhome.data.CompanionAuthManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "WearHomeWatch"
        private const val PREFS_NAME = "wearhome_prefs"
        private const val KEY_IS_AUTHENTICATED = "is_authenticated"
        private const val KEY_USER_TOKEN = "user_token"
    }
    
    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val yandexAuthSdk by lazy { YandexAuthSdk.create(YandexAuthOptions(this)) }
    private lateinit var authLauncher: androidx.activity.result.ActivityResultLauncher<YandexAuthLoginOptions>
    
    private lateinit var tokenManager: TokenManager
    private lateinit var companionAuthManager: CompanionAuthManager
    
    private var isAuthenticated = mutableStateOf(false)
    private var userToken = mutableStateOf<String?>(null)
    private var authMessage = mutableStateOf<String?>(null)
    private var isLoading = mutableStateOf(false)
    
    private val tokenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Broadcast received: ${intent?.action}")
            if (intent?.action == "com.porscheilya.wearhome.TOKEN_RECEIVED") {
                val token = intent.getStringExtra("token")
                Log.d(TAG, "Token received via broadcast: ${token?.take(10)}...")
                if (token != null) {
                    handleTokenReceived(token)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "MainActivity created")

        setTheme(android.R.style.Theme_DeviceDefault)
        
        // Инициализируем менеджеры
        tokenManager = TokenManager(this)
        companionAuthManager = CompanionAuthManager(this)
        
        // Регистрируем authLauncher до setContent
        authLauncher = registerForActivityResult(yandexAuthSdk.contract) { result ->
            handleAuthResult(result)
        }
        
        // Регистрируем BroadcastReceiver для получения токенов от мобильного приложения
        val filter = IntentFilter("com.porscheilya.wearhome.TOKEN_RECEIVED")
        ContextCompat.registerReceiver(this, tokenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        
        // Восстанавливаем состояние авторизации из SharedPreferences
        restoreAuthState()

        setContent {
            WearApp(
                onLoginClick = { startCompanionAuth() },
                onLogoutClick = { logout() },
                isAuthenticated = isAuthenticated.value,
                userToken = userToken.value,
                authMessage = authMessage.value,
                isLoading = isLoading.value,
                onClearMessage = { authMessage.value = null }
            )
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(tokenReceiver)
    }
    
    private fun startCompanionAuth() {
        Log.d(TAG, "Starting companion auth")
        isLoading.value = true
        authMessage.value = "Запрос авторизации через телефон..."
        
        lifecycleScope.launch {
            val success = companionAuthManager.requestAuthFromPhone()
            Log.d(TAG, "Companion auth request result: $success")
            if (!success) {
                isLoading.value = false
                authMessage.value = "Телефон не подключен. Попробуйте локальную авторизацию."
                // Fallback к локальной авторизации
                startYandexAuth()
            }
        }
    }
    
    private fun startYandexAuth() {
        isLoading.value = true
        authMessage.value = null
        val loginOptions = YandexAuthLoginOptions()
        authLauncher.launch(loginOptions)
    }
    

    
    private fun handleAuthResult(result: YandexAuthResult) {
        isLoading.value = false
        when (result) {
            is YandexAuthResult.Success -> {
                // Успешная авторизация
                val token = result.token
                isAuthenticated.value = true
                userToken.value = token.value
                authMessage.value = "Успешная авторизация!"
                
                // Сохраняем состояние авторизации
                saveAuthState(true, token.value)
                
                // Можно также сохранить JWT токен
                try {
                    val jwtToken = yandexAuthSdk.getJwt(token)
                    // TODO: Использовать JWT токен при необходимости
                } catch (e: Exception) {
                    // Обработка ошибок получения JWT
                    authMessage.value = "Авторизация прошла успешно, но возникла ошибка с JWT токеном"
                }
            }
            is YandexAuthResult.Failure -> {
                // Ошибка авторизации
                val exception = result.exception
                isAuthenticated.value = false
                userToken.value = null
                authMessage.value = "Ошибка авторизации: ${exception.message ?: "Неизвестная ошибка"}"
            }
            YandexAuthResult.Cancelled -> {
                // Пользователь отменил авторизацию
                isAuthenticated.value = false
                userToken.value = null
                authMessage.value = "Авторизация отменена пользователем"
            }
        }
    }
    
    private fun handleTokenReceived(token: String) {
        Log.d(TAG, "handleTokenReceived called with token: ${token.take(10)}...")
        isLoading.value = false
        isAuthenticated.value = true
        userToken.value = token
        authMessage.value = "Авторизация через телефон успешна!"
        
        // Сохраняем токен через TokenManager
        tokenManager.saveToken(token)
        
        // Сохраняем состояние авторизации
        saveAuthState(true, token)
        Log.d(TAG, "Token handling completed successfully")
    }
    
    private fun logout() {
        // Очищаем состояние авторизации
        isAuthenticated.value = false
        userToken.value = null
        authMessage.value = "Вы успешно вышли из аккаунта"
        
        // Очищаем токен через TokenManager
        tokenManager.clearToken()
        
        // Очищаем сохраненные данные в SharedPreferences
        saveAuthState(false, null)
    }
    
    private fun saveAuthState(isAuth: Boolean, token: String?) {
        prefs.edit()
            .putBoolean(KEY_IS_AUTHENTICATED, isAuth)
            .putString(KEY_USER_TOKEN, token)
            .apply()
    }
    
    private fun restoreAuthState() {
        val savedIsAuth = prefs.getBoolean(KEY_IS_AUTHENTICATED, false)
        val savedToken = prefs.getString(KEY_USER_TOKEN, null)
        
        isAuthenticated.value = savedIsAuth
        userToken.value = savedToken
        
        if (savedIsAuth && savedToken != null) {
            authMessage.value = "Добро пожаловать! Вы уже авторизованы"
        }
    }
}

@Composable
fun WearApp(
    onLoginClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    isAuthenticated: Boolean = false,
    userToken: String? = null,
    authMessage: String? = null,
    isLoading: Boolean = false,
    onClearMessage: () -> Unit = {}
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    
    WearHomeTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> MainScreen()
                    1 -> LoginScreen(
                        onLoginClick = onLoginClick,
                        onLogoutClick = onLogoutClick,
                        isAuthenticated = isAuthenticated,
                        userToken = userToken,
                        authMessage = authMessage,
                        isLoading = isLoading,
                        onClearMessage = onClearMessage
                    )
                }
            }
            
            // Индикатор страниц внизу экрана
            HorizontalPageIndicator(
                pageIndicatorState = object : PageIndicatorState {
                    override val pageOffset: Float
                        get() = pagerState.currentPageOffsetFraction
                    override val selectedPage: Int
                        get() = pagerState.currentPage
                    override val pageCount: Int
                        get() = 2
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
fun MainScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        TimeText()
        MainList()
    }
}

@Composable
fun LoginScreen(
    onLoginClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    isAuthenticated: Boolean = false,
    userToken: String? = null,
    authMessage: String? = null,
    isLoading: Boolean = false,
    onClearMessage: () -> Unit = {}
) {
    // Анимированный альфа для всего экрана
    val screenAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0.7f else 1.0f,
        animationSpec = tween(300),
        label = "screenAlpha"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .alpha(screenAlpha),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            // Отображение статуса загрузки с анимацией
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(animationSpec = tween(300)) + slideInVertically(),
                exit = fadeOut(animationSpec = tween(300)) + slideOutVertically()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Пульсирующая анимация для загрузки
                    val infiniteTransition = rememberInfiniteTransition(label = "loading")
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 0.8f,
                        targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = EaseInOutCubic),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "loadingScale"
                    )
                    
                    Text(
                        text = "⏳",
                        fontSize = 24.sp,
                        modifier = Modifier
                            .scale(scale)
                            .padding(8.dp)
                    )
                    
                    Text(
                        text = "Авторизация...",
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
            
            // Отображение сообщений с анимацией
            AnimatedVisibility(
                visible = authMessage != null,
                enter = slideInVertically(
                    initialOffsetY = { -it }
                ) + fadeIn(animationSpec = tween(400)),
                exit = slideOutVertically(
                    targetOffsetY = { -it }
                ) + fadeOut(animationSpec = tween(300))
            ) {
                authMessage?.let { message ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Анимированная иконка в зависимости от результата
                        val icon = if (isAuthenticated) "✅" else "❌"
                        val iconScale by animateFloatAsState(
                            targetValue = 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "iconScale"
                        )
                        
                        Text(
                            text = icon,
                            fontSize = 24.sp,
                            modifier = Modifier
                                .scale(iconScale)
                                .padding(8.dp)
                        )
                        
                        Text(
                            text = message,
                            style = MaterialTheme.typography.body2,
                            color = if (isAuthenticated) Color.Green else Color.Red,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        // Анимированная кнопка OK
                        val buttonScale by animateFloatAsState(
                            targetValue = 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "buttonScale"
                        )
                        
                        Button(
                            onClick = onClearMessage,
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier
                                .padding(bottom = 16.dp)
                                .scale(buttonScale)
                        ) {
                            Text(
                                text = "OK",
                                color = MaterialTheme.colors.onSurface,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
            
            if (isAuthenticated && userToken != null) {
                // Показываем информацию о том, что пользователь авторизован
                Text(
                    text = "✓ Вы вошли в аккаунт",
                    style = MaterialTheme.typography.body1,
                    color = Color.Green,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = "Токен: ${userToken.take(15)}...",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                // Кнопка выхода с анимацией
                val logoutButtonScale by animateFloatAsState(
                    targetValue = if (isLoading) 0.9f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "logoutButtonScale"
                )
                
                Button(
                    onClick = onLogoutClick,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color.Red,
                        contentColor = Color.White
                    ),
                    enabled = !isLoading,
                    modifier = Modifier.scale(logoutButtonScale)
                ) {
                    Text(
                        text = "Выйти из аккаунта",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            } else {
                // Показываем кнопку входа
                if (authMessage == null) {
                    Text(
                        text = "Авторизация",
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.onBackground,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = "Войдите через Яндекс для доступа к функциям приложения",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }
                
                // Анимированная кнопка входа
                val loginButtonScale by animateFloatAsState(
                    targetValue = if (isLoading) 0.9f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "loginButtonScale"
                )
                
                val loginButtonColor by animateColorAsState(
                    targetValue = if (isLoading) Color(0xFFE6C547) else Color(0xFFFFDB4C),
                    animationSpec = tween(300),
                    label = "loginButtonColor"
                )
                
                Button(
                    onClick = onLoginClick,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = loginButtonColor,
                        contentColor = Color.Black
                    ),
                    enabled = !isLoading,
                    modifier = Modifier.scale(loginButtonScale)
                ) {
                    Text(
                        text = if (isLoading) "Загрузка..." else "Войти",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Главный список элементов с плавными анимациями. Каждый элемент меняет цвет при нажатии.
 */
@Composable
fun MainList() {
    val items = listOf("🏠 Освещение", "🌡️ Температура", "🔒 Безопасность", "📺 Медиа", "⚙️ Настройки")
    val selectedIndices = remember { mutableStateListOf<Int>() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 32.dp)
    ) {
        itemsIndexed(items) { index, item ->
            val isSelected = selectedIndices.contains(index)
            
            // Анимированный цвет фона
            val animatedBackgroundColor by animateColorAsState(
                targetValue = if (isSelected) {
                    colorResource(id = R.color.item_selected_purple)
                } else {
                    colorResource(id = R.color.item_default_gray)
                },
                animationSpec = tween(300, easing = EaseInOutCubic),
                label = "backgroundColor"
            )
            
            // Анимированный масштаб
            val animatedScale by animateFloatAsState(
                targetValue = if (isSelected) 1.05f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "scale"
            )
            
            // Анимированная альфа для текста
            val animatedAlpha by animateFloatAsState(
                targetValue = if (isSelected) 1.0f else 0.8f,
                animationSpec = tween(200),
                label = "alpha"
            )
            
            val interactionSource = remember { MutableInteractionSource() }
            
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300 + index * 50)
                ) + fadeIn(animationSpec = tween(300 + index * 50))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp, horizontal = 16.dp)
                        .scale(animatedScale)
                        .background(
                            color = animatedBackgroundColor,
                            shape = RoundedCornerShape(28.dp)
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            if (selectedIndices.contains(index)) {
                                selectedIndices.remove(index)
                            } else {
                                selectedIndices.add(index)
                            }
                        }
                ) {
                    Text(
                        text = item,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .alpha(animatedAlpha),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onBackground,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = if (isSelected) 16.sp else 14.sp
                    )
                }
            }
        }
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    MainScreen()
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun LoginPreview() {
    LoginScreen(onLoginClick = {})
}