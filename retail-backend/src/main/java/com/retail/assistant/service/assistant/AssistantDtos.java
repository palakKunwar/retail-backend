package com.retail.assistant.service.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AssistantDtos {

    private AssistantDtos() {
    }

    public record AskRequest(@NotBlank @Size(max = 500) String question) {
    }

    /** A table of facts the answer is based on. */
    public record Evidence(String title, List<String> columns, List<List<String>> rows) {
    }

    /** Clickable link back into the dashboard (e.g. filter=LOW). */
    public record DrillDown(String label, String target, String filter) {
    }

    public record AskResponse(String question, String intent, String answer, List<Evidence> evidence,
                              List<DrillDown> drillDowns, List<String> followUps, String source) {
    }
}
