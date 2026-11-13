package com.ridecomp.feature.settings

import com.ridecomp.core.testing.TestDispatcherProvider
import com.ridecomp.feature.settings.viewmodel.OssComponent
import com.ridecomp.feature.settings.viewmodel.OssLicensesUiEvent
import com.ridecomp.feature.settings.viewmodel.OssLicensesViewModel
import com.ridecomp.feature.settings.viewmodel.paragraphs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class OssLicensesViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val readRes = mutableListOf<Int>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = OssLicensesViewModel(
        reader = { res ->
            readRes += res
            "BSD 2-Clause License\n\nCopyright (c) 2021 MapLibre contributors\n  \n\nRedistribution..."
        },
        dispatchers = TestDispatcherProvider(dispatcher),
    )

    @Test
    fun `opening a component loads its text as paragraphs and closing returns to the list`() {
        val viewModel = viewModel()

        viewModel.onEvent(OssLicensesUiEvent.Open(OssComponent.MAPLIBRE_NATIVE))

        assertEquals(OssComponent.MAPLIBRE_NATIVE, viewModel.uiState.value.selected)
        assertEquals(listOf(OssComponent.MAPLIBRE_NATIVE.textRes), readRes)
        assertEquals(3, viewModel.uiState.value.paragraphs.size)

        viewModel.onEvent(OssLicensesUiEvent.Close)

        assertNull(viewModel.uiState.value.selected)
        assertTrue(viewModel.uiState.value.paragraphs.isEmpty())
    }

    @Test
    fun `paragraphs split on blank lines, including CRLF and whitespace-only lines`() {
        assertEquals(listOf("a\nb", "c", "d"), "a\nb\r\n\r\nc\n \n\n d \n".paragraphs())
    }
}
