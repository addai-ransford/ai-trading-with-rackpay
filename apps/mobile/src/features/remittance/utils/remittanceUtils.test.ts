import { describe, expect, it } from "vitest";
import {
  countryFlag,
  isQuoteExpired,
  isValidAmount,
  isValidRecipientPhone,
} from "./remittanceUtils";

describe("countryFlag", () => {
  it("renders a regional indicator flag for a two-letter country code", () => {
    expect(countryFlag("gh")).toBe("🇬🇭");
  });

  it("returns a globe for invalid country codes", () => {
    expect(countryFlag("GHA")).toBe("🌐");
    expect(countryFlag("")).toBe("🌐");
  });
});

describe("isValidAmount", () => {
  it("accepts positive finite amounts", () => {
    expect(isValidAmount("100")).toBe(true);
    expect(isValidAmount("0.01")).toBe(true);
  });

  it("rejects empty, zero, negative, and non-finite amounts", () => {
    expect(isValidAmount("")).toBe(false);
    expect(isValidAmount("   ")).toBe(false);
    expect(isValidAmount("0")).toBe(false);
    expect(isValidAmount("-1")).toBe(false);
    expect(isValidAmount("Infinity")).toBe(false);
    expect(isValidAmount("not-a-number")).toBe(false);
  });
});

describe("isValidRecipientPhone", () => {
  it("accepts nine local digits for Ghana after an optional leading zero", () => {
    expect(isValidRecipientPhone("024 123 4567", "+233")).toBe(true);
    expect(isValidRecipientPhone("241234567", "+233")).toBe(true);
  });

  it("rejects invalid Ghana local number lengths", () => {
    expect(isValidRecipientPhone("02412345", "+233")).toBe(false);
    expect(isValidRecipientPhone("02412345678", "+233")).toBe(false);
  });

  it("requires at least six digits for other corridors", () => {
    expect(isValidRecipientPhone("12345", "+254")).toBe(false);
    expect(isValidRecipientPhone("071 234 5678", "+254")).toBe(true);
  });
});

describe("isQuoteExpired", () => {
  const now = Date.parse("2026-10-11T12:00:00.000Z");

  it("keeps a quote valid before its expiry", () => {
    expect(isQuoteExpired("2026-10-11T12:01:00.000Z", now)).toBe(false);
  });

  it("treats a quote as expired at or after its expiry", () => {
    expect(isQuoteExpired("2026-10-11T12:00:00.000Z", now)).toBe(true);
    expect(isQuoteExpired("2026-10-11T11:59:00.000Z", now)).toBe(true);
  });

  it("fails closed when the expiry timestamp is invalid", () => {
    expect(isQuoteExpired("not-a-date", now)).toBe(true);
  });
});
