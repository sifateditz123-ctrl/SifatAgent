import { GoogleGenAI, Type } from "@google/genai";
import { Router, type Request, type Response } from "express";
import {
  ActionPlanResponseSchema,
  ALLOWED_ACTIONS,
  AssistantRequestSchema
} from "../schemas/actionPlan";

const router = Router();

const FORBIDDEN_PATTERNS = [
  "transfer money",
  "send money",
  "bkash",
  "nagad",
  "bank transfer",
  "bypass lock",
  "wipe device",
  "factory reset",
  "rm -rf"
];

function buildSystemInstruction(language: string, screenSummary?: string): string {
  const langRule =
    language === "BANGLA"
      ? "Respond primarily in natural, polite Bangla."
      : language === "ENGLISH"
        ? "Respond in concise English."
        : "Detect whether the user wrote Bangla or English and respond in the same language.";

  const screenBlock = screenSummary
    ? `\nUser-authorized screen context:\n${screenSummary}\n`
    : "";

  return [
    "You are Sifat Agent, a safe bilingual (Bangla + English) Android personal assistant.",
    langRule,
    screenBlock,
    `Allowed actions: ${ALLOWED_ACTIONS.join(", ")}.`,
    "Never generate arbitrary code, payments, file deletions, or security bypasses.",
    "Use CONVERSATION for general Q&A, ACTION_PLAN for phone commands, CLARIFICATION when ambiguous, and BLOCKED for unsafe requests."
  ].join("\n");
}

router.post("/plan", async (req: Request, res: Response) => {
  const parsedBody = AssistantRequestSchema.safeParse(req.body);
  if (!parsedBody.success) {
    res.status(400).json({
      error: "Invalid request payload",
      details: parsedBody.error.flatten()
    });
    return;
  }

  const { prompt, language, screenSummary, history } = parsedBody.data;
  const lowerPrompt = prompt.toLowerCase();
  if (FORBIDDEN_PATTERNS.some((pattern) => lowerPrompt.includes(pattern))) {
    res.json({
      intentType: "BLOCKED",
      language: /[\u0980-\u09FF]/.test(prompt) ? "bn" : "en",
      replyText: "Blocked by Sifat Agent safety policy: financial or destructive commands are not permitted.",
      steps: []
    });
    return;
  }

  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    res.status(500).json({ error: "GEMINI_API_KEY is not configured on the backend." });
    return;
  }

  try {
    const ai = new GoogleGenAI({ apiKey });
    const model = process.env.GEMINI_MODEL || "gemini-3.8-flash";

    const contents = [
      ...history.map((item) => ({
        role: item.role,
        parts: [{ text: item.text }]
      })),
      {
        role: "user",
        parts: [{ text: prompt }]
      }
    ];

    const response = await ai.models.generateContent({
      model,
      contents,
      config: {
        systemInstruction: buildSystemInstruction(language, screenSummary),
        temperature: 0.2,
        responseMimeType: "application/json",
        responseSchema: {
          type: Type.OBJECT,
          properties: {
            intentType: {
              type: Type.STRING,
              enum: ["CONVERSATION", "ACTION_PLAN", "CLARIFICATION", "BLOCKED"]
            },
            language: { type: Type.STRING, enum: ["en", "bn"] },
            replyText: { type: Type.STRING },
            summary: { type: Type.STRING },
            steps: {
              type: Type.ARRAY,
              items: {
                type: Type.OBJECT,
                properties: {
                  action: { type: Type.STRING, enum: [...ALLOWED_ACTIONS] },
                  parameters: { type: Type.OBJECT }
                },
                required: ["action"]
              }
            }
          },
          required: ["intentType", "language", "replyText"]
        }
      }
    });

    const rawText = response.text;
    if (!rawText) {
      res.status(502).json({ error: "Gemini returned an empty response." });
      return;
    }

    const parsedPlan = ActionPlanResponseSchema.safeParse(JSON.parse(rawText));
    if (!parsedPlan.success) {
      res.status(502).json({
        error: "AI response failed strict allow-list validation."
      });
      return;
    }

    res.json(parsedPlan.data);
  } catch (err) {
    console.error("Gemini proxy error:", err);
    res.status(500).json({ error: "Failed to process assistant request." });
  }
});

export default router;
