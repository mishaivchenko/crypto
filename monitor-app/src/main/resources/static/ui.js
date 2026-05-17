import { t, getLocale } from "./i18n.js";

function buildStatusLabels() {
    return {
        candidate: {
            NEW: t("status_candidate_NEW"),
            NORMALIZED: t("status_candidate_NORMALIZED"),
            FAILED: t("status_candidate_FAILED"),
            REJECTED: t("status_candidate_REJECTED"),
            EVENT_CREATED: t("status_candidate_EVENT_CREATED"),
            DELETED: t("status_candidate_DELETED")
        },
        event: {
            DISCOVERED: t("status_event_DISCOVERED"),
            ARMED: t("status_event_ARMED"),
            EXPIRED: t("status_event_EXPIRED"),
            CANCELLED: t("status_event_CANCELLED")
        },
        historyStage: {
            PREPARED: t("status_historyStage_PREPARED"),
            ENTRY_PENDING: t("status_historyStage_ENTRY_PENDING"),
            ENTRY_ATTEMPTED: t("status_historyStage_ENTRY_ATTEMPTED"),
            ATTEMPTS_FAILED: t("status_historyStage_ATTEMPTS_FAILED"),
            MISSED_WINDOW: t("status_historyStage_MISSED_WINDOW"),
            OPEN: t("status_historyStage_OPEN"),
            EXIT_PENDING: t("status_historyStage_EXIT_PENDING"),
            CLOSED: t("status_historyStage_CLOSED"),
            CANCELLED: t("status_historyStage_CANCELLED"),
            FAILED: t("status_historyStage_FAILED"),
            DEV_TEST: t("status_historyStage_DEV_TEST")
        },
        trade: {
            ARMED: t("status_trade_ARMED"),
            ENTRY_PENDING: t("status_trade_ENTRY_PENDING"),
            ENTRY_ATTEMPTED: t("status_trade_ENTRY_ATTEMPTED"),
            OPEN: t("status_trade_OPEN"),
            EXIT_PENDING: t("status_trade_EXIT_PENDING"),
            CLOSED: t("status_trade_CLOSED"),
            CANCELLED: t("status_trade_CANCELLED"),
            FAILED: t("status_trade_FAILED")
        },
        orderAttempt: {
            PLANNED: t("status_orderAttempt_PLANNED"),
            CREATED: t("status_orderAttempt_CREATED"),
            SUBMITTED: t("status_orderAttempt_SUBMITTED"),
            ACKNOWLEDGED: t("status_orderAttempt_ACKNOWLEDGED"),
            FILLED: t("status_orderAttempt_FILLED"),
            CANCELLED: t("status_orderAttempt_CANCELLED"),
            REJECTED: t("status_orderAttempt_REJECTED"),
            FAILED: t("status_orderAttempt_FAILED"),
            EXPIRED: t("status_orderAttempt_EXPIRED")
        },
        actor: {
            SYSTEM: t("status_actor_SYSTEM"),
            OPERATOR: t("status_actor_OPERATOR")
        },
        connection: {
            NOT_CONNECTED: t("status_connection_NOT_CONNECTED"),
            CONNECTED: t("status_connection_CONNECTED"),
            INVALID_CREDENTIALS: t("status_connection_INVALID_CREDENTIALS"),
            ERROR: t("status_connection_ERROR"),
            UNSUPPORTED: t("status_connection_UNSUPPORTED")
        },
        journal: {
            CANDIDATE_APPROVED: t("status_journal_CANDIDATE_APPROVED"),
            CANDIDATE_REJECTED: t("status_journal_CANDIDATE_REJECTED"),
            CANDIDATE_DELETED: t("status_journal_CANDIDATE_DELETED"),
            FUNDING_EVENT_CREATED: t("status_journal_FUNDING_EVENT_CREATED"),
            FUNDING_EVENT_ARMED: t("status_journal_FUNDING_EVENT_ARMED"),
            ARMED_TRADE_CREATED: t("status_journal_ARMED_TRADE_CREATED")
        }
    };
}

const badgeTones = {
    candidate: {
        NEW: "neutral",
        NORMALIZED: "info",
        FAILED: "bad",
        REJECTED: "muted",
        EVENT_CREATED: "good",
        DELETED: "muted"
    },
    event: {
        DISCOVERED: "warning",
        ARMED: "good",
        EXPIRED: "bad",
        CANCELLED: "muted"
    },
    historyStage: {
        PREPARED: "info",
        ENTRY_PENDING: "warning",
        ENTRY_ATTEMPTED: "warning",
        ATTEMPTS_FAILED: "bad",
        MISSED_WINDOW: "warning",
        OPEN: "good",
        EXIT_PENDING: "warning",
        CLOSED: "muted",
        CANCELLED: "bad",
        FAILED: "bad",
        DEV_TEST: "info"
    },
    trade: {
        ARMED: "info",
        ENTRY_PENDING: "warning",
        ENTRY_ATTEMPTED: "warning",
        OPEN: "good",
        EXIT_PENDING: "warning",
        CLOSED: "muted",
        CANCELLED: "bad",
        FAILED: "bad"
    },
    orderAttempt: {
        PLANNED: "neutral",
        CREATED: "neutral",
        SUBMITTED: "info",
        ACKNOWLEDGED: "info",
        FILLED: "good",
        CANCELLED: "bad",
        REJECTED: "bad",
        FAILED: "bad",
        EXPIRED: "warning"
    },
    connection: {
        NOT_CONNECTED: "muted",
        CONNECTED: "good",
        INVALID_CREDENTIALS: "bad",
        ERROR: "warning",
        UNSUPPORTED: "muted"
    }
};

function getRelativeFormatter() {
    return new Intl.RelativeTimeFormat(getLocale(), { numeric: "auto" });
}

function escapeHtml(value) {
    if (value === null || value === undefined) {
        return "";
    }
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#39;");
}

function translate(kind, value) {
    if (value === null || value === undefined) {
        return "—";
    }
    const labels = buildStatusLabels();
    return labels[kind]?.[value] ?? String(value);
}

export function formatTimeMs(value) {
    if (!value) return "—";
    const d = new Date(value);
    const hh = String(d.getHours()).padStart(2, "0");
    const mm = String(d.getMinutes()).padStart(2, "0");
    const ss = String(d.getSeconds()).padStart(2, "0");
    const ms = String(d.getMilliseconds()).padStart(3, "0");
    return `${hh}:${mm}:${ss}.${ms}`;
}

export function formatInstant(value) {
    if (!value) return "—";
    const d = new Date(value);
    const base = d.toLocaleString(getLocale(), {
        year: "numeric",
        month: "short",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit"
    });
    const ms = String(d.getMilliseconds()).padStart(3, "0");
    return `${base}.${ms}`;
}

export function formatRelative(value) {
    if (!value) {
        return "—";
    }
    const timestamp = new Date(value).getTime();
    const deltaMs = timestamp - Date.now();
    const absMs = Math.abs(deltaMs);
    const fmt = getRelativeFormatter();

    if (absMs < 60_000) {
        return fmt.format(Math.round(deltaMs / 1000), "second");
    }
    if (absMs < 3_600_000) {
        return fmt.format(Math.round(deltaMs / 60_000), "minute");
    }
    if (absMs < 86_400_000) {
        return fmt.format(Math.round(deltaMs / 3_600_000), "hour");
    }
    return fmt.format(Math.round(deltaMs / 86_400_000), "day");
}

export function formatFundingCountdown(value) {
    if (!value) {
        return t("label_funding_not_set");
    }
    const deltaMs = new Date(value).getTime() - Date.now();
    if (deltaMs >= 0) {
        return `${t("label_until_funding")} ${formatRelative(value)}`;
    }
    return `${t("label_funding")} ${formatRelative(value)}`;
}

export function formatNumber(value) {
    if (value === null || value === undefined) {
        return "—";
    }
    return new Intl.NumberFormat(getLocale()).format(value);
}

export function formatDecimal(value, digits = 4) {
    if (value === null || value === undefined || value === "") {
        return "—";
    }
    return new Intl.NumberFormat(getLocale(), {
        minimumFractionDigits: 0,
        maximumFractionDigits: digits
    }).format(Number(value));
}

export function formatDurationMs(value) {
    if (value === null || value === undefined) {
        return "—";
    }
    const absValue = Math.abs(value);
    const sign = value < 0 ? t("unit_after") : t("unit_before");
    if (absValue < 1000) {
        return `${formatNumber(absValue)} ${t("unit_ms")} ${sign}`;
    }
    return `${formatDecimal(absValue / 1000, 2)} ${t("unit_s")} ${sign}`;
}

export function formatSignedMs(value) {
    if (value === null || value === undefined) {
        return "—";
    }
    const sign = value > 0 ? "+" : "";
    return `${sign}${formatNumber(value)} ${t("unit_ms")}`;
}

function resolveTone(kind, text) {
    const registry = badgeTones[kind] ?? {};
    return registry[text] ?? "neutral";
}

export function formatBadge(kind, text, tone = "") {
    const safeTone = tone || resolveTone(kind, text);
    const safeText = escapeHtml(translate(kind, text));
    return `<span class="badge ${kind} ${safeTone}">${safeText}</span>`;
}

export function metaRow(label, value, helper = "") {
    return `
        <div class="meta-row">
            <span class="meta-label">${escapeHtml(label)}</span>
            <strong class="meta-value">${value ?? "—"}</strong>
            ${helper ? `<span class="meta-helper">${escapeHtml(helper)}</span>` : ""}
        </div>
    `;
}

export function kv(label, value) {
    return `<div class="inline-kv"><span>${escapeHtml(label)}</span><strong>${escapeHtml(value ?? "—")}</strong></div>`;
}

export function emptyState(title, detail = "") {
    return `
        <div class="empty-state">
            <strong>${escapeHtml(title)}</strong>
            ${detail ? `<p>${escapeHtml(detail)}</p>` : ""}
        </div>
    `;
}

export function journalMarkup(entries) {
    if (!entries?.length) {
        return emptyState(t("empty_journal"), t("empty_journal_detail"));
    }
    return `
        <div class="journal-list">
            ${entries.map((entry) => `
                <article class="journal-entry">
                    <header>
                        <strong>${escapeHtml(translate("journal", entry.eventCode))}</strong>
                        <span class="muted">${formatInstant(entry.createdAt)}</span>
                    </header>
                    <p class="muted">${escapeHtml(entry.oldState ?? "—")} → ${escapeHtml(entry.newState ?? "—")}</p>
                    <p>${escapeHtml(entry.note ?? t("label_no_comment"))}</p>
                    <p class="muted">${escapeHtml(translate("actor", entry.actorType))}${entry.actorRef ? ` · ${escapeHtml(entry.actorRef)}` : ""}</p>
                </article>
            `).join("")}
        </div>
    `;
}

export function section(title, body, aside = "") {
    return `
        <section class="detail-section">
            <div class="detail-section-header">
                <h3>${escapeHtml(title)}</h3>
                ${aside}
            </div>
            ${body}
        </section>
    `;
}

export function formatConnectionBadge(status) {
    return formatBadge("connection", status ?? "NOT_CONNECTED");
}

export function pipelineStageMarkup(current) {
    const stages = ["signal", "event", "trade", "executed"];
    const currentIndex = stages.indexOf(current);
    return `
        <div class="pipeline-strip">
            ${stages.map((stage, i) => {
                const cls = stage === current ? "is-current" : i < currentIndex ? "is-done" : "";
                const label = t(`pipeline_${stage}`);
                return `<span class="pipeline-step ${cls}">${escapeHtml(label)}</span>${i < stages.length - 1 ? `<span class="pipeline-arrow">→</span>` : ""}`;
            }).join("")}
        </div>
    `;
}

export { escapeHtml, translate };
