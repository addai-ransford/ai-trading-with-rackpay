export interface TradingLimits{readonly maxAllocatedAmountMinor:bigint;readonly maxSingleTradeAmountMinor:bigint;readonly maxDailyLossAmountMinor:bigint;readonly maxPortfolioExposureAmountMinor:bigint;readonly maxOpenPositions:number;}
export interface TradingAccount{readonly id:string;readonly walletId:string;readonly enabled:boolean;readonly limits:TradingLimits;}
export interface TradeSignal{readonly id:string;readonly strategyId:string;readonly symbol:string;readonly side:"BUY"|"SELL";readonly quantity:string;readonly proposedNotionalMinor:bigint;readonly createdAt:string;}
export interface RiskDecision{readonly signalId:string;readonly approved:boolean;readonly reasons:readonly string[];}
