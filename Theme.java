import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * 화면 디자인 모음 (TimeTree 앱 느낌: 흰 바탕, 초록 포인트 색, 둥근 카드, 파스텔 일정 색).
 * 색·글꼴·버튼 모양을 여기 한 곳에서 정하고, 다른 화면 클래스는 가져다 쓰기만 한다.
 */
public final class Theme {
    private Theme() {}

    // ---------- 색 ----------
    public static final Color BG = new Color(0xF5F6F8);          // 앱 바탕
    public static final Color SURFACE = Color.WHITE;              // 카드 바탕
    public static final Color LINE = new Color(0xE5E7EB);         // 구분선
    public static final Color LINE_SOFT = new Color(0xF0F1F3);
    public static final Color TEXT = new Color(0x1F2328);
    public static final Color SUBTEXT = new Color(0x6B7280);
    public static final Color HINT = new Color(0x9CA3AF);

    public static final Color GREEN = new Color(0x22B573);        // 포인트 색 (TimeTree 초록 느낌)
    public static final Color GREEN_DARK = new Color(0x179A5E);
    public static final Color GREEN_SOFT = new Color(0xE3F6EC);
    public static final Color BLUE = new Color(0x3B82F6);
    public static final Color BLUE_SOFT = new Color(0xE8F0FE);
    public static final Color RED = new Color(0xE5484D);
    public static final Color RED_SOFT = new Color(0xFDECEC);
    public static final Color AMBER = new Color(0xD97706);
    public static final Color AMBER_SOFT = new Color(0xFEF3C7);
    public static final Color GRAY_SOFT = new Color(0xF1F2F4);
    public static final Color SATURDAY = new Color(0x3B82F6);
    public static final Color SUNDAY = new Color(0xE5484D);

    /** 반별 조 색 (캘린더 일정 블록) — 차분한 2가지 색만 사용 */
    public static Color classColor(StudyClass sc) {
        return sc == StudyClass.N1_N2 ? new Color(0x7C8FD6) : new Color(0x7FA8A0);
    }

    /** c를 흰색 쪽으로 섞은 연한 색 (amount 0~1, 클수록 연함) */
    public static Color soft(Color c, double amount) {
        return mix(c, Color.WHITE, amount);
    }

    public static Color mix(Color a, Color b, double t) {
        return new Color(
                (int) Math.round(a.getRed() * (1 - t) + b.getRed() * t),
                (int) Math.round(a.getGreen() * (1 - t) + b.getGreen() * t),
                (int) Math.round(a.getBlue() * (1 - t) + b.getBlue() * t));
    }

    public static Color darker(Color c, double amount) {
        return mix(c, Color.BLACK, amount);
    }

    /** HTML로 보여 줄 글자에서 <, >, & 를 바꿔 줌 (이름·사유에 이런 글자가 있어도 깨지지 않게) */
    public static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ---------- 글꼴 ----------
    private static final String FAMILY = pickFamily();

    /** 한글이 잘 보이는 글꼴을 순서대로 찾음 (Windows: 맑은 고딕) */
    private static String pickFamily() {
        String[] wanted = {"Pretendard", "Malgun Gothic", "맑은 고딕", "Apple SD Gothic Neo", "Noto Sans CJK KR", "NanumGothic"};
        List<String> installed = new ArrayList<>();
        for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) installed.add(f);
        for (String w : wanted) if (installed.contains(w)) return w;
        return Font.DIALOG;
    }

    public static Font font(float size) { return new Font(FAMILY, Font.PLAIN, 12).deriveFont(size); }

    public static Font bold(float size) { return new Font(FAMILY, Font.BOLD, 12).deriveFont(size); }

    public static String fontFamily() { return FAMILY; }

    /** 대화상자·툴팁 등 기본 부품의 글꼴과 색을 맞춤 */
    public static void install() {
        FontUIResource f = new FontUIResource(font(13f));
        String[] keys = {"Label.font", "Button.font", "OptionPane.messageFont", "OptionPane.buttonFont",
                "ToolTip.font", "MenuItem.font", "Menu.font", "PopupMenu.font", "TextArea.font", "List.font",
                "FileChooser.listFont", "ComboBox.font", "TextField.font"};
        for (String k : keys) UIManager.put(k, f);
        UIManager.put("ToolTip.background", Color.WHITE);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE), BorderFactory.createEmptyBorder(6, 8, 6, 8)));
    }

    // ---------- 그리기 도우미 ----------
    public static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    /** 체크 표시(✓)를 직접 그림 — 글꼴에 ✓ 문자가 없어도 보이도록 */
    public static void drawCheck(Graphics2D g2, int x, int y, int size, Color color) {
        Path2D p = new Path2D.Double();
        p.moveTo(x, y + size * 0.55);
        p.lineTo(x + size * 0.38, y + size * 0.9);
        p.lineTo(x + size, y + size * 0.15);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(Math.max(1.6f, size / 6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(p);
    }

    /** 느낌표 삼각형(경고)을 직접 그림 */
    public static void drawWarn(Graphics2D g2, int x, int y, int size, Color color) {
        Path2D p = new Path2D.Double();
        p.moveTo(x + size / 2.0, y);
        p.lineTo(x + size, y + size);
        p.lineTo(x, y + size);
        p.closePath();
        g2.setColor(color);
        g2.fill(p);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(Math.max(1.4f, size / 8f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(x + size / 2, y + size * 4 / 10, x + size / 2, y + size * 7 / 10);
        g2.drawLine(x + size / 2, y + size * 85 / 100, x + size / 2, y + size * 85 / 100);
    }

    // ---------- 아이콘 ----------
    /** 아무것도 그리지 않는 빈 자리 (아이콘이 나타날 때 글자가 밀리지 않게) */
    public static Icon blankIcon(int size) {
        return new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) { }
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    public static Icon dot(Color color, int size) {
        return new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = smooth(g);
                g2.setColor(color);
                g2.fillOval(x, y, size, size);
                g2.dispose();
            }
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    public static Icon checkIcon(Color color, int size) {
        return new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = smooth(g);
                drawCheck(g2, x + 1, y + 1, size - 2, color);
                g2.dispose();
            }
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    /** 연필 모양 (이름 바꾸기) */
    public static Icon editIcon(Color color, int size) {
        return new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = smooth(g);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(size / 7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D p = new Path2D.Double();
                p.moveTo(x + size * 0.15, y + size * 0.85);      // 연필 끝
                p.lineTo(x + size * 0.25, y + size * 0.6);
                p.lineTo(x + size * 0.7, y + size * 0.15);
                p.lineTo(x + size * 0.85, y + size * 0.3);
                p.lineTo(x + size * 0.4, y + size * 0.75);
                p.closePath();
                g2.draw(p);
                g2.dispose();
            }
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    public static Icon warnIcon(Color color, int size) {
        return new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = smooth(g);
                drawWarn(g2, x, y, size, color);
                g2.dispose();
            }
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    // ---------- 부품 ----------
    /** 작은 둥근 꼬리표 (예: "운영진", "5명 미만") */
    public static JLabel chip(String text, Color fg, Color bg) {
        JLabel l = new JLabel(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = smooth(g);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        l.setOpaque(false);
        l.setFont(bold(11f));
        l.setForeground(fg);
        l.setBackground(bg);
        l.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        return l;
    }

    public static JLabel label(String text, float size, boolean isBold, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(isBold ? bold(size) : font(size));
        l.setForeground(color);
        return l;
    }

    /** 가로로 이어 붙이는 투명 줄 (칩 여러 개 놓을 때) */
    public static JPanel row(int gap) {
        // FlowLayout은 맨 앞에도 간격을 넣어서 위아래 줄의 시작 위치가 어긋남 → 항목 "사이"에만 간격을 넣음
        JPanel p = new JPanel() {
            @Override protected void addImpl(Component comp, Object constraints, int index) {
                if (getComponentCount() > 0 && gap > 0) super.addImpl(javax.swing.Box.createHorizontalStrut(gap), null, -1);
                super.addImpl(comp, constraints, index);
            }
        };
        p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.X_AXIS));
        p.setOpaque(false);
        return p;
    }

    /** 둥근 모서리 버튼. primary면 초록 바탕 흰 글씨, 아니면 흰 바탕 테두리 */
    public static JButton button(String text, boolean primary) {
        JButton b = new JButton(text) {
            private boolean hover;
            {
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                });
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = smooth(g);
                int w = getWidth(), h = getHeight();
                Color fill, border;
                if (!isEnabled()) { fill = GRAY_SOFT; border = GRAY_SOFT; }
                else if (primary) { fill = hover ? GREEN_DARK : GREEN; border = fill; }
                else { fill = hover ? GRAY_SOFT : Color.WHITE; border = LINE; }
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);
                g2.setColor(border);
                g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
                g2.dispose();
                setForeground(!isEnabled() ? HINT : primary ? Color.WHITE : TEXT);
                super.paintComponent(g);
            }
        };
        b.setFont(bold(13f));
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** 스크롤 영역: 테두리 없애고 얇은 스크롤바 */
    public static JScrollPane scroll(Component view, Color bg) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(bg);
        sp.setBackground(bg);
        sp.getVerticalScrollBar().setUI(new ThinScrollBarUI());
        sp.getHorizontalScrollBar().setUI(new ThinScrollBarUI());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(10, 10));
        sp.getHorizontalScrollBar().setPreferredSize(new Dimension(10, 10));
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.getVerticalScrollBar().setOpaque(false);
        return sp;
    }

    /** 둥근 모서리 카드. 테두리 색·두께를 바꿔 선택/경고 상태를 표시 */
    public static class Card extends JPanel {
        private Color fill = SURFACE;
        private Color stroke = LINE;
        private float strokeWidth = 1f;
        private final int arc;

        public Card(LayoutManager layout, int arc) {
            super(layout);
            this.arc = arc;
            setOpaque(false);
        }

        public void setFill(Color c) { fill = c; repaint(); }
        public void setStroke(Color c, float width) { stroke = c; strokeWidth = width; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getHeight();
            g2.setColor(new Color(0, 0, 0, 10));                 // 아주 옅은 그림자
            g2.fillRoundRect(1, 2, w - 2, h - 2, arc, arc);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w - 1, h - 2, arc, arc);
            if (stroke != null) {
                g2.setColor(stroke);
                g2.setStroke(new BasicStroke(strokeWidth));
                float o = strokeWidth / 2f;
                g2.drawRoundRect(Math.round(o), Math.round(o), Math.round(w - 1 - strokeWidth),
                        Math.round(h - 2 - strokeWidth), arc, arc);
            }
            g2.dispose();
        }
    }

    /** 밑줄 탭 (TimeTree 상단 탭 느낌). 선택하면 onSelect(번호) 호출 */
    public static class Tabs extends JPanel {
        private final List<JLabel> items = new ArrayList<>();
        private int selected;
        private final IntConsumer onSelect;

        public Tabs(IntConsumer onSelect, String... titles) {
            super(new FlowLayout(FlowLayout.LEFT, 0, 0));
            this.onSelect = onSelect;
            setOpaque(false);
            for (int i = 0; i < titles.length; i++) {
                final int index = i;
                JLabel l = new JLabel(titles[i]) {
                    @Override protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        if (index == selected) {
                            Graphics2D g2 = smooth(g);
                            g2.setColor(GREEN);
                            g2.fillRoundRect(10, getHeight() - 3, getWidth() - 20, 3, 3, 3);
                            g2.dispose();
                        }
                    }
                };
                l.setBorder(BorderFactory.createEmptyBorder(8, 14, 10, 14));
                l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                l.addMouseListener(new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent e) { select(index); }
                });
                items.add(l);
                add(l);
            }
            restyle();
        }

        public void select(int index) {
            selected = index;
            restyle();
            onSelect.accept(index);
        }

        public void setTitle(int index, String title) { items.get(index).setText(title); }

        private void restyle() {
            for (int i = 0; i < items.size(); i++) {
                JLabel l = items.get(i);
                l.setFont(i == selected ? bold(14f) : font(14f));
                l.setForeground(i == selected ? TEXT : SUBTEXT);
                l.repaint();
            }
        }
    }

    /** 위아래 간격만 있는 세로 묶음 */
    public static JPanel stack() {
        JPanel p = new JPanel();
        p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.Y_AXIS));
        p.setOpaque(false);
        return p;
    }

    /** 내용물 c를 왼쪽 정렬하고 세로로 늘어나지 않게 감쌈 (BoxLayout 안에서 사용) */
    public static JComponent fitWidth(JComponent c) {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        p.setOpaque(false);
        p.add(c, BorderLayout.CENTER);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    /** 얇고 둥근 스크롤바 */
    private static class ThinScrollBarUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            thumbColor = new Color(0xC9CDD3);
            trackColor = new Color(0, 0, 0, 0);
        }
        @Override protected JButton createDecreaseButton(int o) { return zero(); }
        @Override protected JButton createIncreaseButton(int o) { return zero(); }
        private JButton zero() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = smooth(g);
            g2.setColor(isDragging ? new Color(0xA9AEB6) : thumbColor);
            boolean vertical = scrollbar.getOrientation() == JScrollBar.VERTICAL;
            int arc = vertical ? r.width - 4 : r.height - 4;
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, arc, arc);
            g2.dispose();
        }
        @Override public Dimension getPreferredSize(JComponent c) { return new Dimension(10, 10); }
        @Override protected Dimension getMinimumThumbSize() { return new Dimension(10, 30); }
    }
}
