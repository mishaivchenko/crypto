const statusLabels = {
    candidate: {
        NEW: "Новый signal",
        NORMALIZED: "Готов к review",
        FAILED: "Ошибка normalization",
        REJECTED: "Rejected",
        EVENT_CREATED: "Event created",
        DELETED: "Удалён"
    },
    event: {
        DISCOVERED: "Discovered",
        ARMED: "Armed",
        EXPIRED: "Expired",
        CANCELLED: "Cancelled"
    },
    trade: {
        ARMED: "Prepared",
        ENTRY_PENDING: "Entry pending",
        ENTRY_ATTEMPTED: "Entry attempted",
        OPEN: "Open",
        EXIT_PENDING: "Exit pending",
        CLOSED: "Closed",
        CANCELLED: "Cancelled",
        FAILED: "Failed"
    },
    actor: {
        SYSTEM: "Система",
        OPERATOR: "Оператор"
    },
    connection: {
        NOT_CONNECTED: "Ключи не подключены",
        CONNECTED: "Connected",
        INVALID_CREDENTIALS: "Invalid credentials",
        ERROR: "Ошибка check",
        UNSUPPORTED: "Unsupported"
    },
    journal: {
        CANDIDATE_APPROVED: "Candidate approved",
        CANDIDATE_REJECTED: "Candidate rejected",
        CANDIDATE_DELETED: "Candidate deleted",
        FUNDING_EVENT_CREATED: "Funding Event created",
        FUNDING_EVENT_ARMED: "Funding Event armed",
        ARMED_TRADE_CREATED: "Prepared Trade created"
    }
};

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
    connection: {
        NOT_CONNECTED: "muted",
        CONNECTED: "good",
        INVALID_CREDENTIALS: "bad",
        ERROR: "warning",
        UNSUPPORTED: "muted"
    }
};

const relativeFormatter = new Intl.RelativeTimeFormat("ru-RU", { numeric: "auto" });

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
    return statusLabels[kind]?.[value] ?? String(value);
}

export function formatInstant(value) {
    if (!value) {
        return "—";
    }
    return new Date(value).toLocaleString("ru-RU", {
        year: "numeric",
        month: "short",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit"
    });
}

export function formatRelative(value) {
    if (!value) {
        return "—";
    }
    const timestamp = new Date(value).getTime();
    const deltaMs = timestamp - Date.now();
    const absMs = Math.abs(deltaMs);

    if (absMs < 60_000) {
        return relativeFormatter.format(Math.round(deltaMs / 1000), "second");
    }
    if (absMs < 3_600_000) {
        return relativeFormatter.format(Math.round(deltaMs / 60_000), "minute");
    }
    if (absMs < 86_400_000) {
        return relativeFormatter.format(Math.round(deltaMs / 3_600_000), "hour");
    }
    return relativeFormatter.format(Math.round(deltaMs / 86_400_000), "day");
}

export function formatFundingCountdown(value) {
    if (!value) {
        return "Funding time не задан";
    }
    const deltaMs = new Date(value).getTime() - Date.now();
    if (deltaMs >= 0) {
        return `до funding ${formatRelative(value)}`;
    }
    return `funding был ${formatRelative(value).replace("назад", "").trim()} назад`;
}

export function formatNumber(value) {
    if (value === null || value === undefined) {
        return "—";
    }
    return new Intl.NumberFormat("ru-RU").format(value);
}

export function formatDecimal(value, digits = 4) {
    if (value === null || value === undefined || value === "") {
        return "—";
    }
    return new Intl.NumberFormat("ru-RU", {
        minimumFractionDigits: 0,
        maximumFractionDigits: digits
    }).format(Number(value));
}

export function formatDurationMs(value) {
    if (value === null || value === undefined) {
        return "—";
    }
    const absValue = Math.abs(value);
    const sign = value < 0 ? "after" : "before";
    if (absValue < 1000) {
        return `${formatNumber(absValue)} мс ${sign}`;
    }
    return `${formatDecimal(absValue / 1000, 2)} с ${sign}`;
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
        return emptyState("Journal пока пуст.", "Записи появятся после первого действия системы или оператора.");
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
                    <p>${escapeHtml(entry.note ?? "Без комментария")}</p>
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

export { escapeHtml, translate };
