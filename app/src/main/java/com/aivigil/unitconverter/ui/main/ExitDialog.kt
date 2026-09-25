package com.aivigil.unitconverter.ui.main

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivigil.unitconverter.BuildConfig
import com.aivigil.unitconverter.R
import com.aivigil.unitconverter.data.RatingPrefsStore
import com.aivigil.unitconverter.ui.theme.AccentAmber
import com.aivigil.unitconverter.ui.theme.ElectricCyan
import com.aivigil.unitconverter.ui.theme.NeonPurpleLight
import com.aivigil.unitconverter.ui.theme.SurfaceCard
import com.aivigil.unitconverter.ui.theme.SurfaceInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * State holder for the exit confirmation prompt.
 *
 * Deliberately does NOT use the Play In-App Review API. Google's guidance for that API
 * says an app "shouldn't ask the user any questions before or while presenting the
 * rating button or card", and that you "should not have a call-to-action option (such
 * as a button) to trigger the API", because the review flow is quota-limited and may
 * silently fail to appear. An exit dialog is exactly that forbidden call-to-action, so
 * the stars here open the Play Store listing directly instead.
 *
 * Every star opens the same destination. Routing high ratings to the Store and low ones
 * to a private feedback form is review gating and is not something this app does.
 */
@Stable
class ExitPromptController internal constructor(
    private val store: RatingPrefsStore,
    private val scope: CoroutineScope,
) {
    /** Whether the confirmation dialog is on screen. */
    var visible by mutableStateOf(false)
        private set

    /** Whether to include the star row this time. */
    var showRating by mutableStateOf(false)
        private set

    fun show() {
        visible = true
        if (BuildConfig.DEBUG) {
            showRating = true
            return
        }
        scope.launch {
            val hasRated = store.hasRated()
            val count = store.promptCount()
            val eligible = !hasRated && count < RatingPrefsStore.MAX_PROMPTS
            showRating = eligible
            if (eligible) store.recordPromptShown()
        }
    }

    fun dismiss() {
        visible = false
    }

    /** User tapped a star; remember it so we stop asking. */
    fun onRated() {
        showRating = false
        scope.launch { store.recordRated() }
    }
}

@Composable
fun rememberExitPromptController(
    scope: CoroutineScope,
): ExitPromptController {
    val context = LocalContext.current.applicationContext
    return remember(context, scope) { ExitPromptController(RatingPrefsStore(context), scope) }
}

/**
 * "Are you sure you want to exit?" with an optional rating row.
 *
 * @param onConfirmExit finishes the Activity.
 */
@Composable
fun ExitConfirmDialog(
    controller: ExitPromptController,
    onConfirmExit: () -> Unit,
) {
    if (!controller.visible) return
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = controller::dismiss,
        containerColor = SurfaceCard,
        titleContentColor = Color.White,
        textContentColor = Color(0xFF9CA3AF),
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = stringResource(R.string.exit_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.exit_message),
                    fontSize = 14.sp,
                )
                if (controller.showRating) {
                    Spacer(Modifier.height(20.dp))
                    RatingRow(
                        onStarSelected = {
                            controller.onRated()
                            openPlayStoreListing(context)
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmExit) {
                Text(
                    text = stringResource(R.string.exit_confirm),
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = controller::dismiss) {
                Text(
                    text = stringResource(R.string.exit_cancel),
                    color = NeonPurpleLight,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    )
}

@Composable
private fun RatingRow(onStarSelected: (Int) -> Unit) {
    // Purely a launcher for the Play Store listing: the selection is not stored or
    // used to decide where the user is sent. Filling on hover-in from the left is
    // the familiar affordance, so stars fill up to whichever one is pressed.
    var previewed by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceInput)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.exit_rate_label),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..5).forEach { star ->
                val filled = star <= previewed
                Icon(
                    imageVector = if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = stringResource(R.string.exit_rate_star, star),
                    tint = if (filled) AccentAmber else Color(0xFF4B5563),
                    modifier = Modifier
                        .size(48.dp) // ≥ 48dp touch target per DESIGN.md
                        .clip(CircleShape)
                        .clickable {
                            previewed = star
                            onStarSelected(star)
                        }
                        .padding(8.dp),
                )
            }
        }
    }
}

/**
 * Opens this app's Play Store listing. Falls back to the https URL when the Play Store
 * app isn't installed, and does nothing at all if neither resolves — a missing browser
 * must not crash the exit dialog.
 */
private fun openPlayStoreListing(context: Context) {
    val packageName = context.packageName
    val market = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$packageName"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        context.startActivity(market)
    } catch (e: ActivityNotFoundException) {
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(web)
        } catch (e2: ActivityNotFoundException) {
            // No Play Store and no browser. Nothing to do; the dialog stays usable.
        }
    }
}