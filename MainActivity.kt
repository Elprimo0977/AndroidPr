package com.example.myapplication

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

// --- МОДЕЛИ ДАННЫХ ---
// Класс Product описывает структуру одного товара.
// Каждый товар имеет уникальный ID (создается автоматически по времени), название, описание и список картинок (Uri).
data class Product(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val description: String,
    val imageUris: List<Uri>
)

// --- ЦВЕТА ПРИЛОЖЕНИЯ ---
// Вынесены отдельно, чтобы можно было легко поменять цветовую схему во всем приложении, изменив значение только здесь.
val AppBlue = Color(0xFF1E56E3)    // Основной синий фон
val AppOrange = Color(0xFFFF9800)  // Оранжевый для акцентов (сердечки, кнопки админа)
val AppLightGray = Color(0xFFE0E0E0) // Светло-серый для заглушек

// --- ГЛАВНАЯ ТОЧКА ВХОДА ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // Вызов главного навигационного контроллера
                AppNavigation()
            }
        }
    }
}

// --- НАВИГАЦИЯ (УПРАВЛЕНИЕ ЭКРАНАМИ) ---
@Composable
fun AppNavigation() {
    // --- ГЛОБАЛЬНЫЕ СОСТОЯНИЯ (ДАННЫЕ ПРИЛОЖЕНИЯ) ---
    // isLoggedIn: показывает, вошел ли пользователь в аккаунт (по умолчанию - нет)
    var isLoggedIn by remember { mutableStateOf(false) }
    // isAdmin: показывает, включен ли режим администратора (по умолчанию - выключен)
    var isAdmin by remember { mutableStateOf(false) }

    // Списки данных, которые хранятся в памяти устройства, пока приложение запущено.
    // mutableStateListOf автоматически обновляет интерфейс, когда в список добавляются новые элементы.
    val products = remember { mutableStateListOf<Product>() } // Все товары магазина
    val cartItems = remember { mutableStateListOf<Product>() } // Товары, которые пользователь добавил в корзину
    val favorites = remember { mutableStateListOf<Product>() } // Избранные товары (пока не используется, но задел на будущее)

    // Для всплывающих сообщений внизу экрана (Snackbar)
    val snackbarHostState = remember { SnackbarHostState() }
    // Корутинный scope нужен для вызова функции showSnackbar (она должна запускаться в фоне)
    val scope = rememberCoroutineScope()

    // Переменная, которая хранит название текущего открытого экрана.
    // Именно она определяет, что сейчас видит пользователь: главную, товары, корзину или профиль.
    var currentScreen by remember { mutableStateOf("main") }

    // Scaffold - это базовый каркас экрана. Он содержит:
    // 1. snackbarHost - место для всплывающих сообщений
    // 2. bottomBar - нижнюю панель навигации
    // 3. content (в фигурных скобках) - сам контент экрана
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // --- НИЖНЯЯ ПАНЕЛЬ НАВИГАЦИИ ---
            NavigationBar(containerColor = Color.White) {
                // Кнопка "Главная"
                NavigationBarItem(
                    selected = currentScreen == "main", // Если текущий экран "main", кнопка подсвечена
                    onClick = { currentScreen = "main" }, // При нажатии меняем экран на "main"
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Главная") }
                )
                // Кнопка "Товары"
                NavigationBarItem(
                    selected = currentScreen == "products",
                    onClick = { currentScreen = "products" },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text("Товары") }
                )
                // Кнопка "Корзина" со счетчиком (Badge)
                NavigationBarItem(
                    selected = currentScreen == "cart",
                    onClick = { currentScreen = "cart" },
                    icon = {
                        // BadgedBox показывает красный кружок с количеством товаров над иконкой корзины
                        BadgedBox(badge = {
                            if (cartItems.isNotEmpty()) Badge { Text(cartItems.size.toString()) }
                        }) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null)
                        }
                    },
                    label = { Text("Корзина") }
                )
                // Кнопка "Профиль"
                NavigationBarItem(
                    selected = currentScreen == "profile",
                    onClick = { currentScreen = "profile" },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("Профиль") }
                )
            }
        }
    ) { paddingValues ->
        // Этот блок отвечает за переключение экранов в зависимости от значения currentScreen
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentScreen) {
                // Если currentScreen == "main", показываем MainScreen
                "main" -> MainScreen(
                    isAdmin = isAdmin,
                    onNavigateToAddProduct = { currentScreen = "add_product" } // Переход на экран добавления товара
                )
                // Если currentScreen == "products", показываем ProductListScreen
                "products" -> ProductListScreen(
                    products = products, // Передаем список товаров для отображения
                    onAddToCart = { product ->
                        cartItems.add(product) // Добавляем товар в корзину
                        // Показываем всплывающее сообщение о добавлении
                        scope.launch { snackbarHostState.showSnackbar("${product.name} добавлен в корзину") }
                    }
                )
                // Если currentScreen == "cart", показываем CartScreen
                "cart" -> CartScreen(
                    cartItems = cartItems, // Передаем список товаров в корзине
                    onClearCart = { cartItems.clear() } // Очистка корзины
                )
                // Если currentScreen == "profile", показываем ProfileScreen
                "profile" -> ProfileScreen(
                    isLoggedIn = isLoggedIn,
                    isAdmin = isAdmin,
                    onLogin = { currentScreen = "login" }, // Переход на экран входа
                    onLogout = { isLoggedIn = false; isAdmin = false }, // Выход из аккаунта
                    onAdminToggle = { isAdmin = it } // Включение/выключение режима админа
                )
                // Если currentScreen == "add_product", показываем AddProductScreen
                "add_product" -> AddProductScreen(
                    onBack = { currentScreen = "main" }, // Возврат на главную
                    onSave = { newProduct ->
                        products.add(newProduct) // Добавляем новый товар в общий список
                        currentScreen = "products" // После добавления переходим в каталог товаров
                    }
                )
                // Если currentScreen == "login", показываем LoginScreen
                "login" -> LoginScreen(onLoginSuccess = {
                    isLoggedIn = true // Устанавливаем статус "вошел"
                    currentScreen = "profile" // Возвращаем в профиль
                })
            }
        }
    }
}

// --- ГЛАВНЫЙ ЭКРАН ---
@Composable
fun MainScreen(isAdmin: Boolean, onNavigateToAddProduct: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(AppBlue).padding(16.dp)
    ) {
        Text("Добро пожаловать!", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        // Показываем кнопку добавления товара только если пользователь - админ
        if (isAdmin) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onNavigateToAddProduct() },
                colors = CardDefaults.cardColors(containerColor = AppOrange),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "➕ Добавить новый товар (Админ)",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        } else {
            // Если не админ, показываем подсказку
            Text("Вы вошли как Гость. Чтобы добавить товары, включите режим администратора в профиле.", color = Color.White)
        }
    }
}

// --- СПИСОК ТОВАРОВ ---
@Composable
fun ProductListScreen(products: List<Product>, onAddToCart: (Product) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(AppBlue).padding(16.dp)
    ) {
        Text("Каталог товаров", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // Если товаров нет, показываем подсказку
        if (products.isEmpty()) {
            Text("Здесь пока ничего нет. (Зайдите в профиль -> включите админа -> добавьте товар)", color = Color.LightGray)
        } else {
            // LazyColumn создает прокручиваемый список. Внутри перебираем все товары.
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(products) { product ->
                    ProductItemCard(product, onAddToCart)
                }
            }
        }
    }
}

// Карточка одного товара (с каруселью картинок)
@Composable
fun ProductItemCard(product: Product, onAddToCart: (Product) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column {
            // Карусель картинок (если они есть)
            if (product.imageUris.isNotEmpty()) {
                // rememberPagerState хранит текущую страницу карусели
                val pagerState = rememberPagerState(pageCount = { product.imageUris.size })
                Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    // HorizontalPager позволяет свайпать картинки влево-вправо
                    HorizontalPager(state = pagerState) { page ->
                        AsyncImage(
                            model = product.imageUris[page],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    // Точки-индикаторы внизу картинки (показывают, какая картинка открыта)
                    if (product.imageUris.size > 1) {
                        Row(
                            Modifier.align(Alignment.BottomCenter).padding(8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(product.imageUris.size) { iteration ->
                                // Текущая страница - синяя точка, остальные - серые
                                val color = if (pagerState.currentPage == iteration) AppBlue else Color.LightGray
                                Box(
                                    modifier = Modifier.padding(2.dp).size(8.dp)
                                        .background(color, RoundedCornerShape(50))
                                )
                            }
                        }
                    }
                }
            }

            // Информация о товаре и кнопка
            Column(modifier = Modifier.padding(16.dp)) {
                Text(product.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(product.description, fontSize = 14.sp, color = Color.DarkGray)
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onAddToCart(product) }, // При нажатии вызываем функцию добавления в корзину
                    colors = ButtonDefaults.buttonColors(containerColor = AppBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("В корзину")
                }
            }
        }
    }
}

// --- КОРЗИНА ---
@Composable
fun CartScreen(cartItems: List<Product>, onClearCart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(AppBlue).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Корзина", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            // Если корзина не пуста, показываем кнопку "Очистить"
            if (cartItems.isNotEmpty()) {
                TextButton(onClick = onClearCart) {
                    Text("Очистить", color = Color.Red)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (cartItems.isEmpty()) {
            Text("Корзина пуста", color = Color.White, fontSize = 18.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cartItems) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("1 шт.", color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

// --- ПРОФИЛЬ (Вход/Админ) ---
@Composable
fun ProfileScreen(
    isLoggedIn: Boolean,
    isAdmin: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onAdminToggle: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(AppBlue).padding(16.dp)
    ) {
        Text("Профиль", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        if (!isLoggedIn) {
            // Если пользователь не вошел - показываем кнопку "Войти"
            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text("Войти в аккаунт / Регистрация", color = AppBlue, fontWeight = FontWeight.Bold)
            }
        } else {
            // Если пользователь вошел - показываем настройки
            Text("Вы авторизованы", color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))

            // Переключатель режима администратора
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Режим администратора", color = Color.White, modifier = Modifier.weight(1f))
                Switch(
                    checked = isAdmin,
                    onCheckedChange = onAdminToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = AppOrange)
                )
            }

            // Spacer с weight(1f) отталкивает кнопку "Выйти" в самый низ экрана
            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Выйти")
            }
        }
    }
}

// --- ДОБАВЛЕНИЕ ТОВАРА (Экран админа) ---
@Composable
fun AddProductScreen(onBack: () -> Unit, onSave: (Product) -> Unit) {
    // Переменные для хранения введенных данных
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // Механизм выбора нескольких картинок из галереи телефона
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> selectedImages = uris } // Когда пользователь выбрал картинки, сохраняем их в переменную

    Column(
        modifier = Modifier.fillMaxSize().background(AppBlue).padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        // Кнопка "Назад" (стрелочка влево)
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White) }
        Text("Новый товар", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // Поле ввода названия товара
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(8.dp)))
        Spacer(modifier = Modifier.height(8.dp))
        // Поле ввода описания товара
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Описание") }, modifier = Modifier.fillMaxWidth().height(120.dp).background(Color.White, RoundedCornerShape(8.dp)))
        Spacer(modifier = Modifier.height(16.dp))

        // Кнопка вызова галереи для выбора картинок
        Button(
            onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AppOrange)
        ) { Text("Выбрать картинки") }

        Spacer(modifier = Modifier.height(16.dp))
        // Превью выбранных картинок (горизонтальный прокручиваемый список)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(selectedImages.size) { index ->
                AsyncImage(
                    model = selectedImages[index], contentDescription = null,
                    modifier = Modifier.size(100.dp).background(Color.Gray, RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))

        // Кнопка сохранения товара.
        // Сработает только если введено название (не пустое) и выбрана хотя бы одна картинка.
        Button(
            onClick = {
                if (name.isNotBlank() && selectedImages.isNotEmpty()) {
                    onSave(Product(name = name, description = description, imageUris = selectedImages))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
        ) { Text("Сохранить товар") }
    }
}

// --- ЭКРАН ВХОДА ---
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.White).padding(24.dp),
        verticalArrangement = Arrangement.Center, // Центрируем содержимое по вертикали
        horizontalAlignment = Alignment.CenterHorizontally // Центрируем содержимое по горизонтали
    ) {
        Text(text = "ВХОД В АККАУНТ", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = AppBlue)
        Spacer(modifier = Modifier.height(32.dp))
        // Поле ввода имени (пока без логики сохранения)
        OutlinedTextField(value = "", onValueChange = {}, label = { Text("ИМЯ") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        // Поле ввода пароля (пока без логики сохранения)
        OutlinedTextField(value = "", onValueChange = {}, label = { Text("ПАРОЛЬ") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(32.dp))
        // Кнопка "Войти". При нажатии просто вызывает onLoginSuccess (имитация входа).
        Button(onClick = onLoginSuccess, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AppBlue)) {
            Text("ВОЙТИ")
        }
    }
}