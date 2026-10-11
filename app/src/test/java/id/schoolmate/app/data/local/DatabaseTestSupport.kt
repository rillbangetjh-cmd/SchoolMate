package id.schoolmate.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import id.schoolmate.app.data.local.entity.SubjectEntity

fun inMemoryDatabase(): SchoolMateDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SchoolMateDatabase::class.java)
        .allowMainThreadQueries()
        .build()

suspend fun SchoolMateDatabase.insertSubject(name: String, color: Int = 0xFF006A60.toInt()): Long =
    subjectDao().insert(SubjectEntity(name = name, teacher = "", colorArgb = color, createdAt = 0L))
