/** 스터디 시간 하나 (요일 + 시작 칸 + 길이) */
public class TimeWindow {
    private final int day;
    private final int startSlot;
    private final int length;

    public TimeWindow(int day, int startSlot, int length) {
        this.day = day;
        this.startSlot = startSlot;
        this.length = length;
    }

    public int getDay() { return day; }
    public int getStartSlot() { return startSlot; }
    public int getLength() { return length; }

    @Override
    public String toString() {
        return Availability.DAYS[day] + " " + Availability.slotToTime(startSlot)
                + "-" + Availability.slotToTime(startSlot + length);
    }
}
