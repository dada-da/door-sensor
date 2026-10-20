package dev.dada.doorsensor.env;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Nguong canh bao nhiet do, do am. Mot dong duy nhat, id = 1 (V8). */
@Entity
@Table(name = "env_alert_rule")
public class EnvAlertRule {

    @Id
    private Short id = 1;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "temp_high")
    private Double tempHigh;

    @Column(name = "temp_low")
    private Double tempLow;

    @Column(name = "rh_high")
    private Double rhHigh;

    @Column(name = "rh_low")
    private Double rhLow;

    @Column(name = "hold_minutes", nullable = false)
    private int holdMinutes = 5;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public EnvAlertEvaluator.Limits limits(EnvAlertEvaluator.Metric m) {
        return m == EnvAlertEvaluator.Metric.TEMP
                ? new EnvAlertEvaluator.Limits(tempHigh, tempLow)
                : new EnvAlertEvaluator.Limits(rhHigh, rhLow);
    }

    public Short getId() { return id; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Double getTempHigh() { return tempHigh; }
    public void setTempHigh(Double tempHigh) { this.tempHigh = tempHigh; }

    public Double getTempLow() { return tempLow; }
    public void setTempLow(Double tempLow) { this.tempLow = tempLow; }

    public Double getRhHigh() { return rhHigh; }
    public void setRhHigh(Double rhHigh) { this.rhHigh = rhHigh; }

    public Double getRhLow() { return rhLow; }
    public void setRhLow(Double rhLow) { this.rhLow = rhLow; }

    public int getHoldMinutes() { return holdMinutes; }
    public void setHoldMinutes(int holdMinutes) { this.holdMinutes = holdMinutes; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
