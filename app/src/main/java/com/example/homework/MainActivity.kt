package com.example.homework

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHost
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.homework.ui.theme.HomeworkTheme
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.appcompat.app.AppCompatActivity
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.viewModels
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.room.Room
import coil.compose.AsyncImage


class MainActivity : ComponentActivity() {

    private val db by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "photos1.db"
        ).build()
    }

    private val viewModel by viewModels<PhotoViewModel>(
        factoryProducer = {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    return PhotoViewModel(db.photoDao) as T
                }
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeworkTheme {
                val state by viewModel.state.collectAsState()
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(state = state, onEvent = viewModel::onEvent)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(state: PhotoState, onEvent: (PhotoEvent) -> Unit) {
    val navController = rememberNavController()
    val allSamples: MutableList<Photo> = mutableListOf<Photo>()
    allSamples += SampleData.conversationSample
    val allStoredPhotos = state.photos
    println("TEST")
    println(allStoredPhotos)

//    data.add(PhotoState.pho)
    NavHost(
        navController = navController,
        startDestination = "firstPage") {
        composable("firstPage") { Conversation(state, navController) }
        composable("secondPage") { SecondPage(navController, state, onEvent) }
    }
}

@Composable
fun SecondPage(navController: NavHostController, state: PhotoState, onEvent: (PhotoEvent) -> Unit) {
    var selectedImageUri by remember {
        mutableStateOf<Uri?>(null)
    }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            selectedImageUri = uri
            if (uri != null) {
                onEvent(PhotoEvent.setPhoto(uri.toString()))
            }
         }
    )
    val author = remember { mutableStateOf("") }
    val body = remember { mutableStateOf("") }

    Column(modifier = Modifier
        .fillMaxHeight()
        .padding(16.dp)
    ) {
        Button(modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
            onClick = { navController.popBackStack() }) {
            Text("Go Back")
        }
        Button(modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
            onClick = { photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            ) }) {
            Text("Pick Photo")
        }
        AsyncImage(
            model = selectedImageUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(if(selectedImageUri != null) 40.dp else 0.dp)
                .clip(CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
        )
        OutlinedTextField(
            value = state.author,
            onValueChange = { onEvent(PhotoEvent.setAuthor(it)) },
            label = { Text("Author") },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.body,
            onValueChange = { onEvent(PhotoEvent.setBody(it)) },
            label = { Text("Body") },
        )
        Button(modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
            onClick = {
                println("WHAAT")
                println(state.body)
                println(state.photos)
                onEvent(PhotoEvent.SavePhoto)
                navController.popBackStack()}) {
            Text("Save Photo")
        }

    }
}

//data class Message(val photo: String, val author: String, val body: String)

@Composable
fun MessageCard(msg: Photo) {
    Row(modifier = Modifier.padding(all = 8.dp)) {

        AsyncImage(
            model = Uri.parse(msg.photoUri),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))

        // We keep track if the message is expanded or not in this
        // variable
        var isExpanded by remember { mutableStateOf(false) }

        // We toggle the isExpanded variable when we click on this Column
        Column(modifier = Modifier.clickable { isExpanded = !isExpanded }) {
            Text(
                text = msg.author,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 1.dp,
            ) {
                Text(
                    text = msg.body,
                    modifier = Modifier.padding(all = 4.dp),
                    // If the message is expanded, we display all its content
                    // otherwise we only display the first line
                    maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Preview(name = "Light Mode")
@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    name = "Dark Mode"
)
@Composable
fun PreviewMessageCard() {
    HomeworkTheme {
        Surface {
            MessageCard(
                msg = Photo(1, "", "Lexi", "Hey, take a look at Jetpack Compose, it's great!")
            )
        }
    }
}
@Composable
fun Conversation(state: PhotoState, navController: NavHostController) {
//    messages: List<Photo>
    Column(modifier = Modifier
        .fillMaxHeight()
        .padding(16.dp)
    ) {
        Button(
            onClick = { navController.navigate("secondPage") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text("Go to Second Page")
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.photos) { message ->
                MessageCard(message)
            }
        }
    }
}

