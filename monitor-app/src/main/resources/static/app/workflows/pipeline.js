import { escapeHtml, section } from "../shared.js";
import { t } from "../../i18n.js";

export function buildDeleteCandidateSection(candidate, label = null) {
    const buttonLabel = label ?? t("pipeline_delete_button");
    return section(
        t("pipeline_cleanup_title"),
        `
            <div class="action-card danger-zone">
                <p class="helper-text">${t("pipeline_cleanup_detail")}</p>
                <form class="drawer-form" data-action="delete-candidate" data-id="${candidate.id}">
                    <label class="field">
                        <span>${t("pipeline_delete_note")}</span>
                        <textarea name="deleteNote" placeholder="${t("pipeline_delete_note_placeholder")}">operator cleanup</textarea>
                    </label>
                    <div class="actions">
                        <button class="button danger" type="submit">${escapeHtml(buttonLabel)}</button>
                    </div>
                </form>
            </div>
        `
    );
}
