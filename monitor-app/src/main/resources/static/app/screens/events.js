import { emptyState, eventCard, wireOpenButtons } from "../shared.js";
import { t } from "../../i18n.js";

export function eventsListMarkup(page = {}) {
    const events = page?.content ?? [];
    return events.length
        ? events.map(eventCard).join("")
        : emptyState(t("empty_events"), t("empty_events_detail"));
}

export function renderFundingEvents({ nodes, page, onOpenEvent }) {
    nodes.eventsList.innerHTML = eventsListMarkup(page);
    wireOpenButtons(nodes.eventsList, "[data-open-event]", onOpenEvent);
}
