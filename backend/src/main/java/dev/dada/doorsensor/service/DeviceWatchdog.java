package dev.dada.doorsensor.service;

import dev.dada.doorsensor.config.DeviceProperties;
import dev.dada.doorsensor.domain.Device;
import dev.dada.doorsensor.repo.DeviceRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeviceWatchdog {

    private static final Logger log = LoggerFactory.getLogger(DeviceWatchdog.class);

    private final DeviceRepository deviceRepo;
    private final DoorService doorService;
    private final DeviceProperties props;

    public DeviceWatchdog(DeviceRepository deviceRepo, DoorService doorService, DeviceProperties props) {
        this.deviceRepo = deviceRepo;
        this.doorService = doorService;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${app.device.watchdog-interval-ms}")
    public void checkStaleDevices() {
        Instant threshold = Instant.now().minusSeconds(props.offlineAfterSeconds());
        List<Device> stale = deviceRepo.findStale(threshold);
        for (Device device : stale) {
            log.debug("Watchdog: {} khong gui tin tu {}", device.getId(), device.getLastSeen());
            doorService.markOfflineFromWatchdog(device.getId());
        }
    }
}
