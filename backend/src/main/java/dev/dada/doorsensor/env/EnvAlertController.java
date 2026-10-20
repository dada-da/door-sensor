package dev.dada.doorsensor.env;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Nguong canh bao nhiet do, do am.
 *
 * <pre>
 * GET /api/env/alerts           {"rule":{...},"active":[...],"tempHysteresis":0.5,"rhHysteresis":3.0}
 * PUT /api/env/alerts           {"enabled":true,"tempHigh":35,"tempLow":12,"rhHigh":85,"rhLow":null,"holdMinutes":5}
 * GET /api/env/alerts/events    20 lan vuot nguong, het gan nhat
 * </pre>
 *
 * Nguong null la khong canh bao chieu do.
 */
@RestController
@RequestMapping("/api/env/alerts")
public class EnvAlertController {

    private final EnvAlertRuleRepository rules;
    private final EnvAlertEventRepository events;
    private final EnvAlertService service;

    public EnvAlertController(EnvAlertRuleRepository rules, EnvAlertEventRepository events,
                              EnvAlertService service) {
        this.rules = rules;
        this.events = events;
        this.service = service;
    }

    public record RuleDto(
            boolean enabled,
            @DecimalMin("-40") @DecimalMax("85") Double tempHigh,
            @DecimalMin("-40") @DecimalMax("85") Double tempLow,
            @DecimalMin("0") @DecimalMax("100") Double rhHigh,
            @DecimalMin("0") @DecimalMax("100") Double rhLow,
            @Min(0) @Max(120) int holdMinutes) {

        static RuleDto from(EnvAlertRule r) {
            return new RuleDto(r.isEnabled(), r.getTempHigh(), r.getTempLow(), r.getRhHigh(), r.getRhLow(),
                    r.getHoldMinutes());
        }
    }

    public record ActiveDto(String deviceId, String metric, String direction, Instant since,
                            double value, Double threshold, double peak) {

        static ActiveDto from(EnvAlertEvaluator.Active a) {
            return new ActiveDto(a.deviceId(), a.metric().name(), a.direction().name(), a.since(),
                    a.value(), a.threshold(), a.peak());
        }
    }

    public record AlertsDto(RuleDto rule, List<ActiveDto> active, double tempHysteresis, double rhHysteresis) {
    }

    public record EventDto(long id, String deviceId, String metric, String direction, String kind,
                           double value, Double threshold, Instant occurredAt) {

        static EventDto from(EnvAlertEvent e) {
            return new EventDto(e.getId(), e.getDeviceId(), e.getMetric().name(), e.getDirection().name(),
                    e.getKind().name(), e.getValue(), e.getThreshold(), e.getOccurredAt());
        }
    }

    @GetMapping
    public AlertsDto get() {
        return new AlertsDto(RuleDto.from(rules.current()),
                service.active().stream().map(ActiveDto::from).toList(),
                EnvAlertEvaluator.TEMP_HYSTERESIS, EnvAlertEvaluator.RH_HYSTERESIS);
    }

    @PutMapping
    @Transactional
    public AlertsDto update(@Valid @RequestBody RuleDto body) {
        String problem = problem(body);
        if (problem != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, problem);
        }
        EnvAlertRule r = rules.current();
        r.setEnabled(body.enabled());
        r.setTempHigh(body.tempHigh());
        r.setTempLow(body.tempLow());
        r.setRhHigh(body.rhHigh());
        r.setRhLow(body.rhLow());
        r.setHoldMinutes(body.holdMinutes());
        r.setUpdatedAt(Instant.now());
        rules.save(r);
        service.onRuleChanged(r);
        return get();
    }

    @GetMapping("/events")
    public List<EventDto> events(@RequestParam(defaultValue = "20") int limit) {
        int n = Math.min(Math.max(limit, 1), 200);
        return events.findAllByOrderByOccurredAtDesc(PageRequest.of(0, n)).stream()
                .map(EventDto::from)
                .toList();
    }

    /**
     * Nguong duoi phai thap hon nguong tren it nhat hai lan khoang tre: neu khong,
     * mot gia tri vua "het cao" da nam duoi nguong thap, hai canh bao lat nhau.
     */
    static String problem(RuleDto b) {
        if (b.tempHigh() != null && b.tempLow() != null
                && b.tempLow() > b.tempHigh() - 2 * EnvAlertEvaluator.TEMP_HYSTERESIS) {
            return "Ngưỡng nhiệt độ thấp phải nhỏ hơn ngưỡng cao ít nhất "
                    + 2 * EnvAlertEvaluator.TEMP_HYSTERESIS + " °C";
        }
        if (b.rhHigh() != null && b.rhLow() != null
                && b.rhLow() > b.rhHigh() - 2 * EnvAlertEvaluator.RH_HYSTERESIS) {
            return "Ngưỡng độ ẩm thấp phải nhỏ hơn ngưỡng cao ít nhất "
                    + (int) (2 * EnvAlertEvaluator.RH_HYSTERESIS) + " %";
        }
        return null;
    }
}
