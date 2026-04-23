import { escapeHtml, section } from "../shared.js";

export function buildDeleteCandidateSection(candidate, label = "Delete candidate") {
    return section(
        "Очистка pipeline",
        `
            <div class="action-card danger-zone">
                <p class="helper-text">Удаление candidate чистит связанный Funding Event, Prepared Trade и связанные journal entries. Source signal при этом не исполняется и не уходит в engine.</p>
                <form class="drawer-form" data-action="delete-candidate" data-id="${candidate.id}">
                    <label class="field">
                        <span>Delete note</span>
                        <textarea name="deleteNote" placeholder="Например: false signal / duplicate / operator cleanup">operator cleanup</textarea>
                    </label>
                    <div class="actions">
                        <button class="button danger" type="submit">${escapeHtml(label)}</button>
                    </div>
                </form>
            </div>
        `
    );
}
