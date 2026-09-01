package co.medina.portfolio.clientsservice.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Role {
    ADMIN,
    CLIENT;

    @JsonValue
    public String toWireValue() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static Role fromWireValue(String value) {
        return Role.valueOf(value.toUpperCase());
    }
}
