import { emptyState, venueCard, wireOpenButtons } from "../shared.js";

export function venuesListMarkup(venues = []) {
    return venues.length
        ? venues.map(venueCard).join("")
        : emptyState("Venue Access пуст.", "Проверь enabled venues и registry sync.");
}

export function renderVenues({ nodes, venues, onOpenVenue }) {
    nodes.venuesList.innerHTML = venuesListMarkup(venues);
    wireOpenButtons(nodes.venuesList, "[data-open-venue]", onOpenVenue);
}
