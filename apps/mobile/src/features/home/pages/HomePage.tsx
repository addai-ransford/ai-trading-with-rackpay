import { useQuery } from "@tanstack/react-query";
import { ArrowDownLeft, ArrowLeftRight, ArrowRight, ArrowUpRight, ChartNoAxesCombined, ShieldCheck, WalletCards } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { apiFetch } from "../../../shared/api/httpClient";
import { useAuthStore } from "../../../shared/auth/authStore";
import { keycloak } from "../../../shared/auth/keycloak";

type Balance={balanceId:string;currency:string;balance:string|number};
type Transaction={transactionId:string;operationType:string;amount:string|number;currency:string;status:string;createdAt:string};
type Page<T>={content:T[];totalElements:number};
const money=(n:string|number,c:string)=>{try{return new Intl.NumberFormat(undefined,{style:"currency",currency:c,maximumFractionDigits:2}).format(Number(n));}catch{return `${n} ${c}`;}};

export function HomePage(){
 const navigate=useNavigate();
 const token=useAuthStore(s=>s.accessToken);const initialized=useAuthStore(s=>s.initialized);
 const wallet=useQuery({queryKey:["wallet"],queryFn:()=>apiFetch<{balances:Balance[]}>("/api/v1/wallet",{},token),enabled:Boolean(token)});
 const activity=useQuery({queryKey:["wallet-transactions"],queryFn:()=>apiFetch<Page<Transaction>>("/api/v1/wallet/transactions?page=0&size=5",{},token),enabled:Boolean(token)});
 return <section><div className="page-heading"><div><p className="eyebrow">YOUR FINANCIAL SPACE</p><h1>Good to have you here.</h1><p className="page-description">One place for your money, international transfers and smarter financial tools.</p></div></div>
 {!initialized?<div className="skeleton-card"/>:!token?<div className="notice-panel"><ShieldCheck size={24}/><div><strong>Your money, in one place.</strong><p>Sign in to view your wallet, make transfers and manage your RackPay account securely.</p><div className="auth-actions"><button className="button button-primary" onClick={()=>void keycloak.login()}>Sign in securely <ArrowRight size={16}/></button><button className="button button-secondary" onClick={()=>navigate("/login?mode=register")}>Create account</button></div></div></div>:<>
 <div className="section-heading"><div><h2>Wallet overview</h2><p>Live balances from your RackPay account</p></div><Link className="text-link" to="/wallet">View wallet <ArrowRight size={14}/></Link></div>
 {wallet.isLoading?<div className="skeleton-card"/>:wallet.isError?<div className="inline-error">Unable to load wallet.<button onClick={()=>void wallet.refetch()}>Retry</button></div>:wallet.data?.balances.length?<div className="balance-grid">{wallet.data.balances.slice(0,3).map(b=><div className="mini-balance" key={b.balanceId}><span><WalletCards size={15}/> {b.currency}</span><strong>{money(b.balance,b.currency)}</strong><small>Available balance</small></div>)}</div>:<div className="empty-inline">No currency balances yet. <Link to="/wallet">Open a balance <ArrowRight size={14}/></Link></div>}
 <div className="section-heading"><div><h2>Quick actions</h2><p>Choose what you want to do next</p></div></div>
 <div className="action-grid"><Link to="/wallet" className="action-card"><span className="action-icon"><WalletCards size={19}/></span><strong>Manage wallet</strong><p>Balances and transaction history</p><ArrowRight className="action-arrow" size={16}/></Link><Link to="/remittance" className="action-card"><span className="action-icon"><ArrowLeftRight size={19}/></span><strong>Send money</strong><p>International transfers</p><ArrowRight className="action-arrow" size={16}/></Link><Link to="/trading" className="action-card"><span className="action-icon"><ChartNoAxesCombined size={19}/></span><strong>AI Trading</strong><p>Trading and risk controls</p><ArrowRight className="action-arrow" size={16}/></Link></div>
 <div className="section-heading"><div><h2>Recent activity</h2><p>Latest wallet transactions</p></div><Link className="text-link" to="/wallet">Full history <ArrowRight size={14}/></Link></div>
 {activity.isLoading?<div className="skeleton-list"/>:activity.isError?<div className="inline-error">Unable to load activity.<button onClick={()=>void activity.refetch()}>Retry</button></div>:activity.data?.content.length?<div className="transaction-list">{activity.data.content.map(t=><div className="transaction-row" key={t.transactionId}><div className="transaction-icon">{t.operationType.toLowerCase().includes("credit")?<ArrowDownLeft size={18}/>:<ArrowUpRight size={18}/>}</div><div className="transaction-info"><strong>{t.operationType.replaceAll("_"," ")}</strong><span>{new Date(t.createdAt).toLocaleDateString()}</span></div><div className="transaction-value"><strong>{money(t.amount,t.currency)}</strong><span className={`state-pill state-${t.status.toLowerCase()}`}>{t.status.replaceAll("_"," ")}</span></div></div>)}</div>:<div className="empty-state"><strong>No transactions yet</strong><p>Your recent wallet activity will appear here.</p><Link to="/remittance" className="text-link">Start a transfer <ArrowRight size={14}/></Link></div>}
 <p className="compliance-note"><ShieldCheck size={14}/> Balances and transaction statuses are provided by the RackPay backend.</p></>}
 </section>;
}
