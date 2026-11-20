package com.example

import com.example.automation.ActionType
import com.example.automation.ActionValidator
import com.example.automation.ParsedAiPlan
import com.example.automation.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActionValidatorTest {

    @Test
    fun `parses normal bilingual conversation cleanly without actions`() {
        val json = """
            {
              "intentType": "CONVERSATION",
              "language": "bn",
              "replyText": "আমি সিফাত এজেন্ট, আপনার বাংলা ও ইংরেজি সহকারী।"
            }
        """.trimIndent()

        val parsed = ActionValidator.parseStructuredPlanJson(json)
        assertTrue(parsed is ParsedAiPlan.Conversation)
        val conv = parsed as ParsedAiPlan.Conversation
        assertEquals("bn", conv.language)
        assertTrue(conv.replyText.contains("সিফাত"))
    }

    @Test
    fun `parses valid multi-step allow-listed action plan`() {
        val json = """
            {
              "intentType": "ACTION_PLAN",
              "language": "en",
              "replyText": "Opening YouTube and scrolling down.",
              "summary": "Launch YouTube and scroll",
              "steps": [
                {
                  "action": "LAUNCH_APP",
                  "parameters": { "appName": "YouTube" }
                },
                {
                  "action": "SCROLL_SCREEN",
                  "parameters": { "direction": "DOWN" }
                }
              ]
            }
        """.trimIndent()

        val parsed = ActionValidator.parseStructuredPlanJson(json)
        assertTrue(parsed is ParsedAiPlan.StructuredActionPlan)
        val plan = (parsed as ParsedAiPlan.StructuredActionPlan).plan
        assertEquals(2, plan.steps.size)
        assertEquals(ActionType.LAUNCH_APP, plan.steps[0].actionType)
        assertEquals("YouTube", plan.steps[0].parameters["appName"])
        assertEquals(ActionType.SCROLL_SCREEN, plan.steps[1].actionType)
        assertEquals("DOWN", plan.steps[1].parameters["direction"])
        assertTrue(plan.requiresConfirmation)
    }

    @Test
    fun `rejects unknown or arbitrary action outside allow-list`() {
        val json = """
            {
              "intentType": "ACTION_PLAN",
              "language": "en",
              "replyText": "Running shell command",
              "steps": [
                {
                  "action": "EXEC_SHELL",
                  "parameters": { "cmd": "rm -rf /" }
                }
              ]
            }
        """.trimIndent()

        val parsed = ActionValidator.parseStructuredPlanJson(json)
        assertTrue(parsed is ParsedAiPlan.BlockedOrInvalid)
    }

    @Test
    fun `rejects unsafe url schemes such as javascript or file`() {
        assertNull(ActionValidator.normalizeSafeHttpUrl("javascript:alert(1)"))
        assertNull(ActionValidator.normalizeSafeHttpUrl("file:///etc/passwd"))
        assertNull(ActionValidator.normalizeSafeHttpUrl("intent://scan/#Intent;end"))
        assertEquals(
            "https://developer.android.com",
            ActionValidator.normalizeSafeHttpUrl("developer.android.com")
        )
    }

    @Test
    fun `enforces confirmation and high risk on DIAL_NUMBER and COMPOSE_SMS`() {
        val json = """
            {
              "intentType": "ACTION_PLAN",
              "language": "en",
              "replyText": "Ready to compose SMS",
              "steps": [
                {
                  "action": "COMPOSE_SMS",
                  "parameters": {
                    "phoneNumber": "+880 1700-000000",
                    "messageBody": "Meeting at 5 PM"
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = ActionValidator.parseStructuredPlanJson(json)
        assertTrue(parsed is ParsedAiPlan.StructuredActionPlan)
        val step = (parsed as ParsedAiPlan.StructuredActionPlan).plan.steps.first()
        assertEquals(RiskLevel.HIGH, step.riskLevel)
        assertTrue(step.requiresConfirmation)
        assertEquals("+8801700000000", step.parameters["phoneNumber"])
    }

    @Test
    fun `detects forbidden financial and security bypass prompts`() {
        assertTrue(ActionValidator.isPromptForbidden("Please transfer money via bKash now"))
        assertTrue(ActionValidator.isPromptForbidden("Bypass lock screen immediately"))
        assertFalse(ActionValidator.isPromptForbidden("Open Wi-Fi settings"))
    }

    @Test
    fun `serializes and deserializes action plans symmetrically`() {
        val json = """
            {
              "intentType": "ACTION_PLAN",
              "language": "en",
              "replyText": "Adjust brightness",
              "summary": "Set brightness to 60%",
              "steps": [
                {
                  "action": "ADJUST_BRIGHTNESS",
                  "parameters": { "levelPercent": "60" }
                }
              ]
            }
        """.trimIndent()
        val parsed = ActionValidator.parseStructuredPlanJson(json) as ParsedAiPlan.StructuredActionPlan
        val serialized = ActionValidator.serializePlanToJson(parsed.plan)
        val restored = ActionValidator.deserializePlanFromJson(serialized)
        assertNotNull(restored)
        assertEquals("60", restored?.steps?.first()?.parameters?.get("levelPercent"))
    }
}
