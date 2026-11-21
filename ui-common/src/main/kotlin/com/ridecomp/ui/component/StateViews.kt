package com.ridecomp.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.SmallScreenPreviews
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme

/**
 * Generic centered message with an optional action — the base for the empty, waiting and error
 * states every data screen must have (02-DESIGN §3.13, UI-06).
 */
@Composable
fun MessageState(
    message: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionStyle: BigButtonStyle = BigButtonStyle.PRIMARY,
) {
    // Scrolls when the text does not fit (small screens, T13.3) and stays centred when it does. Inside a list
    // (height not bounded) it is a plain column: a scroll inside a scroll is not allowed.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val scroll = if (constraints.hasBoundedHeight) {
            Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
        } else {
            Modifier
        }
        Column(
            modifier = Modifier.fillMaxWidth().then(scroll).padding(RideCompDimens.spacingL),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingM, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MessageContent(message, detail, actionLabel, onAction, actionStyle)
        }
    }
}

@Composable
private fun MessageContent(
    message: String,
    detail: String?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    actionStyle: BigButtonStyle,
) {
    Text(
        text = message,
        style = RideCompTheme.typography.title.copy(fontSize = RideCompTheme.dimens.messageTitleSize),
        color = RideCompTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
    )
    if (detail != null) {
        Text(
            text = detail,
            style = RideCompTheme.typography.body,
            color = RideCompTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
    if (actionLabel != null && onAction != null) {
        BigButton(
            text = actionLabel,
            onClick = onAction,
            style = actionStyle,
            modifier = Modifier.widthIn(max = RideCompDimens.contentMaxWidth),
        )
    }
}

/** Empty list/screen: clear sentence + one action (e.g. "No rides yet" + START RIDE). */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) = MessageState(message = message, modifier = modifier, actionLabel = actionLabel, onAction = onAction)

/** Failed operation: readable cause + retry. Never shows a stack trace (02-DESIGN §3.13). */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) = MessageState(
    message = message,
    modifier = modifier,
    actionLabel = retryLabel,
    onAction = onRetry,
    actionStyle = BigButtonStyle.SECONDARY,
)

@ThemePreviews
@SmallScreenPreviews
@Composable
private fun EmptyStatePreview() {
    RideCompPreview {
        EmptyState(message = "No rides yet. Start your first ride", actionLabel = "START RIDE", onAction = {})
    }
}

@ThemePreviews
@Composable
private fun ErrorStatePreview() {
    RideCompPreview {
        ErrorState(message = "Could not read the GPX file", retryLabel = "Try again", onRetry = {})
    }
}
