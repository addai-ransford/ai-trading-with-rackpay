export type WalletStatus="ACTIVE"|"LOCKED"|"CLOSED";
export type LedgerTransactionType="DEPOSIT"|"WITHDRAWAL"|"REMITTANCE"|"TRANSFER"|"TRADE"|"TRADING_PROFIT"|"TRADING_LOSS"|"FEE"|"REFUND";
export interface Wallet{readonly id:string;readonly userId:string;readonly status:WalletStatus;readonly createdAt:string;}
export interface LedgerEntry{readonly id:string;readonly walletId:string;readonly transactionId:string;readonly currency:string;readonly amountMinor:bigint;readonly createdAt:string;}
export interface LedgerTransaction{readonly id:string;readonly walletId:string;readonly type:LedgerTransactionType;readonly idempotencyKey:string;readonly createdAt:string;readonly entries:readonly LedgerEntry[];}
