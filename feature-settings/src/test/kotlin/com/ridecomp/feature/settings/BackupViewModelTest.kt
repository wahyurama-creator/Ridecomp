package com.ridecomp.feature.settings

import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeAppRestarter
import com.ridecomp.core.testing.FakeBackupDatabase
import com.ridecomp.core.testing.FakeBackupFiles
import com.ridecomp.core.testing.FakeBackupManifestCodec
import com.ridecomp.core.testing.FakeBackupStateRepository
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeRideController
import com.ridecomp.core.testing.RecordingLogger
import com.ridecomp.core.testing.TestDispatcherProvider
import com.ridecomp.core.usecase.BackupWriter
import com.ridecomp.core.usecase.CreateBackupUseCase
import com.ridecomp.core.usecase.ObserveLastBackupUseCase
import com.ridecomp.core.usecase.ReadBackupSummaryUseCase
import com.ridecomp.core.usecase.RestartAfterRestoreUseCase
import com.ridecomp.core.usecase.RestoreBackupUseCase
import com.ridecomp.core.usecase.SuggestBackupFileNameUseCase
import com.ridecomp.feature.settings.viewmodel.BackupMessage
import com.ridecomp.feature.settings.viewmodel.BackupUiEvent
import com.ridecomp.feature.settings.viewmodel.BackupUseCases
import com.ridecomp.feature.settings.viewmodel.BackupViewModel
import com.ridecomp.feature.settings.viewmodel.RestoreStep
import java.io.File
import java.nio.file.Files
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val root: File = Files.createTempDirectory("backup-vm").toFile()
    private val database = FakeBackupDatabase(root)
    private val restarter = FakeAppRestarter()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        root.deleteRecursively()
    }

    private fun viewModel(): BackupViewModel {
        val files = FakeBackupFiles(root)
        val codec = FakeBackupManifestCodec()
        val clock = FakeClock()
        val dispatchers = TestDispatcherProvider(dispatcher)
        val controller = FakeRideController()
        val writer = BackupWriter(database, files, FakeActivityRepository(), codec, clock)
        val state = FakeBackupStateRepository()
        return BackupViewModel(
            BackupUseCases(
                ObserveLastBackupUseCase(state),
                SuggestBackupFileNameUseCase(clock, ZoneOffset.UTC),
                CreateBackupUseCase(writer, files, state, controller, dispatchers, RecordingLogger()),
                ReadBackupSummaryUseCase(files, codec, dispatchers),
                RestoreBackupUseCase(writer, database, files, codec, controller, dispatchers),
                RestartAfterRestoreUseCase(restarter),
            ),
        )
    }

    @Test
    fun `password must be long enough and repeated, then backup, unlock and two-step restore`() {
        val viewModel = viewModel()
        assertTrue(viewModel.suggestedFileName.startsWith("RideComp_backup_"))
        assertFalse(viewModel.uiState.value.canCreate)

        viewModel.onEvent(BackupUiEvent.Password("rahasia1"))
        viewModel.onEvent(BackupUiEvent.PasswordConfirm("rahasia2"))
        assertFalse(viewModel.uiState.value.canCreate)
        viewModel.onEvent(BackupUiEvent.PasswordConfirm("rahasia1"))
        viewModel.onEvent(BackupUiEvent.CreateTo(DOC))
        assertEquals(BackupMessage.Created(0), viewModel.uiState.value.message)
        assertEquals("", viewModel.uiState.value.password)

        viewModel.onEvent(BackupUiEvent.OpenFrom(DOC))
        assertIs<RestoreStep.NeedsPassword>(viewModel.uiState.value.restore)
        viewModel.onEvent(BackupUiEvent.RestorePassword("rahasia1"))
        viewModel.onEvent(BackupUiEvent.Unlock)
        val summary = assertIs<RestoreStep.Summary>(viewModel.uiState.value.restore)
        assertFalse(summary.isFinalConfirm)

        viewModel.onEvent(BackupUiEvent.ConfirmRestore)
        assertEquals(null, database.staged)
        viewModel.onEvent(BackupUiEvent.ConfirmRestore)
        assertEquals(RestoreStep.Done, viewModel.uiState.value.restore)
        assertEquals("rides", database.staged)

        viewModel.onEvent(BackupUiEvent.Restart)
        assertEquals(1, restarter.restarts)
    }

    private companion object {
        const val DOC = "vm.rcbackup"
    }
}
