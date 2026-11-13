package com.ridecomp.feature.settings.viewmodel

import android.content.Context
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.feature.settings.R
import com.ridecomp.ui.udf.NoEffect
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Third-party notices shipped with the app (BSD-2-Clause and Apache-2.0 require them, D-65). The texts
 * are the upstream files of the versions in the version catalog; update them when a dependency changes.
 */
enum class OssComponent(
    @param:StringRes val titleRes: Int,
    @param:StringRes val licenseRes: Int,
    @param:RawRes val textRes: Int,
) {
    MAPLIBRE_NATIVE(R.string.licenses_maplibre_native, R.string.licenses_bsd_2, R.raw.license_maplibre_native),
    MAPLIBRE_COMPONENTS(
        R.string.licenses_maplibre_components,
        R.string.licenses_various,
        R.raw.license_maplibre_components,
    ),
    MAPLIBRE_GESTURES(R.string.licenses_maplibre_gestures, R.string.licenses_bsd_2, R.raw.license_maplibre_gestures),
    APACHE_LIBRARIES(R.string.licenses_apache_libraries, R.string.licenses_apache_2, R.raw.license_apache_2_0),
    OPENSTREETMAP(R.string.licenses_openstreetmap, R.string.licenses_odbl, R.raw.license_openstreetmap),
}

/** Reads a bundled text resource; blocking, call on the IO dispatcher. */
fun interface LicenseTextReader {
    fun read(@RawRes textRes: Int): String
}

/** [LicenseTextReader] over the app's raw resources. */
class AndroidLicenseTextReader(private val context: Context) : LicenseTextReader {
    override fun read(textRes: Int): String =
        context.resources.openRawResource(textRes).bufferedReader().use { it.readText() }
}

@Immutable
data class OssLicensesUiState(
    val selected: OssComponent? = null,
    /** Paragraphs of the selected license, so a long text is laid out lazily. */
    val paragraphs: ImmutableList<String> = persistentListOf(),
)

sealed interface OssLicensesUiEvent {
    data class Open(val component: OssComponent) : OssLicensesUiEvent

    data object Close : OssLicensesUiEvent
}

/** Open source licenses (D-65): list of components, then the selected license text. */
class OssLicensesViewModel(private val reader: LicenseTextReader, private val dispatchers: DispatcherProvider) :
    UdfViewModel<OssLicensesUiState, OssLicensesUiEvent, NoEffect>(OssLicensesUiState()) {
    private var loading: Job? = null

    override fun onEvent(event: OssLicensesUiEvent) {
        loading?.cancel()
        when (event) {
            is OssLicensesUiEvent.Open -> open(event.component)
            OssLicensesUiEvent.Close -> updateState { OssLicensesUiState() }
        }
    }

    private fun open(component: OssComponent) {
        updateState { OssLicensesUiState(selected = component) }
        loading = viewModelScope.launch {
            val paragraphs = withContext(dispatchers.io) { reader.read(component.textRes).paragraphs() }
            updateState { copy(paragraphs = paragraphs) }
        }
    }
}

/** Splits on blank lines and drops empty blocks. */
internal fun String.paragraphs(): ImmutableList<String> =
    split(PARAGRAPH_BREAK).map { it.trim() }.filter { it.isNotEmpty() }.toImmutableList()

private val PARAGRAPH_BREAK = Regex("""\R\s*\R""")
