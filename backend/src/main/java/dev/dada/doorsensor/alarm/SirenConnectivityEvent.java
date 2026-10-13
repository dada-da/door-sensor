package dev.dada.doorsensor.alarm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Lich su len xuong mang cua node coi. Tach khoi connectivity_event vi bang do
 * co khoa ngoai toi device, ma node coi khong phai cua. So lieu cho cot "Node
 * coi" cua bang 5.2.
 */
@Entity
@Table(name = "siren_connectivity_event")
public class SirenConnectivityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "siren_id", nullable = false, length = 64)
    private String sirenId;

    @Column(name = "online", nullable = false)
    private boolean online;

    @Column(name = "source", nullable = false, length = 16)
    private String source;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected SirenConnectivityEvent() {
    }

    public SirenConnectivityEvent(String sirenId, boolean online, SirenHealth.Source source, Instant receivedAt) {
        this.sirenId = sirenId;
        this.online = online;
        this.source = source.name();
        this.receivedAt = receivedAt;
    }

    public Long getId() { return id; }
    public String getSirenId() { return sirenId; }
    public boolean isOnline() { return online; }
    public String getSource() { return source; }
    public Instant getReceivedAt() { return receivedAt; }
}
