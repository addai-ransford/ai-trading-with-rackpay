import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, Clock3, Send } from "lucide-react";
import { listRemittances, getRemittance, type Remittance } from "../api/remittanceApi";
import { useAuthStore } from "../../../shared/auth/authStore";

const terminalStatuses = new Set(["COMPLETED", "FAILED", "CANCELLED"]);

export function RemittanceHistoryPage() {
  const accessToken = useAuthStore((state) => state.accessToken);
  const { remittanceId } = useParams();

  const listQuery = useQuery({
    queryKey: ["remittance", "history"],
    queryFn: () => listRemittances(accessToken!),
    enabled: Boolean(accessToken) && !remittanceId,
  });

  const detailQuery = useQuery({
    queryKey: ["remittance", "detail", remittanceId],
    queryFn: () => getRemittance(accessToken!, remittanceId!),
    enabled: Boolean(accessToken && remittanceId),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status && (terminalStatuses.has(status) || status === "RECOVERY_REQUIRED")
        ? false
        : 3000;
    },
  });

  if (!accessToken) return null;

  if (remittanceId) {
    return <RemittanceDetail remittance={detailQuery.data} isLoading={detailQuery.isPending} isError={detailQuery.isError} />;
  }

  return (
    <section className="flex min-h-dvh flex-col gap-6 pb-10 pt-4">
      <header className="flex items-center gap-3">
        <Link to="/" className="rounded-xl border border-slate-800 p-2 text-slate-300" aria-label="Back home">
          <ArrowLeft size={18} />
        </Link>
        <div>
          <p className="text-sm text-slate-500">Activity</p>
          <h1 className="text-3xl font-semibold tracking-tight">Remittance history</h1>
        </div>
      </header>

      {listQuery.isPending ? <p className="text-sm text-slate-500">Loading transfers…</p> : null}
      {listQuery.isError ? (
        <p className="rounded-2xl border border-red-900/50 bg-red-950/30 p-4 text-sm text-red-300">
          We could not load your remittance history.
        </p>
      ) : null}

      {!listQuery.isPending && !listQuery.isError && listQuery.data?.length === 0 ? (
        <div className="rounded-3xl border border-slate-800 bg-slate-900 p-6 text-center">
          <Send className="mx-auto" size={24} />
          <p className="mt-3 font-medium">No remittances yet</p>
          <p className="mt-1 text-sm text-slate-500">Your completed and in-progress transfers will appear here.</p>
          <Link to="/remittance" className="mt-5 inline-flex rounded-2xl bg-white px-4 py-3 text-sm font-semibold text-slate-950">
            Send money
          </Link>
        </div>
      ) : null}

      <div className="grid gap-3">
        {listQuery.data?.map((item) => (
          <Link
            key={item.remittanceId}
            to={`/remittance/history/${item.remittanceId}`}
            className="rounded-3xl border border-slate-800 bg-slate-900 p-5"
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="font-medium">{item.recipientName}</p>
                <p className="mt-1 text-xs text-slate-500">{formatDate(item.createdAt)}</p>
              </div>
              <StatusBadge status={item.status} />
            </div>
            <div className="mt-4 flex items-end justify-between gap-4">
              <span className="text-sm text-slate-500">You sent</span>
              <span className="font-semibold">{item.sourceAmount} {item.sourceCurrency}</span>
            </div>
            <div className="mt-1 flex items-end justify-between gap-4">
              <span className="text-sm text-slate-500">Recipient gets</span>
              <span className="text-sm">{item.destinationAmount} {item.destinationCurrency}</span>
            </div>
          </Link>
        ))}
      </div>
    </section>
  );
}

function RemittanceDetail({
  remittance,
  isLoading,
  isError,
}: {
  remittance?: Remittance;
  isLoading: boolean;
  isError: boolean;
}) {
  return (
    <section className="flex min-h-dvh flex-col gap-6 pb-10 pt-4">
      <header className="flex items-center gap-3">
        <Link to="/remittance/history" className="rounded-xl border border-slate-800 p-2 text-slate-300" aria-label="Back to remittance history">
          <ArrowLeft size={18} />
        </Link>
        <div>
          <p className="text-sm text-slate-500">Transfer details</p>
          <h1 className="text-3xl font-semibold tracking-tight">Remittance</h1>
        </div>
      </header>

      {isLoading ? <p className="text-sm text-slate-500">Loading transfer…</p> : null}
      {isError ? (
        <p className="rounded-2xl border border-red-900/50 bg-red-950/30 p-4 text-sm text-red-300">
          We could not load this remittance.
        </p>
      ) : null}

      {remittance ? (
        <div className="grid gap-4">
          <section className="rounded-3xl border border-slate-800 bg-slate-900 p-5">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-xs uppercase tracking-wide text-slate-500">Recipient</p>
                <p className="mt-1 text-lg font-semibold">{remittance.recipientName}</p>
              </div>
              <StatusBadge status={remittance.status} />
            </div>
            <p className="mt-4 break-all text-xs text-slate-600">{remittance.remittanceId}</p>
          </section>

          <section className="rounded-3xl border border-slate-800 bg-slate-900 p-5">
            <DetailRow label="You sent" value={`${remittance.sourceAmount} ${remittance.sourceCurrency}`} />
            <DetailRow label="Fee" value={`${remittance.feeAmount} ${remittance.sourceCurrency}`} />
            <DetailRow label="Recipient gets" value={`${remittance.destinationAmount} ${remittance.destinationCurrency}`} />
            {remittance.payoutProvider ? <DetailRow label="Payout provider" value={remittance.payoutProvider} /> : null}
            {remittance.providerTransferId ? <DetailRow label="Provider transfer" value={remittance.providerTransferId} /> : null}
          </section>

          <section className="rounded-3xl border border-slate-800 bg-slate-900 p-5 text-sm">
            <div className="flex items-center gap-2 text-slate-400">
              <Clock3 size={16} />
              <span>Created {formatDate(remittance.createdAt)}</span>
            </div>
            <p className="mt-2 text-slate-500">Updated {formatDate(remittance.updatedAt)}</p>
            <p className="mt-4 text-xs leading-5 text-slate-500">
              Transfer status and provider details come from the backend. The mobile app does not infer financial state.
            </p>
          </section>
        </div>
      ) : null}
    </section>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-slate-800 py-3 last:border-b-0">
      <span className="text-sm text-slate-500">{label}</span>
      <span className="text-right text-sm font-medium">{value}</span>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  return (
    <span className="rounded-full bg-slate-800 px-2.5 py-1 text-xs font-medium text-slate-300">
      {status.replaceAll("_", " ")}
    </span>
  );
}

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}
