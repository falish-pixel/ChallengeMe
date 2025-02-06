package com.example.challengeme

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage

// Приложение
class ChallangeMeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}

// Главная активность
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContent {
            ChallangeMeAppTheme {
                val navController = rememberNavController()
                MainScreen(navController)
            }
        }
    }
}

// Тема приложения
@Composable
fun ChallangeMeAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

// Главный экран с навигацией
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("ChallangeME") })
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            NavigationGraph(navController)
        }
    }
}

// Навигация
@Composable
fun NavigationGraph(navController: NavHostController) {
    NavHost(navController, startDestination = "login") {
        composable("login") { LoginScreen(navController) }
        composable("home") { HomeScreen(navController) }
        composable("profile") { ProfileScreen(User("Димаш", 5, 100, 10)) }
    }
}

@Composable
fun LoginScreen(navController: NavHostController) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    // Set up Google Sign-In client
    val googleSignInClient = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("821912809748-eg452bfdp6abbdpsq57o1ucgm4gr5mfe.apps.googleusercontent.com") // Replace with your web client ID from Firebase
            .requestEmail()
            .build()
    )
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                    auth.signInWithCredential(credential).addOnCompleteListener { authResult ->
                        if (authResult.isSuccessful) {
                            println("Вход успешен")
                            navController.navigate("home")
                        } else {
                            println("Ошибка входа: ${authResult.exception?.message}")
                        }
                    }
                }
            } catch (e: ApiException) {
                println("Ошибка при получении учетных данных: ${e.message}")
            }
        }
    }
    // Google Sign-In Button
    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = {
            val signInIntent = googleSignInClient.signInIntent
            launcher.launch(signInIntent)
        }) {
            Text("Войти через Google")
        }
    }
}

// Главный экран
@Composable
fun HomeScreen(navController: NavHostController) {
    val sampleChallenges = listOf(
        Challenge("1", "100 push-ups", "url1"),
        Challenge("2", "Run 5km", "url2")
    )
    LazyColumn {
        items(sampleChallenges) { challenge ->
            ChallengeItem(challenge)
        }
    }
}

// Элемент челленджа
@Composable
fun ChallengeItem(challenge: Challenge) {
    Card(modifier = Modifier.padding(8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = challenge.title)
            Button(onClick = { /* Отметить выполнение */ }) {
                Text("Выполнено")
            }
            Button(onClick = {
                FirestoreManager.createDuel(challenge.id, "opponentId")
            }) {
                Text("Вызвать на дуэль")
            }
            val videoUri = Uri.parse("file://test_video.mp4") // Тестовый URI
            Button(onClick = {
                StorageManager.uploadVideo(videoUri) { url ->
                    println("Видео загружено: $url")
                }
            }) {
                Text("Загрузить видео")
            }
        }
    }
}

// Экран профиля
@Composable
fun ProfileScreen(user: User) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Имя: ${user.name}")
        Text("Уровень: ${user.level}")
        Text("HP: ${user.hp}")
        Text("Streaks: ${user.streaks}")
    }
}

// Менеджер аутентификации
object AuthManager {
    private val auth = FirebaseAuth.getInstance()
    fun signInWithGoogle(googleCredential: AuthCredential, onResult: (Boolean) -> Unit) {
        auth.signInWithCredential(googleCredential).addOnCompleteListener { task ->
            onResult(task.isSuccessful)
        }
    }
}

// Менеджер Firestore
object FirestoreManager {
    fun createDuel(challengeId: String, opponentId: String) {
        val db = FirebaseFirestore.getInstance() // Instantiate Firestore here, not statically
        val duel = hashMapOf(
            "challengeId" to challengeId,
            "creatorId" to FirebaseAuth.getInstance().currentUser?.uid,
            "opponentId" to opponentId,
            "status" to "pending"
        )
        db.collection("duels").add(duel)
    }
}

// Менеджер хранилища
object StorageManager {
    fun uploadVideo(uri: Uri, onSuccess: (String) -> Unit) {
        val storageRef = Firebase.storage.reference.child("videos/${System.currentTimeMillis()}.mp4")
        storageRef.putFile(uri).addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { url ->
                onSuccess(url.toString())
            }
        }
    }
}

// Модели данных
data class Challenge(val id: String, val title: String, val videoProof: String)
data class User(val name: String, val level: Int, val hp: Int, val streaks: Int)

// Превью
@Preview
@Composable
fun PreviewHomeScreen() {
    ChallangeMeAppTheme {
        HomeScreen(rememberNavController())
    }
}
