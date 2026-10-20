package dev.dada.doorsensor.env;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Mot lan vuot nguong hoac tro lai binh thuong (V8, env_alert_event). */
@Entity
@Table(name = "env_alert_event")
public class EnvAlertEvent {

    public enum Kind { RAISED, CLEARED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private EnvAlertEvaluator.Metric metric;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private EnvAlertEvaluator.Direction direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Kind kind;

    @Column(nullable = false)
    private double value;

    @Column
    private Double threshold;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected EnvAlertEvent() {
    }

    public EnvAlertEvent(EnvAlertEvaluator.Transition t) {
        this.deviceId = t.deviceId();
        this.metric = t.metric();
        this.direction = t.direction();
        this.kind = t.raised() ? Kind.RAISED : Kind.CLEARED;
        // Luc het ghi gia tri dinh (cao nhat hoac thap nhat) cua ca dot: co ich hon so luc ve.
        this.value = t.raised() ? t.value() : t.peak();
        this.threshold = t.threshold();
        this.occurredAt = t.at();
    }

    public Long getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public EnvAlertEvaluator.Metric getMetric() { return metric; }
    public EnvAlertEvaluator.Direction getDirection() { return direction; }
    public Kind getKind() { return kind; }
    public double getValue() { return value; }
    public Double getThreshold() { return threshold; }
    public Instant getOccurredAt() { return occurredAt; }
}
