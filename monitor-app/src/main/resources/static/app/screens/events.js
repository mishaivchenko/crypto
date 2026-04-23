import { emptyState, eventCard, wireOpenButtons } from "../shared.js";

export function eventsListMarkup(page = {}) {
    const events = page?.content ?? [];
    return events.length
        ? events.map(eventCard).join("")
        : emptyState("Funding Events пока нет.", "Approve signal, чтобы создать первое событие.");
}

export function renderFundingEvents({ nodes, page, onOpenEvent }) {
    nodes.eventsList.innerHTML = eventsListMarkup(page);
    wireOpenButtons(nodes.eventsList, "[data-open-event]", onOpenEvent);
}
