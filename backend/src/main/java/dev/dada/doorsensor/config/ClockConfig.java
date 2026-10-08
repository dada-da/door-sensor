package dev.dada.doorsensor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Dong ho he thong, tiem vao cac service can biet thoi gian.
 *
 * <p>Tach ra thanh bean de kiem thu thay bang {@code Clock.fixed(...)}, nho vay
 * cac truong hop kiem thu lap lai duoc va khong phu thuoc gio chay that.
 *
 * <p>Mui gio ghi tuong minh, khong dung {@code Clock.systemDefaultZone()}. JVM,
 * Raspberry Pi va container PostgreSQL co the dang o ba mui gio khac nhau, va
 * hau qua la bao dong keu lech bay tieng ma khong co dau hieu gi de lan ra.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
