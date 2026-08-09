package co.medina.portfolio.clientsservice.client;

import co.medina.portfolio.clientsservice.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "client_phones")
@Getter
@Setter
@EqualsAndHashCode(of = "id", callSuper = false)
public class Phone extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PhoneType type;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    protected Phone() {
    }

    public Phone(UUID clientId, String number, PhoneType type, boolean primary) {
        this.clientId = clientId;
        this.number = number;
        this.type = type;
        this.primary = primary;
    }
}
