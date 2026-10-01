package com.agon.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppearancePreferencesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: FoodRepository

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmp.root, "appearance.preferences_pb") },
        )
        repo = FoodRepository(dataStore, File(tmp.root, "corrupt"))
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `blur defaults on and liquid glass defaults off`() = runBlocking {
        assertTrue(repo.miuixBlurEnabledFlow.first())
        assertFalse(repo.liquidGlassNavEnabledFlow.first())
    }

    @Test
    fun `liquid glass requires blur and disabling blur also disables liquid glass`() = runBlocking {
        repo.setMiuixBlurEnabled(false)
        assertFalse(repo.miuixBlurEnabledFlow.first())

        repo.setLiquidGlassNavEnabled(true)
        assertTrue(repo.miuixBlurEnabledFlow.first())
        assertTrue(repo.liquidGlassNavEnabledFlow.first())

        repo.setMiuixBlurEnabled(false)
        assertFalse(repo.miuixBlurEnabledFlow.first())
        assertFalse(repo.liquidGlassNavEnabledFlow.first())
    }

    @Test
    fun `turning off liquid glass keeps blur enabled`() = runBlocking {
        repo.setLiquidGlassNavEnabled(true)
        repo.setLiquidGlassNavEnabled(false)

        assertTrue(repo.miuixBlurEnabledFlow.first())
        assertFalse(repo.liquidGlassNavEnabledFlow.first())
    }
}
