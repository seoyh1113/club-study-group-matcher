import java.util.ArrayList;
import java.util.List;

/**
 * 한 사람의 주간 가능 시간표.
 * 월~일, 09:00~23:00을 30분 단위 칸(slot)으로 나눠 true/false로 저장한다.
 */
public class Availability {
    public static final String[] DAYS = {"월", "화", "수", "목", "금", "토", "일"};
    public static final int START_MIN = 9 * 60;   // 09:00
    public static final int END_MIN = 23 * 60;    // 23:00
    public static final int SLOT_MIN = 30;
    public static final int SLOTS = (END_MIN - START_MIN) / SLOT_MIN; // 28칸

    private final boolean[][] slots = new boolean[DAYS.length][SLOTS];

    /** day 요일 startSlot부터 length칸이 모두 가능한지 */
    public boolean isFree(int day, int startSlot, int length) {
        if (startSlot < 0 || startSlot + length > SLOTS) return false;
        for (int s = startSlot; s < startSlot + length; s++) {
            if (!slots[day][s]) return false;
        }
        return true;
    }

    /** 스터디 시간(TimeWindow) 전체가 가능한지 */
    public boolean isFree(TimeWindow w) {
        return isFree(w.getDay(), w.getStartSlot(), w.getLength());
    }

    private void setFree(int day, int startSlot, int endSlotExclusive) {
        for (int s = startSlot; s < endSlotExclusive; s++) slots[day][s] = true;
    }

    public boolean isEmpty() {
        for (boolean[] d : slots) for (boolean b : d) if (b) return false;
        return true;
    }

    /**
     * "월 19:00-21:00, 수 18:30-20:00" 형식 파싱.
     * 구분자는 쉼표(,) 또는 세미콜론(;). 형식이 틀리면 IllegalArgumentException.
     */
    public static Availability parse(String text) {
        Availability a = new Availability();
        if (text == null || text.trim().isEmpty()) return a;
        for (String part : text.split("[,;]")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            String[] dayAndTime = part.split("\\s+");
            if (dayAndTime.length != 2) {
                throw new IllegalArgumentException("형식 오류: \"" + part + "\" (예: 월 19:00-21:00)");
            }
            int day = dayIndex(dayAndTime[0]);
            if (day < 0) throw new IllegalArgumentException("요일 오류: \"" + dayAndTime[0] + "\" (월~일)");
            String[] range = dayAndTime[1].split("-");
            if (range.length != 2) {
                throw new IllegalArgumentException("시간 범위 오류: \"" + dayAndTime[1] + "\" (예: 19:00-21:00)");
            }
            int start = toMinutes(range[0]);
            int end = toMinutes(range[1]);
            if (start >= end) throw new IllegalArgumentException("시작 시간이 끝 시간보다 늦거나 같음: " + part);
            if (start < START_MIN || end > END_MIN) {
                throw new IllegalArgumentException("가능 범위(09:00~23:00)를 벗어남: " + part);
            }
            a.setFree(day, (start - START_MIN) / SLOT_MIN, (end - START_MIN) / SLOT_MIN);
        }
        return a;
    }

    private static int dayIndex(String d) {
        d = d.replace("요일", "");
        for (int i = 0; i < DAYS.length; i++) if (DAYS[i].equals(d)) return i;
        return -1;
    }

    private static int toMinutes(String hhmm) {
        String[] p = hhmm.trim().split(":");
        try {
            int h = Integer.parseInt(p[0]);
            int m = p.length > 1 ? Integer.parseInt(p[1]) : 0;
            if (m != 0 && m != 30) throw new IllegalArgumentException("30분 단위로 입력: " + hhmm);
            return h * 60 + m;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("시간 숫자 오류: " + hhmm);
        }
    }

    public static String slotToTime(int slot) {
        int min = START_MIN + slot * SLOT_MIN;
        return String.format("%02d:%02d", min / 60, min % 60);
    }

    /** 연속된 칸을 묶어서 "월 19:00-21:00; 수 18:30-20:00" 형태로 */
    public String toText() {
        List<String> parts = new ArrayList<>();
        for (int d = 0; d < DAYS.length; d++) {
            int s = 0;
            while (s < SLOTS) {
                if (!slots[d][s]) { s++; continue; }
                int e = s;
                while (e < SLOTS && slots[d][e]) e++;
                parts.add(DAYS[d] + " " + slotToTime(s) + "-" + slotToTime(e));
                s = e;
            }
        }
        return String.join("; ", parts);
    }

    @Override
    public String toString() {
        String t = toText();
        return t.isEmpty() ? "(없음)" : t;
    }
}
