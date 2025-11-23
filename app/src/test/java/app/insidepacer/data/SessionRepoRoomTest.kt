package app.insidepacer.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.insidepacer.data.db.AppDatabase
import app.insidepacer.domain.Segment
import app.insidepacer.domain.SessionLog
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Test for SessionRepoRoom with focus on notes functionality.
 */
@RunWith(RobolectricTestRunner::class)
class SessionRepoRoomTest {
    private lateinit var database: AppDatabase
    private lateinit var repo: SessionRepoRoom
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = SessionRepoRoom(context, database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `append and load session with notes`() = runTest {
        val session = SessionLog(
            id = "session_1",
            programId = "program_1",
            startMillis = 1000L,
            endMillis = 2000L,
            totalSeconds = 300,
            segments = listOf(Segment(5.0, 300)),
            aborted = false,
            notes = "Great workout, felt strong!"
        )

        repo.append(session)
        val loaded = repo.loadAll()

        assertEquals(1, loaded.size)
        assertEquals("session_1", loaded[0].id)
        assertEquals("Great workout, felt strong!", loaded[0].notes)
    }

    @Test
    fun `append session without notes`() = runTest {
        val session = SessionLog(
            id = "session_2",
            programId = null,
            startMillis = 1000L,
            endMillis = 2000L,
            totalSeconds = 300,
            segments = listOf(Segment(5.0, 300)),
            aborted = false,
            notes = null
        )

        repo.append(session)
        val loaded = repo.loadAll()

        assertEquals(1, loaded.size)
        assertNull(loaded[0].notes)
    }

    @Test
    fun `update session notes`() = runTest {
        val session = SessionLog(
            id = "session_3",
            programId = null,
            startMillis = 1000L,
            endMillis = 2000L,
            totalSeconds = 300,
            segments = listOf(Segment(5.0, 300)),
            aborted = false,
            notes = null
        )

        repo.append(session)
        
        // Update notes
        repo.updateNotes("session_3", "Added notes after completion")
        
        val loaded = repo.loadAll()
        assertEquals(1, loaded.size)
        assertEquals("Added notes after completion", loaded[0].notes)
    }

    @Test
    fun `update notes multiple times`() = runTest {
        val session = SessionLog(
            id = "session_4",
            programId = null,
            startMillis = 1000L,
            endMillis = 2000L,
            totalSeconds = 300,
            segments = listOf(Segment(5.0, 300)),
            aborted = false,
            notes = "Initial notes"
        )

        repo.append(session)
        
        // Update notes first time
        repo.updateNotes("session_4", "Updated notes")
        var loaded = repo.loadAll()
        assertEquals("Updated notes", loaded[0].notes)
        
        // Update notes second time
        repo.updateNotes("session_4", "Final notes")
        loaded = repo.loadAll()
        assertEquals("Final notes", loaded[0].notes)
    }

    @Test
    fun `clear notes by setting to null`() = runTest {
        val session = SessionLog(
            id = "session_5",
            programId = null,
            startMillis = 1000L,
            endMillis = 2000L,
            totalSeconds = 300,
            segments = listOf(Segment(5.0, 300)),
            aborted = false,
            notes = "Some notes"
        )

        repo.append(session)
        
        // Clear notes
        repo.updateNotes("session_5", null)
        
        val loaded = repo.loadAll()
        assertEquals(1, loaded.size)
        assertNull(loaded[0].notes)
    }

    @Test
    fun `export CSV includes notes`() = runTest {
        val session1 = SessionLog(
            id = "session_6",
            programId = null,
            startMillis = 1000L,
            endMillis = 2000L,
            totalSeconds = 300,
            segments = listOf(Segment(5.0, 300)),
            aborted = false,
            notes = "Morning run, felt great"
        )
        
        val session2 = SessionLog(
            id = "session_7",
            programId = null,
            startMillis = 3000L,
            endMillis = 4000L,
            totalSeconds = 300,
            segments = listOf(Segment(5.5, 300)),
            aborted = false,
            notes = null
        )

        repo.append(session1)
        repo.append(session2)
        
        val (sessionsCsv, _) = repo.exportCsv(Units.MPH)
        val csvContent = sessionsCsv.readText()
        
        // Verify CSV contains notes column and the note
        assert(csvContent.contains("notes"))
        assert(csvContent.contains("Morning run, felt great"))
    }
}
