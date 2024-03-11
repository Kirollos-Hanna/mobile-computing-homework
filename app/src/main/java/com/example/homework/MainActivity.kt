package com.example.homework

import android.Manifest
import com.google.accompanist.permissions.rememberPermissionState
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.homework.ui.theme.HomeworkTheme
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.viewModels
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.room.Room
import coil.compose.AsyncImage
import kotlin.math.abs
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Environment
import androidx.compose.runtime.MutableState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.shouldShowRationale
import java.io.File
import java.io.IOException

val CHANNEL_ID = "Notification-channel"
private const val LOG_TAG = "AudioRecordTest"
private const val REQUEST_RECORD_AUDIO_PERMISSION = 200

class MainActivity : ComponentActivity(), SensorEventListener {
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val db by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "photos1.db"
        ).fallbackToDestructiveMigration().build()
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

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        println(accuracy)
    }
    private var lastRotation: List<Float>? = null

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientationAngles = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            // Convert radians to degrees
            val degrees = orientationAngles.map { Math.toDegrees(it.toDouble()).toFloat() }

            if (lastRotation != null) {
                val deltaRotation = degrees.zip(lastRotation!!).map { abs(it.first - it.second) }
                if (deltaRotation.any { it > 20 }) {
                    showNotification("New Message", "Device rotated", this, 2)
                }
            }

            lastRotation = degrees
        }
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.also { rotation ->
            sensorManager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
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

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AudioRecordTestApp(onEvent: (PhotoEvent) -> Unit) {
    // Context
    val context = LocalContext.current

    val recordAudioPermissionState = rememberPermissionState(
        android.Manifest.permission.RECORD_AUDIO
    )
    if (recordAudioPermissionState.status.isGranted) {
        Text("Mic permission Granted")
    } else {
        Column {
            val textToShow = if (recordAudioPermissionState.status.shouldShowRationale) {
                // If the user has denied the permission but the rationale can be shown,
                // then gently explain why the app requires this permission
                "The mic is important for this app. Please grant the permission."
            } else {
                // If it's the first time the user lands on this feature, or the user
                // doesn't want to be asked again for this permission, explain that the
                // permission is required
                "Mic permission required for this feature to be available. " +
                        "Please grant the permission"
            }
            Text(textToShow)
            Button(onClick = { recordAudioPermissionState.launchPermissionRequest() }) {
                Text("Request permission")
            }
        }
    }
    // Recording state
    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    val fileName = remember { File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "audiorecordtest-${System.currentTimeMillis()}.3gp").absolutePath }
    println("FILENAME: $fileName")
    // MediaRecorder and MediaPlayer instances
    val recorder = remember { mutableStateOf<MediaRecorder?>(null) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }

    Column {
        Button(onClick = {
            if (isRecording) {
                stopRecording(recorder)
                onEvent(PhotoEvent.setAudioUrl(fileName))
            } else {
                startRecording(context, recorder, fileName)
            }
            isRecording = !isRecording
        }) {
            Text(if (isRecording) "Stop recording" else "Start recording")
        }

        Button(onClick = {
            if (isPlaying) {
                stopPlaying(player)
            } else {
                startPlaying(player, fileName)
            }
            isPlaying = !isPlaying
        }) {
            Text(if (isPlaying) "Stop playing" else "Start playing")
        }
    }
}

fun startRecording(context: Context, recorderState: MutableState<MediaRecorder?>, fileName: String) {

    recorderState.value = MediaRecorder(context).apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        setOutputFile(fileName)
        setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        try {
            prepare()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        start()
    }
}

fun stopRecording(recorderState: MutableState<MediaRecorder?>) {
    recorderState.value?.apply {
        stop()
        release()
    }
    recorderState.value = null
}

fun startPlaying(playerState: MutableState<MediaPlayer?>, fileName: String) {
    playerState.value = MediaPlayer().apply {
        try {
            setDataSource(fileName)
            prepare()
            start()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}

fun stopPlaying(playerState: MutableState<MediaPlayer?>) {
    playerState.value?.apply {
        stop()
        release()
    }
    playerState.value = null
}

fun showNotification(title: String, text: String, context: Context, notificationId: Int) {
    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is new and not in the support library
    val name = "Notification Channel"
    val descriptionText = "Channel Description"
    val importance = NotificationManager.IMPORTANCE_DEFAULT
    val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
        description = descriptionText
    }
    // Register the channel with the system
    val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.createNotificationChannel(channel)
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent: PendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.notification)
        .setContentTitle(title)
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)

    with(NotificationManagerCompat.from(context)) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1)
        }
        notify(notificationId, builder.build())
    }
}


@Composable
fun AppNavigation(state: PhotoState, onEvent: (PhotoEvent) -> Unit) {
    val navController = rememberNavController()
    val allSamples: MutableList<Photo> = mutableListOf<Photo>()
    allSamples += SampleData.conversationSample
    NavHost(
        navController = navController,
        startDestination = "firstPage") {
        composable("firstPage") { Conversation(state, navController) }
        composable("secondPage") { SecondPage(navController, state, onEvent) }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SecondPage(navController: NavHostController, state: PhotoState, onEvent: (PhotoEvent) -> Unit) {
    val context = LocalContext.current
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
        AudioRecordTestApp(onEvent)
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
                showNotification("New Message", "A new message has appeared.", context, 1)
                onEvent(PhotoEvent.SavePhoto)
                navController.popBackStack()}) {
            Text("Save Photo")
        }

    }
}

@Composable
fun MessageCard(msg: Photo) {
    var isPlaying by remember { mutableStateOf(false) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }
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
            Button(onClick = {
                if (isPlaying) {
                    stopPlaying(player)
                } else {
                    println("MSG URL: ${msg.audioUrl}")
                    startPlaying(player, msg.audioUrl)
                }
                isPlaying = !isPlaying
            }) {
                Text(if (isPlaying) "Stop playing" else "Start playing")
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
                msg = Photo(1, "", "Lexi", "Hey, take a look at Jetpack Compose, it's great!", "")
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

