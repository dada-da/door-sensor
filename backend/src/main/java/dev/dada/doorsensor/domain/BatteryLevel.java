package dev.dada.doorsensor.domain;

/**
 * Doi dien ap pin Li-ion sang phan tram con lai.
 *
 * <p><b>Vi sao khong noi suy thang 3,3 V - 4,2 V.</b> Duong xa cua pin Li-ion
 * khong thang: tu 4,2 xuong khoang 3,95 V ap tut nhanh (mat khoang 30 %), sau do
 * gan nhu nam ngang quanh 3,7 - 3,85 V (phan lon dung luong o day), roi lai rot
 * nhanh duoi 3,6 V. Noi suy thang bao 4,05 V la 83 % trong khi thuc te con khoang
 * 80 %, va bao 3,75 V la 50 % trong khi chi con khoang 25 %: pin trong co ve con
 * nhieu dung luc no sap het, la luc can biet dung nhat.
 *
 * <p>Bang lay theo dien ap nghi (dong rat nho) cua cell NCR18650 o nhiet do
 * phong. door-02 do pin truoc khi bat Wi-Fi, dong khoang 26 mA, nen so do gan voi
 * dien ap nghi. 0 % dat o 3300 mV vi firmware ngat mem o muc nay (tin lowbatt),
 * khong phai muc can that su cua cell.
 *
 * <p>Day la uoc luong, sai so vai phan tram: nhiet do, tuoi pin va dong tai deu
 * lam lech duong. Dung de nhin nhanh, khong dung de tinh tuoi tho.
 */
public final class BatteryLevel {

    /** Cap (mV, %) giam dan. Noi suy thang giua hai diem ke nhau. */
    private static final int[][] CURVE = {
            {4200, 100},
            {4150, 95},
            {4110, 90},
            {4080, 85},
            {4020, 80},
            {3980, 75},
            {3950, 70},
            {3910, 65},
            {3870, 60},
            {3850, 55},
            {3840, 50},
            {3820, 45},
            {3800, 40},
            {3790, 35},
            {3770, 30},
            {3750, 25},
            {3730, 20},
            {3710, 15},
            {3690, 10},
            {3610, 5},
            {3450, 2},
            {3300, 0},
    };

    private BatteryLevel() {
    }

    /** Phan tram 0-100, hoac null neu khong co so do (thiet bi cam USB). */
    public static Integer percent(Integer mv) {
        if (mv == null) {
            return null;
        }
        if (mv >= CURVE[0][0]) {
            return 100;
        }
        for (int i = 1; i < CURVE.length; i++) {
            int[] hi = CURVE[i - 1];
            int[] lo = CURVE[i];
            if (mv >= lo[0]) {
                double f = (mv - lo[0]) / (double) (hi[0] - lo[0]);
                return (int) Math.round(lo[1] + f * (hi[1] - lo[1]));
            }
        }
        return 0;
    }
}
