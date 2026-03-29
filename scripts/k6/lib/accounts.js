import { fail } from "k6";

export function requireAccounts(accountIds) {
  if (!accountIds || accountIds.length === 0) {
    fail("ACCOUNT_IDS must contain at least one account UUID");
  }
}

export function pickAccount(accountIds, vu, iteration) {
  requireAccounts(accountIds);
  const index = (vu + iteration) % accountIds.length;
  return accountIds[index];
}
