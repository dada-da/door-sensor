package dev.dada.doorsensor.alarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dada.doorsensor.config.NotifyProperties;
import dev.dada.doorsensor.notify.TelegramClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Doc lenh Telegram. Khong goi mang: TelegramClient duoc thay bang ban ghi lai.
 */
class TelegramAlarmControlTest {

    private static final String OUR_CHAT = "123456";
    private static final Instant NOW = Instant.parse("2026-10-14T05:00:00Z");

    static final class RecordingTelegram extends TelegramClient {
        final List<String> replies = new ArrayList<>();
        final List<String> answers = new ArrayList<>();

        RecordingTelegram(NotifyProperties p) {
            super(p, RestClient.builder());
        }

        @Override public void reply(String html) { replies.add(html); }

        @Override public void answerCallback(String id, String text) { answers.add(id + ":" + text); }

        @Override public boolean ready() { return true; }
    }

    final ObjectMapper om = new ObjectMapper();
    int stops;
    AlarmService.CommandResult stopResult = AlarmService.CommandResult.OK;

    private final NotifyProperties notify = new NotifyProperties(true,
            new NotifyProperties.Telegram("token", OUR_CHAT), null, null, null, null);
    private final RecordingTelegram telegram = new RecordingTelegram(notify);

    private final TelegramAlarmControl control = new TelegramAlarmControl(notify, telegram,
            new TelegramAlarmControl.Actions() {
                @Override public AlarmService.CommandResult stopFromTelegram() {
                    stops++;
                    return stopResult;
                }

                @Override public AlarmStatusDto status() {
                    return new AlarmStatusDto(true, true, "ARMED_SIREN_OFFLINE", "siren-01",
                            "OFFLINE", null, false, null);
                }
            },
            om, Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")), RestClient.builder(), true);

    private JsonNode message(String chat, String text, Instant date) throws Exception {
        return om.readTree("""
                {"update_id":1,"message":{"date":%d,"chat":{"id":%s},"text":"%s"}}"""
                .formatted(date.getEpochSecond(), chat, text));
    }

    private JsonNode button(String chat, String data) throws Exception {
        return om.readTree("""
                {"update_id":2,"callback_query":{"id":"cb1","data":"%s","message":{"date":1,"chat":{"id":%s}}}}"""
                .formatted(data, chat));
    }

    @Test
    @DisplayName("Bam nut Tat coi: tat coi, tat vong xoay, bao lai trong chat")
    void nutTatCoi() throws Exception {
        assertEquals(TelegramAlarmControl.Outcome.STOPPED, control.handleUpdate(button(OUR_CHAT, "siren_stop")));
        assertEquals(1, stops);
        assertEquals(List.of("cb1:Đã tắt còi"), telegram.answers);
        assertEquals(1, telegram.replies.size());
    }

    @Test
    @DisplayName("Nut bam tren tin bao dong cu van dung duoc")
    void nutCuVanDung() throws Exception {
        // message.date = 1 (nam 1970) nhung van tat: nguoi dung co the bam muon.
        assertEquals(TelegramAlarmControl.Outcome.STOPPED, control.handleUpdate(button(OUR_CHAT, "siren_stop")));
    }

    @Test
    @DisplayName("Lenh /tatcoi, ke ca dang /tatcoi@ten_bot")
    void lenhTatCoi() throws Exception {
        assertEquals(TelegramAlarmControl.Outcome.STOPPED, control.handleUpdate(message(OUR_CHAT, "/tatcoi", NOW)));
        assertEquals(TelegramAlarmControl.Outcome.STOPPED,
                control.handleUpdate(message(OUR_CHAT, "/TatCoi@door_bot", NOW)));
        assertEquals(2, stops);
    }

    @Test
    @DisplayName("Chat la: khong tat coi, khong tra loi gi")
    void chatLa() throws Exception {
        assertEquals(TelegramAlarmControl.Outcome.IGNORED_FOREIGN_CHAT,
                control.handleUpdate(message("999", "/tatcoi", NOW)));
        assertEquals(TelegramAlarmControl.Outcome.IGNORED_FOREIGN_CHAT,
                control.handleUpdate(button("999", "siren_stop")));
        assertEquals(0, stops);
        assertTrue(telegram.replies.isEmpty() && telegram.answers.isEmpty());
    }

    @Test
    @DisplayName("Lenh chu cu hon 2 phut (Pi vua khoi dong lai): bo qua")
    void lenhCu() throws Exception {
        assertEquals(TelegramAlarmControl.Outcome.IGNORED_STALE,
                control.handleUpdate(message(OUR_CHAT, "/tatcoi", NOW.minusSeconds(3600))));
        assertEquals(0, stops);
    }

    @Test
    @DisplayName("Gui STOP that bai: bao ro coi van tu tat theo han")
    void tatThatBai() throws Exception {
        stopResult = AlarmService.CommandResult.SEND_FAILED;
        assertEquals(TelegramAlarmControl.Outcome.STOP_FAILED, control.handleUpdate(message(OUR_CHAT, "/tatcoi", NOW)));
        assertTrue(telegram.replies.get(0).contains("tự tắt"));
    }

    @Test
    @DisplayName("/trangthai noi ro truong hop nguy hiem: bao ve ma coi mat lien lac")
    void trangThai() throws Exception {
        assertEquals(TelegramAlarmControl.Outcome.STATUS, control.handleUpdate(message(OUR_CHAT, "/trangthai", NOW)));
        assertTrue(telegram.replies.get(0).contains("MẤT LIÊN LẠC"));
        assertEquals(0, stops);
    }

    @Test
    @DisplayName("Tin thuong khong phai lenh: bo qua")
    void tinThuong() throws Exception {
        assertEquals(TelegramAlarmControl.Outcome.IGNORED_OTHER, control.handleUpdate(message(OUR_CHAT, "chao bot", NOW)));
        assertEquals(TelegramAlarmControl.Outcome.IGNORED_OTHER, control.handleUpdate(button(OUR_CHAT, "khac")));
        assertEquals(0, stops);
    }
}
