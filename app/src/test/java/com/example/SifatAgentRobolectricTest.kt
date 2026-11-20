package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.automation.ActionType
import com.example.automation.SafeAutomationExecutor
import com.example.automation.ValidatedAction
import com.example.automation.ActionExecutionResult
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SifatAgentRobolectricTest {

    @Test
    fun `master automation kill switch blocks execution safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = SafeAutomationExecutor(context)
        val action = ValidatedAction(
            actionType = ActionType.GO_HOME,
            parameters = emptyMap(),
            previewSummary = "Navigate to Home screen",
            requiresConfirmation = false
        )

        val result = executor.executeAction(action, automationMasterEnabled = false)
        assertTrue(result is ActionExecutionResult.Cancelled)
    }

    @Test
    fun `accessibility action returns permission denied when service not connected`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = SafeAutomationExecutor(context)
        val action = ValidatedAction(
            actionType = ActionType.READ_SCREEN_SUMMARY,
            parameters = emptyMap(),
            previewSummary = "Read active screen",
            requiresConfirmation = true
        )

        val result = executor.executeAction(action, automationMasterEnabled = true)
        assertTrue(result is ActionExecutionResult.PermissionDenied)
    }
}
