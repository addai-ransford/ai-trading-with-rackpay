export function countryFlag(countryCode: string): string {
  const normalized = countryCode.trim().toUpperCase();
  if (!/^[A-Z]{2}$/.test(normalized)) return "🌐";

  return String.fromCodePoint(
    ...[...normalized].map((character) => 127397 + character.charCodeAt(0)),
  );
}

export function isValidAmount(value: string): boolean {
  if (!value.trim()) return false;
  const amount = Number(value);
  return Number.isFinite(amount) && amount > 0;
}

export function isValidRecipientPhone(
  phoneNumber: string,
  countryDialCode: string,
): boolean {
  const phoneDigits = phoneNumber.replace(/\D/g, "");
  const localPhoneDigits = phoneDigits.startsWith("0")
    ? phoneDigits.slice(1)
    : phoneDigits;
  const isFlutterwaveGhanaSandboxNumber =
    countryDialCode.replace(/\D/g, "") === "233" && phoneDigits === "054709929220";

  return countryDialCode.replace(/\D/g, "") === "233"
    ? localPhoneDigits.length === 9 || isFlutterwaveGhanaSandboxNumber
    : phoneDigits.length >= 6;
}

export function isQuoteExpired(expiresAt: string, now: number): boolean {
  const expiryTime = Date.parse(expiresAt);
  return !Number.isFinite(expiryTime) || expiryTime <= now;
}
