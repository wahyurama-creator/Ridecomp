package com.ridecomp.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.ridecomp.ui.theme.RideCompTheme
import org.junit.Rule
import org.junit.Test

class DataFieldTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun label_is_uppercased_and_value_shown() {
        composeRule.setContent {
            RideCompTheme { DataField(label = "Speed", value = "32.4", unit = "km/h") }
        }

        composeRule.onNodeWithText("SPEED", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("32.4", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun missing_value_shows_placeholder_not_zero() {
        composeRule.setContent {
            RideCompTheme {
                DataField(label = "HR", value = DataFieldDefaults.NO_VALUE, status = DataFieldStatus.WARNING)
            }
        }

        composeRule.onNodeWithText(DataFieldDefaults.NO_VALUE, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun field_exposes_merged_accessibility_description() {
        composeRule.setContent {
            RideCompTheme { DataField(label = "Cadence", value = "90", unit = "rpm") }
        }

        composeRule.onNodeWithContentDescription("Cadence 90 rpm").assertIsDisplayed()
    }
}
