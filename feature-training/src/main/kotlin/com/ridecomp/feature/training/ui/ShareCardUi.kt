package com.ridecomp.feature.training.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.engine.share.ShareCardConfig
import com.ridecomp.core.engine.share.ShareDetail
import com.ridecomp.core.engine.share.ShareFormat
import com.ridecomp.core.engine.share.SharePalette
import com.ridecomp.core.engine.share.ShareTemplate
import com.ridecomp.feature.training.R
import com.ridecomp.feature.training.viewmodel.ShareAction
import com.ridecomp.feature.training.viewmodel.ShareCardUiEvent
import com.ridecomp.feature.training.viewmodel.ShareCardUiState
import com.ridecomp.feature.training.viewmodel.ShareCardViewModel
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonSize
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.Choice
import com.ridecomp.ui.component.ChoiceChips
import com.ridecomp.ui.component.EmptyState
import com.ridecomp.ui.component.IconSquareButton
import com.ridecomp.ui.component.ListCard
import com.ridecomp.ui.component.PageIndicator
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SectionHeader
import com.ridecomp.ui.component.SwitchRow
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.theme.ShareCardColors
import kotlinx.collections.immutable.persistentListOf
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Styles in swipe order: the most striking first. */
private val PAGER_TEMPLATES =
    listOf(ShareTemplate.POSTER, ShareTemplate.PHOTO, ShareTemplate.STICKER, ShareTemplate.CLASSIC)

/** Share card editor (T4.5/T4.5b, 13-UI-SCREENS #21): swipe a style, then share; the rest is in "Sesuaikan". */
@Composable
fun ShareCardRoute(
    activityId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShareCardViewModel = koinViewModel(key = "share-$activityId") { parametersOf(activityId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.onEvent(ShareCardUiEvent.PhotoPicked(it.toString())) }
    }
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ShareCardConfig.MIME_TYPE),
    ) { uri -> uri?.let { viewModel.onEvent(ShareCardUiEvent.Render(ShareAction.SaveTo(it.toString()))) } }
    ShareCardScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onPickPhoto = {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onSave = {
            val startedAtMs = state.data?.activity?.startedAtMs ?: 0L
            saveLauncher.launch(ShareCardConfig.FILE_PREFIX + startedAtMs + ShareCardConfig.FILE_EXTENSION)
        },
        modifier = modifier,
    )
}

@Composable
fun ShareCardScreen(
    state: ShareCardUiState,
    onEvent: (ShareCardUiEvent) -> Unit,
    onBack: () -> Unit,
    onPickPhoto: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isCustomizing by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(R.string.share_title),
        onBack = onBack,
        backLabel = stringResource(R.string.segment_back),
    ) {
        when {
            state.isMissing -> EmptyState(message = stringResource(R.string.share_missing))
            state.data != null -> Editor(state, onEvent, onPickPhoto, onSave, onCustomize = { isCustomizing = true })
        }
    }
    if (isCustomizing) CustomizeSheet(state, onEvent, onDismiss = { isCustomizing = false })
    if (state.isSharing && state.data != null) CardCapture(state, onEvent)
}

@Composable
private fun Editor(
    state: ShareCardUiState,
    onEvent: (ShareCardUiEvent) -> Unit,
    onPickPhoto: () -> Unit,
    onSave: () -> Unit,
    onCustomize: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = RideCompDimens.spacingM),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.cardGap),
    ) {
        StylePager(state, onEvent, onPickPhoto, Modifier.weight(1f))
        StyleBar(state, onEvent)
        Actions(state, onEvent, onSave, onCustomize)
    }
}

/** The card itself, full height; swiping changes the style. */
@Composable
private fun StylePager(
    state: ShareCardUiState,
    onEvent: (ShareCardUiEvent) -> Unit,
    onPickPhoto: () -> Unit,
    modifier: Modifier,
) {
    val pager = rememberPagerState(initialPage = PAGER_TEMPLATES.indexOf(state.template).coerceAtLeast(0)) {
        PAGER_TEMPLATES.size
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { onEvent(ShareCardUiEvent.SetTemplate(PAGER_TEMPLATES[it])) }
    }
    HorizontalPager(
        state = pager,
        modifier = modifier.fillMaxWidth(),
        userScrollEnabled = !state.isSharing,
    ) { page ->
        val pageState = state.copy(template = PAGER_TEMPLATES[page])
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(
                        state.format.widthPx.toFloat() / state.format.heightPx,
                        matchHeightConstraintsFirst = true,
                    )
                    // A thin frame shows where the card ends on a screen of the same color.
                    .border(RideCompDimens.borderThin, RideCompTheme.colors.divider),
                contentAlignment = Alignment.Center,
            ) {
                if (pageState.isTransparent) Checkerboard(Modifier.fillMaxSize())
                ShareCard(state = pageState, modifier = Modifier.fillMaxSize())
                if (pageState.template == ShareTemplate.PHOTO) PhotoButton(state.photo != null, onPickPhoto)
            }
        }
    }
}

/** On the photo style: "Pilih foto" in the middle until there is one, then a small "Ganti foto" at the top. */
@Composable
private fun PhotoButton(hasPhoto: Boolean, onPickPhoto: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = if (hasPhoto) Alignment.TopEnd else Alignment.Center) {
        BigButton(
            text = stringResource(if (hasPhoto) R.string.share_change_photo else R.string.share_pick_photo),
            onClick = onPickPhoto,
            style = BigButtonStyle.SECONDARY,
            size = BigButtonSize.COMPACT,
            modifier = Modifier.padding(RideCompDimens.spacingSm),
        )
    }
}

/** Page dots with the style name, and the poster colours as swatches. */
@Composable
private fun StyleBar(state: ShareCardUiState, onEvent: (ShareCardUiEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
    ) {
        val name = stringResource(state.template.labelRes())
        Text(text = name, style = RideCompTheme.typography.bodyStrong, color = RideCompTheme.colors.textPrimary)
        PageIndicator(
            count = PAGER_TEMPLATES.size,
            current = PAGER_TEMPLATES.indexOf(state.template).coerceAtLeast(0),
            description = stringResource(R.string.share_style_page, name),
        )
        when (state.template) {
            ShareTemplate.POSTER -> PaletteSwatches(state.palette, onEvent)
            ShareTemplate.STICKER -> Text(
                text = stringResource(R.string.share_sticker_hint),
                style = RideCompTheme.typography.caption,
                color = RideCompTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = RideCompDimens.screenPaddingHorizontal),
            )
            ShareTemplate.PHOTO, ShareTemplate.CLASSIC -> Unit
        }
    }
}

@Composable
private fun PaletteSwatches(selected: SharePalette, onEvent: (ShareCardUiEvent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingM)) {
        SharePalette.entries.forEach { palette ->
            val label = stringResource(palette.labelRes())
            Box(
                modifier = Modifier
                    .size(RideCompDimens.swatchSize)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(palette.swatchColors()))
                    .then(
                        if (palette == selected) {
                            Modifier.border(
                                RideCompDimens.borderSelected,
                                RideCompTheme.colors.textPrimary,
                                CircleShape,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clickable(role = Role.RadioButton) { onEvent(ShareCardUiEvent.SetPalette(palette)) }
                    .semantics { contentDescription = label },
            )
        }
    }
}

private fun SharePalette.swatchColors() = when (this) {
    SharePalette.SUNSET -> listOf(ShareCardColors.SunsetTop, ShareCardColors.SunsetBottom)
    SharePalette.OCEAN -> listOf(ShareCardColors.OceanTop, ShareCardColors.OceanBottom)
    SharePalette.FOREST -> listOf(ShareCardColors.ForestTop, ShareCardColors.ForestBottom)
    SharePalette.NIGHT -> listOf(ShareCardColors.NightTop, ShareCardColors.NightBottom)
}

/** "Sesuaikan" as an icon, then two compact buttons: Simpan and Bagikan. */
@Composable
private fun Actions(
    state: ShareCardUiState,
    onEvent: (ShareCardUiEvent) -> Unit,
    onSave: () -> Unit,
    onCustomize: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = RideCompDimens.screenPaddingHorizontal),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
    ) {
        Messages(state)
        Row(
            horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconSquareButton(
                iconRes = UiR.drawable.ic_tune,
                contentDescription = stringResource(R.string.share_customize),
                onClick = { if (!state.isSharing) onCustomize() },
                tint = RideCompTheme.colors.textPrimary,
            )
            BigButton(
                text = stringResource(R.string.share_save_short),
                onClick = onSave,
                isEnabled = !state.isSharing,
                style = BigButtonStyle.OUTLINE,
                size = BigButtonSize.COMPACT,
                iconRes = UiR.drawable.ic_download,
                modifier = Modifier.weight(1f),
            )
            BigButton(
                text = stringResource(R.string.share_send_short),
                onClick = { onEvent(ShareCardUiEvent.Render(ShareAction.Share)) },
                isEnabled = !state.isSharing,
                size = BigButtonSize.COMPACT,
                iconRes = UiR.drawable.ic_share,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Everything a rider rarely changes: format, privacy, background, details and statistics. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomizeSheet(state: ShareCardUiState, onEvent: (ShareCardUiEvent) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = RideCompTheme.colors.bg) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = RideCompDimens.screenPaddingHorizontal,
                    end = RideCompDimens.screenPaddingHorizontal,
                    bottom = RideCompDimens.screenPaddingBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap),
        ) {
            ChoiceChips(
                label = stringResource(R.string.share_format),
                choices = persistentListOf(
                    Choice(ShareFormat.STORY, stringResource(R.string.share_format_story)),
                    Choice(ShareFormat.SQUARE, stringResource(R.string.share_format_square)),
                ),
                selected = state.format,
                onSelect = { onEvent(ShareCardUiEvent.SetFormat(it)) },
                horizontalPadding = RideCompDimens.none,
                columns = FORMAT_COLUMNS,
            )
            SectionHeader(stringResource(R.string.share_privacy), horizontalPadding = RideCompDimens.none)
            ListCard {
                SwitchRow(
                    label = stringResource(R.string.share_hide_ends),
                    description = stringResource(R.string.share_hide_ends_hint),
                    isChecked = state.isEndsHidden,
                    onCheckedChange = { onEvent(ShareCardUiEvent.SetEndsHidden(it)) },
                )
                SwitchRow(
                    label = stringResource(R.string.share_show_track),
                    isChecked = state.isTrackShown,
                    onCheckedChange = { onEvent(ShareCardUiEvent.SetTrackShown(it)) },
                )
            }
            BackgroundOptions(state, onEvent)
            DetailsAndStats(state, onEvent)
        }
    }
}

/** Transparent background for poster and classic, light/dark for classic. */
@Composable
private fun BackgroundOptions(state: ShareCardUiState, onEvent: (ShareCardUiEvent) -> Unit) {
    if (state.template != ShareTemplate.POSTER && state.template != ShareTemplate.CLASSIC) return
    SectionHeader(stringResource(R.string.share_background), horizontalPadding = RideCompDimens.none)
    ListCard {
        SwitchRow(
            label = stringResource(R.string.share_transparent),
            description = stringResource(R.string.share_transparent_hint),
            isChecked = state.isBackgroundTransparent,
            onCheckedChange = { onEvent(ShareCardUiEvent.SetBackgroundTransparent(it)) },
        )
        if (state.template == ShareTemplate.CLASSIC) {
            SwitchRow(
                label = stringResource(R.string.share_dark),
                isChecked = state.isDark,
                onCheckedChange = { onEvent(ShareCardUiEvent.SetDark(it)) },
            )
        }
    }
}

@Composable
private fun DetailsAndStats(state: ShareCardUiState, onEvent: (ShareCardUiEvent) -> Unit) {
    if (state.availableDetails.isNotEmpty()) {
        SectionHeader(stringResource(R.string.share_details), horizontalPadding = RideCompDimens.none)
        ListCard {
            state.availableDetails.forEach { detail ->
                SwitchRow(
                    label = stringResource(detail.labelRes()),
                    isChecked = detail in state.details,
                    onCheckedChange = { onEvent(ShareCardUiEvent.ToggleDetail(detail)) },
                )
            }
        }
    }
    SectionHeader(stringResource(R.string.share_stats), horizontalPadding = RideCompDimens.none)
    ListCard {
        state.availableStats.forEach { stat ->
            SwitchRow(
                label = stringResource(stat.labelRes()),
                isChecked = stat in state.stats,
                onCheckedChange = { onEvent(ShareCardUiEvent.ToggleStat(stat)) },
            )
        }
    }
}

@Composable
private fun Checkerboard(modifier: Modifier) {
    Canvas(modifier = modifier.clipToBounds()) {
        val cell = RideCompDimens.checkerCell.toPx()
        val columns = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        drawRect(ShareCardColors.CheckerDark)
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                if ((row + column) % 2 == 0) {
                    drawRect(ShareCardColors.CheckerLight, Offset(column * cell, row * cell), Size(cell, cell))
                }
            }
        }
    }
}

@Composable
private fun Messages(state: ShareCardUiState) {
    val message = when {
        state.isShareFailed -> R.string.share_failed
        state.isSaved -> R.string.share_saved
        else -> null
    } ?: return
    Text(
        text = stringResource(message),
        style = RideCompTheme.typography.caption,
        color = if (state.isShareFailed) RideCompTheme.colors.warnText else RideCompTheme.colors.ok,
    )
}

/**
 * Draws the card once at its real pixel size (1080 × 1920 or 1080 × 1080) into a graphics layer that is not
 * shown; the ViewModel encodes it as PNG off the main thread (§11: rendered offline). Transparent cards keep
 * their alpha in the PNG.
 */
@Composable
private fun CardCapture(state: ShareCardUiState, onEvent: (ShareCardUiEvent) -> Unit) {
    val layer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val width = with(density) { state.format.widthPx.toDp() }
    val height = with(density) { state.format.heightPx.toDp() }
    Box(modifier = Modifier.size(0.dp).clipToBounds()) {
        ShareCard(
            state = state,
            modifier = Modifier
                .requiredSize(width, height)
                .drawWithContent { layer.record { this@drawWithContent.drawContent() } },
        )
    }
    // Composed only while sharing, and the options are locked meanwhile: one capture per tap.
    LaunchedEffect(Unit) {
        // Two frames: the card is laid out and its layer recorded before reading it.
        withFrameNanos { }
        withFrameNanos { }
        onEvent(ShareCardUiEvent.Captured(layer.toImageBitmap()))
    }
}

@StringRes
private fun ShareTemplate.labelRes(): Int = when (this) {
    ShareTemplate.CLASSIC -> R.string.share_template_classic
    ShareTemplate.POSTER -> R.string.share_template_poster
    ShareTemplate.PHOTO -> R.string.share_template_photo
    ShareTemplate.STICKER -> R.string.share_template_sticker
}

@StringRes
private fun SharePalette.labelRes(): Int = when (this) {
    SharePalette.SUNSET -> R.string.share_palette_sunset
    SharePalette.OCEAN -> R.string.share_palette_ocean
    SharePalette.FOREST -> R.string.share_palette_forest
    SharePalette.NIGHT -> R.string.share_palette_night
}

@StringRes
private fun ShareDetail.labelRes(): Int = when (this) {
    ShareDetail.ELEVATION -> R.string.share_detail_elevation
    ShareDetail.HEART_RATE_ZONES -> R.string.share_detail_zones
    ShareDetail.SPLITS -> R.string.share_detail_splits
    ShareDetail.BADGES -> R.string.share_detail_badges
}

private const val FORMAT_COLUMNS = 2
