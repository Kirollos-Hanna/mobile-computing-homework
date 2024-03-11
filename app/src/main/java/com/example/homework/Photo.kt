package com.example.homework

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Entity
data class Photo(
    @PrimaryKey(autoGenerate = true) val uid: Int = 0,
    @ColumnInfo(name = "photo_uri") val photoUri: String,
    @ColumnInfo(name = "author") val author: String,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "audioUrl") val audioUrl: String
)

data class PhotoState(
    var photos: List<Photo> = emptyList(),
    val photoUri: String = "",
    val author: String = "",
    val body: String = "",
    val audioUrl: String = ""
)

class PhotoViewModel(
    private val dao: PhotoDao
): ViewModel() {
    private val _state = MutableStateFlow(PhotoState())
    private val daoPhotosFlow: Flow<List<Photo>> = dao.getAll()

    val state = combine(_state, daoPhotosFlow) { state, photos ->
        state.copy(
            photos = photos,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PhotoState())
    fun onEvent(event: PhotoEvent){
        when(event) {
            is PhotoEvent.setPhoto -> {
                viewModelScope.launch {
                    _state.update { it.copy(
                        photoUri = event.photo
                    )}
                }
            }
            is PhotoEvent.setAuthor -> {
                viewModelScope.launch {
                    _state.update { it.copy(
                        author = event.author
                    )}
                }
            }
            is PhotoEvent.setBody -> {
                viewModelScope.launch {
                    _state.update { it.copy(
                        body = event.body
                    )}
                }
            }
            is PhotoEvent.setAudioUrl -> {
                viewModelScope.launch {
                    _state.update { it.copy(
                        audioUrl = event.audioUrl
                    )}
                }
            }
            is PhotoEvent.SavePhoto -> {
                val photoUri = state.value.photoUri
                val author = state.value.author
                val body = state.value.body
                val audioUrl = state.value.audioUrl
                println(photoUri + author + body + audioUrl)

                val photo = Photo(
                    photoUri = photoUri,
                    body = body,
                    author = author,
                    audioUrl = audioUrl
                )
                viewModelScope.launch {
                    dao.upsertPhoto(photo)
                }
                _state.update { it.copy(
                    photoUri = "",
                    body = "",
                    author = "",
                    audioUrl = ""
                ) }
            }
        }
    }
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photo")
    fun getAll(): Flow<List<Photo>>

    @Upsert
    suspend fun upsertPhoto(photo: Photo)

    @Delete
    suspend fun deletePhoto(photo: Photo)
}

sealed interface PhotoEvent {
    object SavePhoto: PhotoEvent
    data class setPhoto(val photo: String): PhotoEvent
    data class setAuthor(val author: String): PhotoEvent
    data class setBody(val body: String): PhotoEvent
    data class setAudioUrl(val audioUrl: String): PhotoEvent
}

@Database(
    entities = [Photo::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract val photoDao: PhotoDao


    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }
}