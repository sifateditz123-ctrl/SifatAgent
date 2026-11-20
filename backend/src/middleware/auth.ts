import crypto from "node:crypto";
import type { NextFunction, Request, Response } from "express";

export function requireBearerAuth(req: Request, res: Response, next: NextFunction): void {
  const expectedToken = process.env.CLIENT_AUTH_TOKEN;
  if (!expectedToken) {
    res.status(500).json({ error: "Server authentication is not configured." });
    return;
  }

  const header = req.headers.authorization;
  if (!header || !header.startsWith("Bearer ")) {
    res.status(401).json({ error: "Missing or malformed Authorization header." });
    return;
  }

  const supplied = header.slice("Bearer ".length).trim();
  const a = Buffer.from(supplied, "utf8");
  const b = Buffer.from(expectedToken, "utf8");

  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) {
    res.status(403).json({ error: "Invalid client token." });
    return;
  }

  next();
}
