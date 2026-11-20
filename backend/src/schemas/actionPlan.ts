import { z } from "zod";

export const ALLOWED_ACTIONS = [
  "LAUNCH_APP",
  "OPEN_SETTINGS",
  "GO_HOME",
  "GO_BACK",
  "READ_SCREEN_SUMMARY",
  "CLICK_ELEMENT",
  "SCROLL_SCREEN",
  "OPEN_URL",
  "ADJUST_BRIGHTNESS",
  "DIAL_NUMBER",
  "COMPOSE_SMS"
] as const;

export const AssistantRequestSchema = z.object({
  prompt: z.string().trim().min(1).max(2000),
  language: z.enum(["AUTO", "ENGLISH", "BANGLA"]).optional().default("AUTO"),
  screenSummary: z.string().max(3000).optional(),
  history: z
    .array(
      z.object({
        role: z.enum(["user", "model"]),
        text: z.string().max(2000)
      })
    )
    .max(12)
    .optional()
    .default([])
});

export const ActionStepSchema = z.object({
  action: z.enum(ALLOWED_ACTIONS),
  parameters: z.record(z.string(), z.string()).optional().default({})
});

export const ActionPlanResponseSchema = z.object({
  intentType: z.enum(["CONVERSATION", "ACTION_PLAN", "CLARIFICATION", "BLOCKED"]),
  language: z.enum(["en", "bn"]),
  replyText: z.string().min(1),
  summary: z.string().optional(),
  steps: z.array(ActionStepSchema).optional().default([])
});

export type AssistantRequest = z.infer<typeof AssistantRequestSchema>;
export type ActionPlanResponse = z.infer<typeof ActionPlanResponseSchema>;
