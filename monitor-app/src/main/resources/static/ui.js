export function formatInstant(value) {
    if (!value) {
        return "—";
    }
    return new Date(value).toLocaleString("en-GB", {
        year: "numeric",
        month: "short",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit"
    });
}

export function formatNumber(value) {
    if (value === null || value === undefined) {
        return "—";
    }
    return new Intl.NumberFormat("en-US").format(value);
}

export function formatBadge(type, text, tone = "") {
    return `<span class="badge ${type} ${tone}">${text}</span>`;
}

export function metaRow(label, value) {
    return `
        <div class="meta-row">
            <span class="eyebrow">${label}</span>
            <strong>${value ?? "—"}</strong>
        </div>
    `;
}

export function emptyState(text) {
    return `<div class="empty-state">${text}</div>`;
}

export function journalMarkup(entries) {
    if (!entries?.length) {
        return emptyState("No journal entries yet.");
    }
    return `
        <div class="journal-list">
            ${entries.map((entry) => `
                <article class="journal-entry">
                    <strong>${entry.eventCode}</strong>
                    <p class="muted">${entry.oldState ?? "—"} → ${entry.newState ?? "—"}</p>
                    <p>${entry.note ?? "No note"}</p>
                    <p class="muted">${entry.actorType}${entry.actorRef ? ` · ${entry.actorRef}` : ""} · ${formatInstant(entry.createdAt)}</p>
                </article>
            `).join("")}
        </div>
    `;
}
