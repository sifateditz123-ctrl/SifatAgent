import "dotenv/config";
import cors from "cors";
import express from "express";
import rateLimit from "express-rate-limit";
import helmet from "helmet";
import { requireBearerAuth } from "./middleware/auth";
import assistantRouter from "./routes/assistant";

const app = express();
const port = Number(process.env.PORT || 8443);

app.use(helmet());
app.use(express.json({ limit: "32kb" }));

const allowedOrigins = (process.env.ALLOWED_ORIGINS || "")
  .split(",")
  .map((o) => o.trim())
  .filter(Boolean);

app.use(
  cors({
    origin: allowedOrigins.length > 0 ? allowedOrigins : false
  })
);

const assistantLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 30,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: "Rate limit exceeded. Please wait before sending more commands." }
});

app.get("/api/v1/health", (_req, res) => {
  res.json({ status: "ok", service: "sifat-agent-backend" });
});

app.use("/api/v1/assistant", assistantLimiter, requireBearerAuth, assistantRouter);

app.listen(port, () => {
  console.log(`Sifat Agent secure backend listening on port ${port}`);
});
