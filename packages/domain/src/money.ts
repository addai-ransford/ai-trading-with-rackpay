export type CurrencyCode=string;
export interface Money{readonly amountMinor:bigint;readonly currency:CurrencyCode;}
export const money=(amountMinor:bigint|number,currency:CurrencyCode):Money=>({amountMinor:BigInt(amountMinor),currency:currency.toUpperCase()});
export const assertSameCurrency=(left:Money,right:Money):void=>{if(left.currency!==right.currency)throw new Error(`Currency mismatch: ${left.currency} != ${right.currency}`);};
