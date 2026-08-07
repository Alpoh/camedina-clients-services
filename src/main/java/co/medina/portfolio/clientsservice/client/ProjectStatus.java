package co.medina.portfolio.clientsservice.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ProjectStatus {
    PLANNING,
    IN_PROGRESS,
    BLOCKED,
    REVIEW,
    DONE;

    @JsonValue
    public String toWireValue() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static ProjectStatus fromWireValue(String value) {
        return ProjectStatus.valueOf(value.toUpperCase());
    }
}
