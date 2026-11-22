import javax.swing.JComponent;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * 주간 캘린더 (TimeTree 주간 보기 느낌).
 * - 조 모임 시간을 조 색의 둥근 일정 블록으로 표시, 같은 시간에 겹치면 나란히 배치
 * - 부원을 선택하면: 가능 시간은 연두색 배경, 현재 조는 파란 테두리, 옮겨갈 수 있는 조는 초록 테두리,
 *   나머지 조는 흐리게 표시 (아이콘 없이 테두리만으로 구분)
 * - 일정 블록을 클릭하면 onGroupClick으로 알림 (왼쪽 조 카드로 이동)
 */
@SuppressWarnings("serial")
public class TimetableView extends JComponent {
    private static final int HEADER = 8;                   // 위쪽 여백 (요일 머리글은 따로 고정)
    private static final int DAY_HEADER_H = 34;
    private static final int GUTTER = 48;
    private static final int MIN_SLOT_H = 12;              // 30분 칸의 최소 높이 (창이 아주 작을 때)

    private MatchResult result;
    private Member selected;
    private Consumer<Group> onGroupClick = g -> { };

    /** 마지막으로 그린 일정 블록 위치 (클릭·툴팁 찾기용) */
    private final List<Box> boxes = new ArrayList<>();

    private static class Box {
        final Group group;
        final Rectangle rect;
        Box(Group group, Rectangle rect) { this.group = group; this.rect = rect; }
    }

    public TimetableView() {
        setOpaque(true);
        setToolTipText("");   // 툴팁 사용 등록
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                Box b = boxAt(e.getX(), e.getY());
                if (b != null) onGroupClick.accept(b.group);
            }
            @Override public void mouseMoved(MouseEvent e) {
                setCursor(boxAt(e.getX(), e.getY()) != null
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void setOnGroupClick(Consumer<Group> listener) { this.onGroupClick = listener; }

    public void setData(MatchResult result, Member selected) {
        this.result = result;
        this.selected = selected;
        repaint();
    }

    /** 기본 크기는 작게 잡고, 실제로는 주어진 높이에 맞춰 30분 칸 높이를 늘리거나 줄인다 (스크롤 없음) */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(GUTTER + 7 * 70, HEADER + Availability.SLOTS * MIN_SLOT_H + 6);
    }

    /** 30분 한 칸의 높이: 화면 높이 ÷ 칸 수 */
    private double slotH() {
        return Math.max(MIN_SLOT_H, (getHeight() - HEADER - 6) / (double) Availability.SLOTS);
    }

    private Box boxAt(int x, int y) {
        for (int i = boxes.size() - 1; i >= 0; i--) if (boxes.get(i).rect.contains(x, y)) return boxes.get(i);
        return null;
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        Box b = boxAt(e.getX(), e.getY());
        if (b == null) return null;
        Group g = b.group;
        StringBuilder sb = new StringBuilder("<html><b>").append(g.getName()).append("</b> &nbsp;")
                .append(g.getTime()).append("<br>운영진 ").append(g.getLeader().getName())
                .append(" · ").append(g.size()).append("명<br><font color='#6B7280'>");
        List<String> names = new ArrayList<>();
        for (Member m : g.getMembers()) names.add(m.getName());
        sb.append(names.isEmpty() ? "부원 없음" : String.join(", ", names));
        return sb.append("</font></html>").toString();
    }

    // ===================== 그리기 =====================

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.smooth(g);
        int w = getWidth();
        double colW = (w - GUTTER) / 7.0;

        g2.setColor(Theme.SURFACE);
        g2.fillRect(0, 0, w, getHeight());

        paintGrid(g2, w, colW);
        paintAvailability(g2, colW);
        paintEvents(g2, colW);
        g2.dispose();
    }

    private int y(int slot) { return HEADER + (int) Math.round(slot * slotH()); }

    private int x(int day, double colW) { return GUTTER + (int) Math.round(day * colW); }

    /** 시간표 위에 붙는 요일 머리글 */
    public JComponent createDayHeader() {
        return new JComponent() {
            @Override public Dimension getPreferredSize() { return new Dimension(10, DAY_HEADER_H); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.smooth(g);
                int w = getWidth();
                double colW = (w - GUTTER) / 7.0;
                g2.setColor(Theme.SURFACE);
                g2.fillRect(0, 0, w, DAY_HEADER_H);
                g2.setFont(Theme.bold(13f));
                FontMetrics fm = g2.getFontMetrics();
                for (int d = 0; d < 7; d++) {
                    String name = Availability.DAYS[d];
                    g2.setColor(d == 5 ? Theme.SATURDAY : d == 6 ? Theme.SUNDAY : Theme.TEXT);
                    int cx = GUTTER + (int) Math.round(d * colW + colW / 2);
                    g2.drawString(name, cx - fm.stringWidth(name) / 2, (DAY_HEADER_H + fm.getAscent()) / 2 - 2);
                }
                g2.setColor(Theme.LINE);
                g2.drawLine(0, DAY_HEADER_H - 1, w, DAY_HEADER_H - 1);
                g2.dispose();
            }
        };
    }

    private void paintGrid(Graphics2D g2, int w, double colW) {
        g2.setFont(Theme.font(11f));
        FontMetrics fm = g2.getFontMetrics();
        for (int s = 0; s <= Availability.SLOTS; s++) {
            int yy = y(s);
            if (s % 2 == 0) {
                g2.setColor(Theme.LINE);
                g2.drawLine(GUTTER, yy, w, yy);
                if (s < Availability.SLOTS) {
                    int hour = (Availability.START_MIN / 60) + s / 2;
                    String label = hour + "시";
                    g2.setColor(Theme.HINT);
                    g2.drawString(label, GUTTER - 8 - fm.stringWidth(label), yy + fm.getAscent() - 2);
                }
            } else {
                g2.setColor(Theme.LINE_SOFT);
                g2.drawLine(GUTTER, yy, w, yy);
            }
        }
        g2.setColor(Theme.LINE_SOFT);
        for (int d = 0; d <= 7; d++) g2.drawLine(x(d, colW), HEADER, x(d, colW), y(Availability.SLOTS));
        // 주말 칸은 아주 연하게
        g2.setColor(new Color(0, 0, 0, 6));
        g2.fillRect(x(5, colW) + 1, HEADER, x(7, colW) - x(5, colW), y(Availability.SLOTS) - HEADER);
    }

    /** 선택한 부원의 가능 시간을 연두색으로 */
    private void paintAvailability(Graphics2D g2, double colW) {
        if (selected == null) return;
        for (int d = 0; d < 7; d++) {
            int s = 0;
            while (s < Availability.SLOTS) {
                if (!selected.getAvailability().isFree(d, s, 1)) { s++; continue; }
                int e = s;
                while (e < Availability.SLOTS && selected.getAvailability().isFree(d, e, 1)) e++;
                int x0 = x(d, colW) + 1, x1 = x(d + 1, colW) - 1;
                g2.setColor(new Color(34, 181, 115, 38));
                g2.fillRect(x0, y(s) + 1, x1 - x0, y(e) - y(s) - 1);
                g2.setColor(new Color(34, 181, 115, 150));
                g2.fillRect(x0, y(s) + 1, 2, y(e) - y(s) - 1);
                s = e;
            }
        }
    }

    private void paintEvents(Graphics2D g2, double colW) {
        boxes.clear();
        if (result == null) return;
        Group current = selected == null ? null : result.findGroupOf(selected);

        for (int d = 0; d < 7; d++) {
            List<Group> day = new ArrayList<>();
            for (Group grp : result.getGroups()) if (grp.getTime().getDay() == d) day.add(grp);
            day.sort(Comparator.comparingInt((Group grp) -> grp.getTime().getStartSlot()));

            // 겹치는 일정끼리 묶어서 칸(lane)을 나눔
            int i = 0;
            while (i < day.size()) {
                List<Group> cluster = new ArrayList<>();
                List<Integer> laneEnds = new ArrayList<>();
                List<Integer> lanes = new ArrayList<>();
                int clusterEnd = -1;
                while (i < day.size() && (cluster.isEmpty() || day.get(i).getTime().getStartSlot() < clusterEnd)) {
                    Group grp = day.get(i++);
                    int start = grp.getTime().getStartSlot(), end = start + grp.getTime().getLength();
                    int lane = 0;
                    while (lane < laneEnds.size() && laneEnds.get(lane) > start) lane++;
                    if (lane == laneEnds.size()) laneEnds.add(end); else laneEnds.set(lane, end);
                    cluster.add(grp);
                    lanes.add(lane);
                    clusterEnd = Math.max(clusterEnd, end);
                }
                double laneW = (colW - 6) / laneEnds.size();
                for (int k = 0; k < cluster.size(); k++) {
                    Group grp = cluster.get(k);
                    int bx = x(d, colW) + 3 + (int) Math.round(lanes.get(k) * laneW);
                    int bw = (int) Math.round(laneW) - 2;
                    int by = y(grp.getTime().getStartSlot()) + 2;
                    int bh = y(grp.getTime().getStartSlot() + grp.getTime().getLength()) - by - 1;
                    Rectangle r = new Rectangle(bx, by, bw, bh);
                    boxes.add(new Box(grp, r));
                    paintEvent(g2, grp, r, current);
                }
            }
        }
    }

    /** 폭 안에 들어가도록 글자 단위로 나눔 */
    private static List<String> wrap(String text, FontMetrics fm, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (cur.length() > 0 && fm.stringWidth(cur.toString() + c) > width) {
                lines.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(c);
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    private void paintEvent(Graphics2D g2, Group grp, Rectangle r, Group current) {
        Color base = Theme.classColor(grp.getStudyClass());
        boolean isCurrent = selected != null && grp == current;
        boolean isCandidate = selected != null && !isCurrent && !selected.isStaff()
                && grp.getStudyClass() == selected.getStudyClass() && selected.isAvailable(grp.getTime());
        boolean faded = selected != null && !isCurrent && !isCandidate;

        Graphics2D e = (Graphics2D) g2.create();
        if (faded) e.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        e.setColor(Theme.soft(base, 0.80));
        e.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
        e.setColor(base);
        e.fillRoundRect(r.x, r.y, 4, r.height, 4, 4);

        // 글자: 칸이 넓으면 "기본1조" + "5명 · 운영진", 좁으면 글자 단위로 줄바꿈
        e.setClip(r.x + 5, r.y, r.width - 6, r.height);
        int textW = r.width - 10;
        String title = grp.getName();
        e.setFont(Theme.bold(12f));
        FontMetrics fm = e.getFontMetrics();
        if (fm.stringWidth(title) > textW) {
            e.setFont(Theme.bold(11f));
            fm = e.getFontMetrics();
        }
        List<String> lines = wrap(title, fm, textW);
        String detail = grp.size() + "명 · " + grp.getLeader().getName();
        if (lines.size() == 1 && fm.stringWidth(detail) > textW) detail = grp.size() + "명";
        else if (lines.size() > 1) lines.add(grp.size() + "명");
        int ty = r.y + 3 + fm.getAscent();
        e.setColor(Theme.darker(base, 0.45));
        for (String line : lines) {
            if (ty > r.y + r.height) break;
            e.drawString(line, r.x + 8, ty);
            ty += fm.getHeight() - 2;
            e.setColor(Theme.darker(base, 0.30));
        }
        if (lines.size() == 1) {
            e.setFont(Theme.font(11f));
            e.setColor(Theme.darker(base, 0.30));
            e.drawString(detail, r.x + 8, ty + 1);
        }
        e.setClip(null);

        if (isCurrent || isCandidate) {
            e.setColor(isCurrent ? Theme.BLUE : Theme.GREEN);
            e.setStroke(new BasicStroke(2f));
            e.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, 10, 10);
        }
        e.dispose();
    }
}
