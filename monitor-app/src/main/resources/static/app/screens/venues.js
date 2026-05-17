import { emptyState, venueCard, wireOpenButtons } from "../shared.js";
import { t } from "../../i18n.js";

export function venuesListMarkup(venues = []) {
    return venues.length
        ? venues.map(venueCard).join("")
        : emptyState(t("empty_venues"), t("empty_venues_detail"));
}

export function renderVenues({ nodes, venues, onOpenVenue }) {
    nodes.venuesList.innerHTML = venuesListMarkup(venues);
    wireOpenButtons(nodes.venuesList, "[data-open-venue]", onOpenVenue);
}
