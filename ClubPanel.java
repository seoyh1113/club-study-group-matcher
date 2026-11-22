import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Desktop;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * GUI 메인 화면 (TimeTree 느낌 디자인).
 *  ┌ 위: 앱 제목 · 불러온 파일 · [CSV 불러오기] [조 편성 실행] [결과 저장]
 *  ├ 왼쪽: 알림 배너 / 조 카드(드래그·오른쪽 클릭으로 이동) / 아래에 고정된 미배정 카드
 *  └ 오른쪽: 선택한 부원 카드 / 탭(시간표 · 확인 필요 · 편성 보고서)
 * 편성·이동 로직은 콘솔 버전과 같은 GroupMatcher, MatchResult를 그대로 사용한다.
 */
@SuppressWarnings("serial")  // 화면 객체를 파일로 저장(직렬화)하지 않으므로 경고 끔
public class ClubPanel extends JPanel {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN);

    private final MemberRepository repo = new MemberRepository();
    private final GroupMatcher matcher = new GroupMatcher();
    private final MemberTransferHandler transferHandler = new MemberTransferHandler(this);

    private MatchResult result;
    private RosterDiff diff = RosterDiff.compare(null, new ArrayList<>());
    private Member selected;
    private boolean syncingSelection;   // 목록 선택을 코드로 바꾸는 중에는 선택 이벤트 무시

    private final List<GroupCard> cards = new ArrayList<>();
    private GroupCard unassignedCard;
    private final JPanel unassignedDock = new JPanel(new BorderLayout());   // 미배정 카드는 화면 아래에 고정
    private JScrollPane boardScroll;
    /** 드래그 중 마우스가 조 편성 화면 위·아래 끝에 가면 자동으로 스크롤 */
    private final Timer dragScroll = new Timer(30, e -> autoScrollWhileDragging());

    // ----- 화면 구성 요소 -----
    private final JLabel fileInfo = Theme.chip("불러온 파일 없음", Theme.SUBTEXT, Theme.GRAY_SOFT);
    private final JButton loadButton = Theme.button("CSV 불러오기", false);
    private final JButton excelButton = Theme.button("엑셀에서 열기", false);
    private final JButton matchButton = Theme.button("조 편성 실행", true);
    private final JButton saveButton = Theme.button("결과 저장", false);

    private final Theme.Card banner = new Theme.Card(new BorderLayout(12, 0), 14);
    private final JLabel bannerIcon = new JLabel();
    private final JLabel bannerTitle = Theme.label("", 14f, true, Theme.TEXT);
    private final JLabel bannerSub = Theme.label("", 12f, false, Theme.SUBTEXT);
    private final JButton bannerAction = Theme.button("", true);
    private Runnable bannerActionTask;

    /** 불러온 파일이 엑셀에서 다시 저장됐는지 확인하는 타이머 */
    private final Timer fileWatch = new Timer(1500, e -> checkFileChanged());
    private FileTime loadedTime;      // 불러올 때의 파일 수정 시각
    private boolean changeNotified;   // 같은 변경을 여러 번 알리지 않도록
    private Timer bannerTimer;

    private final JPanel board = Theme.stack();

    private final Theme.Card selectionCard = new Theme.Card(new BorderLayout(14, 0), 16);
    private final CardLayout rightLayout = new CardLayout();
    private final JPanel rightCards = new JPanel(rightLayout);
    private final Theme.Tabs rightTabs = new Theme.Tabs(i -> rightLayout.show(rightCards, "R" + i),
            "시간표", "편성 보고서");
    private final TimetableView timetable = new TimetableView();
    private final ReportView report = new ReportView();

    public ClubPanel() {
        super(new BorderLayout());
        setBackground(Theme.BG);
        add(createHeader(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createLeft(), createRight());
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setResizeWeight(0.55);
        split.setDividerLocation(800);
        split.setDividerSize(6);
        split.setUI(new BasicSplitPaneUI() {          // 구분선을 바탕색과 같게 (윈도우 기본 모양 제거)
            @Override public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override public void paint(Graphics g) {
                        g.setColor(Theme.BG);
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });
        split.setBackground(Theme.BG);
        add(split, BorderLayout.CENTER);

        timetable.setOnGroupClick(this::focusGroup);
        showBanner(Kind.INFO, "CSV를 불러와 주세요", "[CSV 불러오기]로 members.csv를 열어요.", false);
        buildBoard();
        refresh();
    }

    // ===================== 화면 만들기 =====================

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(Theme.LINE);
                g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            }
        };
        header.setBackground(Theme.SURFACE);
        header.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));

        JPanel title = Theme.stack();
        title.add(Theme.label("스터디 조 편성", 17f, true, Theme.TEXT));
        title.add(Theme.label("일본어 동아리 운영진용", 12f, false, Theme.SUBTEXT));

        JPanel left = Theme.row(10);
        left.add(title);
        left.add(Box.createHorizontalStrut(12));
        fileInfo.setFont(Theme.font(12f));
        fileInfo.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        left.add(fileInfo);

        loadButton.addActionListener(e -> chooseAndLoad());
        excelButton.addActionListener(e -> openInExcel());
        excelButton.setToolTipText("불러온 CSV를 엑셀로 열기");
        matchButton.addActionListener(e -> runMatching());
        saveButton.addActionListener(e -> saveResult());
        JPanel right = Theme.row(8);
        right.add(loadButton);
        right.add(excelButton);
        right.add(Box.createHorizontalStrut(8));
        right.add(matchButton);
        right.add(saveButton);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel createLeft() {
        JPanel left = new JPanel(new BorderLayout());
        left.setBackground(Theme.BG);

        banner.setStroke(null, 0);
        banner.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        bannerIcon.setVerticalAlignment(JLabel.TOP);
        JPanel bannerText = Theme.stack();
        bannerText.add(bannerTitle);
        bannerText.add(Box.createVerticalStrut(2));
        bannerText.add(bannerSub);
        banner.add(bannerIcon, BorderLayout.WEST);
        banner.add(bannerText, BorderLayout.CENTER);
        bannerAction.setFont(Theme.bold(12f));
        bannerAction.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        bannerAction.setVisible(false);
        bannerAction.addActionListener(e -> { if (bannerActionTask != null) bannerActionTask.run(); });
        JPanel actionBox = new JPanel(new java.awt.GridBagLayout());   // 버튼을 세로 가운데에
        actionBox.setOpaque(false);
        actionBox.add(bannerAction);
        banner.add(actionBox, BorderLayout.EAST);
        fileWatch.start();

        JPanel top = Theme.stack();
        top.setBorder(BorderFactory.createEmptyBorder(14, 16, 0, 16));
        top.add(Theme.fitWidth(banner));
        left.add(top, BorderLayout.NORTH);

        board.setBorder(BorderFactory.createEmptyBorder(8, 16, 16, 16));
        boardScroll = Theme.scroll(holder(board), Theme.BG);
        JPanel boardPage = new JPanel(new BorderLayout());
        boardPage.setOpaque(false);
        boardPage.add(boardScroll, BorderLayout.CENTER);
        unassignedDock.setOpaque(false);
        unassignedDock.setBorder(BorderFactory.createEmptyBorder(6, 16, 12, 16));
        boardPage.add(unassignedDock, BorderLayout.SOUTH);
        left.add(boardPage, BorderLayout.CENTER);
        return left;
    }

    private JPanel createRight() {
        JPanel right = new JPanel(new BorderLayout(0, 12));
        right.setBackground(Theme.BG);
        right.setBorder(BorderFactory.createEmptyBorder(14, 4, 16, 16));

        selectionCard.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        right.add(selectionCard, BorderLayout.NORTH);

        Theme.Card tabsCard = new Theme.Card(new BorderLayout(), 16);
        tabsCard.setBorder(BorderFactory.createEmptyBorder(4, 8, 10, 8));
        JPanel tabsBar = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(Theme.LINE_SOFT);
                g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            }
        };
        tabsBar.setOpaque(false);
        tabsBar.add(rightTabs, BorderLayout.WEST);
        tabsCard.add(tabsBar, BorderLayout.NORTH);

        JPanel calendar = new JPanel(new BorderLayout());
        calendar.setOpaque(false);
        calendar.add(timetable.createDayHeader(), BorderLayout.NORTH);
        calendar.add(timetable, BorderLayout.CENTER);   // 스크롤 없이 남은 높이에 맞춰 그림
        calendar.add(createLegend(), BorderLayout.SOUTH);

        rightCards.setOpaque(false);
        rightCards.add(calendar, "R0");
        rightCards.add(Theme.scroll(report, Color.WHITE), "R1");
        tabsCard.add(rightCards, BorderLayout.CENTER);
        right.add(tabsCard, BorderLayout.CENTER);
        return right;
    }

    private JPanel createLegend() {
        JPanel legend = Theme.row(14);
        legend.setBorder(BorderFactory.createEmptyBorder(8, 6, 0, 0));
        legend.add(legendItem(new Color(34, 181, 115, 60), null, "가능 시간"));
        legend.add(legendItem(Theme.SURFACE, Theme.BLUE, "현재 조"));
        legend.add(legendItem(Theme.SURFACE, Theme.GREEN, "이동 가능 조"));
        return legend;
    }

    private JLabel legendItem(Color fill, Color stroke, String text) {
        JLabel l = Theme.label(text, 11f, false, Theme.SUBTEXT);
        l.setIcon(new javax.swing.Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = Theme.smooth(g);
                g2.setColor(fill);
                g2.fillRoundRect(x, y, 14, 12, 4, 4);
                if (stroke != null) {
                    g2.setColor(stroke);
                    g2.setStroke(new java.awt.BasicStroke(2f));
                    g2.drawRoundRect(x + 1, y + 1, 12, 10, 4, 4);
                }
                g2.dispose();
            }
            @Override public int getIconWidth() { return 14; }
            @Override public int getIconHeight() { return 12; }
        });
        return l;
    }

    /** 스크롤 안의 내용이 세로로 늘어나지 않게 위쪽에 붙여 둠 */
    private static JPanel holder(JComponent content) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(content, BorderLayout.NORTH);
        return p;
    }

    // ===================== 알림 배너 =====================

    private enum Kind { SUCCESS, INFO, WARN }

    /** 왼쪽 위 알림. flash면 잠깐 진한 색으로 깜빡여서 바뀐 것을 알아채게 함 */
    private void showBanner(Kind kind, String title, String sub, boolean flash) {
        showBanner(kind, title, sub, flash, null, null);
    }

    /** actionLabel이 있으면 배너 오른쪽에 버튼을 보여 줌 (예: 다시 불러오기) */
    private void showBanner(Kind kind, String title, String sub, boolean flash, String actionLabel, Runnable action) {
        bannerAction.setVisible(actionLabel != null);
        bannerAction.setText(actionLabel == null ? "" : actionLabel);
        bannerActionTask = action;
        Color accent = kind == Kind.SUCCESS ? Theme.GREEN : kind == Kind.WARN ? Theme.AMBER : Theme.BLUE;
        Color soft = kind == Kind.SUCCESS ? Theme.GREEN_SOFT : kind == Kind.WARN ? Theme.AMBER_SOFT : Theme.BLUE_SOFT;
        bannerIcon.setIcon(kind == Kind.WARN ? Theme.warnIcon(accent, 18) : circleCheck(accent));
        bannerTitle.setText(title);
        bannerSub.setText("<html>" + sub + "</html>");
        if (bannerTimer != null) bannerTimer.stop();
        if (!flash) {
            banner.setFill(soft);
            return;
        }
        final int steps = 24;
        final int[] step = {0};
        banner.setFill(Theme.mix(accent, soft, 0.7));
        bannerTimer = new Timer(40, e -> {
            step[0]++;
            banner.setFill(Theme.mix(Theme.mix(accent, soft, 0.7), soft, Math.min(1.0, step[0] / (double) steps)));
            if (step[0] >= steps) ((Timer) e.getSource()).stop();
        });
        bannerTimer.start();
    }

    private static javax.swing.Icon circleCheck(Color color) {
        return new javax.swing.Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = Theme.smooth(g);
                g2.setColor(color);
                g2.fillOval(x, y, 18, 18);
                Theme.drawCheck(g2, x + 5, y + 5, 8, Color.WHITE);
                g2.dispose();
            }
            @Override public int getIconWidth() { return 18; }
            @Override public int getIconHeight() { return 18; }
        };
    }

    // ===================== 조 편성 보드 =====================

    /** 편성 결과가 바뀔 때(편성 실행, 파일 불러오기)마다 조 카드를 새로 만든다 */
    private void buildBoard() {
        board.removeAll();
        cards.clear();
        unassignedCard = null;
        unassignedDock.removeAll();

        if (result == null) {
            board.add(Theme.fitWidth(emptyState()));
        } else {

            for (StudyClass sc : StudyClass.values()) {
                JPanel grid = new JPanel(new GridLayout(0, 3, 12, 12));
                grid.setOpaque(false);
                int people = 0;
                for (Group g : result.getGroups()) {
                    if (g.getStudyClass() != sc) continue;
                    GroupCard card = new GroupCard(g);
                    cards.add(card);
                    grid.add(card);
                    people += g.size();
                }
                if (grid.getComponentCount() == 0) continue;
                addSection(sc.getDisplayName(), grid.getComponentCount() + "개 조 · " + people + "명", grid);
            }
            unassignedCard = new GroupCard(null);
            unassignedDock.add(unassignedCard, BorderLayout.CENTER);
        }
        board.revalidate();
        board.repaint();
        unassignedDock.revalidate();
        unassignedDock.repaint();
    }

    private JComponent emptyState() {
        Theme.Card card = new Theme.Card(new BorderLayout(), 16);
        card.setBorder(BorderFactory.createEmptyBorder(40, 24, 40, 24));
        JPanel box = Theme.stack();
        boolean loaded = !repo.findAll().isEmpty();
        JLabel title = Theme.label(loaded ? "편성 전" : "명단 없음", 18f, true, Theme.TEXT);
        JLabel sub = Theme.label(loaded
                ? repo.findAll().size() + "명 불러옴"
                : "[CSV 불러오기]로 시작하세요", 13f, false, Theme.SUBTEXT);
        title.setAlignmentX(CENTER_ALIGNMENT);
        sub.setAlignmentX(CENTER_ALIGNMENT);
        box.add(title);
        box.add(Box.createVerticalStrut(6));
        box.add(sub);
        if (loaded) {
            JButton run = Theme.button("조 편성 실행", true);
            run.addActionListener(e -> runMatching());
            run.setAlignmentX(CENTER_ALIGNMENT);
            box.add(Box.createVerticalStrut(16));
            box.add(run);
        }
        card.add(box, BorderLayout.CENTER);
        return card;
    }

    private void addSection(String title, String sub, JComponent content) {
        JPanel line = Theme.row(8);
        line.add(Theme.label(title, 15f, true, Theme.TEXT));
        line.add(Theme.label(sub, 12f, false, Theme.SUBTEXT));
        line.setBorder(BorderFactory.createEmptyBorder(board.getComponentCount() == 0 ? 6 : 20, 2, 8, 0));
        board.add(Theme.fitWidth(line));
        board.add(Theme.fitWidth(content));
    }

    // ===================== 화면 갱신 =====================

    /** 현재 결과(MatchResult)와 선택한 부원을 기준으로 모든 화면을 다시 그린다 */
    private void refresh() {
        for (GroupCard c : allCards()) c.update();
        timetable.setData(result, selected);
        updateFileInfo();
        updateSelectionCard();
        report.update(result, repo.getErrors());
        matchButton.setEnabled(!repo.findAll().isEmpty());
        excelButton.setEnabled(repo.getLoadedFile() != null);
        saveButton.setEnabled(result != null);
    }

    private void updateFileInfo() {
        if (repo.getLoadedFile() == null) {
            fileInfo.setText("불러온 파일 없음");
            fileInfo.setIcon(null);
            return;
        }
        fileInfo.setIcon(Theme.dot(repo.getErrors().isEmpty() ? Theme.GREEN : Theme.AMBER, 8));
        fileInfo.setIconTextGap(6);
        fileInfo.setText(repo.getLoadedFile().getFileName() + "  ·  " + repo.findAll().size() + "명"
                + (repo.getErrors().isEmpty() ? "" : "  ·  오류 " + repo.getErrors().size() + "줄"));
        fileInfo.setToolTipText(repo.getLoadedFile() + " [" + repo.getEncoding() + "]");
    }

    /** 오른쪽 위: 선택한 부원 정보 + 옮겨갈 수 있는 조 (누르면 바로 이동) */
    private void updateSelectionCard() {
        selectionCard.removeAll();
        if (selected == null) {
            JPanel box = Theme.stack();
            box.add(Theme.label("부원 선택", 15f, true, Theme.TEXT));
            box.add(Box.createVerticalStrut(4));
            box.add(Theme.label("<html>부원을 누르면 가능 시간과 이동 가능한 조가 보여요.<br>"
                    + "끌어다 놓거나 오른쪽 클릭으로 옮겨요.</html>",
                    12.5f, false, Theme.SUBTEXT));
            selectionCard.add(box, BorderLayout.CENTER);
            selectionCard.revalidate();
            selectionCard.repaint();
            return;
        }
        Member m = selected;

        JPanel info = Theme.stack();
        JPanel nameLine = Theme.row(6);
        nameLine.add(Theme.label(m.getName(), 17f, true, Theme.TEXT));
        nameLine.add(Theme.label(m.getStudentId(), 12f, false, Theme.SUBTEXT));
        nameLine.add(Theme.label(m.getStudyClass().getGroupType() + " · " + m.getLevel().getLabel()
                + (m.isStaff() ? " · 운영진" : ""), 12f, false, Theme.SUBTEXT));
        nameLine.setAlignmentX(LEFT_ALIGNMENT);
        info.add(nameLine);
        info.add(Box.createVerticalStrut(4));
        info.add(infoLine("가능 시간", m.getAvailability().toText(), Theme.TEXT));

        if (result != null) {
            Group cur = result.findGroupOf(m);
            if (cur == null) {
                info.add(infoLine("현재", "미배정 — " + result.getUnassigned().get(m), Theme.RED));
            } else {
                boolean fits = m.isAvailable(cur.getTime());
                info.add(infoLine("현재", cur.getName() + " (" + cur.getTime() + ")" + (fits ? "" : "  · 시간 안 맞음"),
                        fits ? Theme.TEXT : Theme.RED));
            }
            if (!m.isStaff()) {
                JPanel chips = Theme.row(6);
                chips.add(keyLabel("이동 가능"));
                int count = 0;
                for (Group g : matcher.candidateGroups(m, m.getAvailability(), result)) {
                    if (!m.isAvailable(g.getTime())) continue;
                    JLabel chip = Theme.chip(g.getName() + "  " + g.size() + "명", Theme.GREEN_DARK, Theme.GREEN_SOFT);
                    chip.setIcon(Theme.checkIcon(Theme.GREEN_DARK, 10));
                    chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    chip.setToolTipText("누르면 이동");
                    chip.addMouseListener(new MouseAdapter() {
                        @Override public void mouseClicked(MouseEvent e) { moveMember(m, g); }
                    });
                    chips.add(chip);
                    count++;
                }
                if (count == 0) chips.add(Theme.label("없음", 12.5f, false, Theme.SUBTEXT));
                chips.setAlignmentX(LEFT_ALIGNMENT);
                chips.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
                info.add(chips);
            }
        }
        selectionCard.add(info, BorderLayout.CENTER);
        selectionCard.revalidate();
        selectionCard.repaint();
    }

    /** 왼쪽 항목 이름 (값이 모두 같은 위치에서 시작하도록 고정 폭) */
    private static JLabel keyLabel(String key) {
        JLabel k = Theme.label(key, 12f, false, Theme.SUBTEXT);
        Dimension size = new Dimension(96, k.getPreferredSize().height);
        k.setPreferredSize(size);
        k.setMinimumSize(size);
        k.setMaximumSize(size);
        return k;
    }

    private JPanel infoLine(String key, String value, Color valueColor) {
        JPanel line = Theme.row(8);
        line.add(keyLabel(key));
        line.add(Theme.label(value, 12.5f, false, valueColor));
        line.setAlignmentX(LEFT_ALIGNMENT);
        return line;
    }

    // ===================== 선택 =====================

    private void onListSelected(JList<Member> source) {
        if (syncingSelection) return;
        Member m = source.getSelectedValue();
        if (m == null) return;
        syncingSelection = true;
        for (GroupCard c : allCards()) if (c.list != source) c.list.clearSelection();
        syncingSelection = false;
        selected = m;
        updateSelectionViews();
    }

    private void updateSelectionViews() {
        for (GroupCard c : allCards()) c.updateBorder();
        timetable.setData(result, selected);
        updateSelectionCard();
    }

    /** 이동 후 목록이 새로 그려져도 같은 부원을 계속 선택해 둠 */
    private void reselect(Member m) {
        syncingSelection = true;
        for (GroupCard c : allCards()) {
            if (c.model.contains(m)) c.list.setSelectedValue(m, true);
            else c.list.clearSelection();
        }
        syncingSelection = false;
    }

    /** 캘린더 일정이나 경고를 눌렀을 때: 조 카드로 스크롤하고 잠깐 강조 */
    private void focusGroup(Group g) {
        for (GroupCard c : cards) {
            if (c.group != g) continue;
            SwingUtilities.invokeLater(() -> {
                c.scrollRectToVisible(new Rectangle(0, 0, c.getWidth(), c.getHeight()));
                c.setStroke(Theme.classColor(g.getStudyClass()), 3f);
                Timer t = new Timer(1200, e -> c.updateBorder());
                t.setRepeats(false);
                t.start();
            });
        }
    }

    private List<GroupCard> allCards() {
        List<GroupCard> list = new ArrayList<>(cards);
        if (unassignedCard != null) list.add(unassignedCard);
        return list;
    }

    // ===================== 이동 =====================

    /** 드래그·오른쪽 클릭·추천 칩이 공통으로 부르는 이동 처리. target이 null이면 미배정으로 */
    public void moveMember(Member m, Group target) {
        if (result == null || m.isStaff()) return;
        Group from = result.findGroupOf(m);
        if (from == target) return;

        if (target == null) {
            result.unassign(m, "운영진이 직접 미배정으로 옮김");
        } else {
            if (target.getStudyClass() != m.getStudyClass()) {
                JOptionPane.showMessageDialog(this, "다른 반의 조로는 옮길 수 없습니다.");
                return;
            }
            boolean fits = m.isAvailable(target.getTime());
            if (!fits) {
                int answer = JOptionPane.showConfirmDialog(this,
                        m.getName() + "님은 " + target.getName() + "(" + target.getTime() + ")에 참여할 수 없어요.\n"
                                + "가능 시간: " + m.getAvailability() + "\n\n그래도 옮길까요?",
                        "시간이 맞지 않음", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (answer != JOptionPane.YES_OPTION) return;
            }
            result.move(m, target, fits);
        }
        selected = m;
        refresh();
        reselect(m);
        String to = target == null ? "미배정" : target.getName();
        String warn = "";
        if (from != null && from.isUnderMinimum()) {
            warn = " · " + from.getName() + " 인원 미달";
        }
        showBanner(warn.isEmpty() ? Kind.SUCCESS : Kind.WARN, m.getName() + " → " + to,
                now() + warn, true);
    }

    /** 드래그 시작·끝 (MemberTransferHandler가 호출) */
    public void dragStarted() { dragScroll.start(); }
    public void dragEnded() { dragScroll.stop(); }

    private void autoScrollWhileDragging() {
        if (boardScroll == null || !boardScroll.isShowing()) return;
        java.awt.PointerInfo info = java.awt.MouseInfo.getPointerInfo();
        if (info == null) return;
        java.awt.Point p = info.getLocation();
        javax.swing.JViewport vp = boardScroll.getViewport();
        SwingUtilities.convertPointFromScreen(p, vp);
        if (p.x < 0 || p.x > vp.getWidth()) return;
        int dy = 0;
        if (p.y < 60 && p.y > -50) dy = -Math.max(4, (60 - p.y) / 2);                          // 위쪽 끝 → 위로
        else if (p.y > vp.getHeight() - 30 && p.y < vp.getHeight()) dy = Math.max(4, (p.y - vp.getHeight() + 30) / 2); // 아래쪽 끝 → 아래로
        if (dy == 0) return;
        java.awt.Point pos = vp.getViewPosition();
        int max = Math.max(0, vp.getView().getHeight() - vp.getHeight());
        vp.setViewPosition(new java.awt.Point(pos.x, Math.max(0, Math.min(max, pos.y + dy))));
    }

    /** TransferHandler가 "같은 조로 놓는지" 확인할 때 사용 */
    public Group groupOf(Member m) {
        return result == null ? null : result.findGroupOf(m);
    }

    private void showMovePopup(JList<Member> list, MouseEvent e) {
        int index = list.locationToIndex(e.getPoint());
        if (index < 0 || !list.getCellBounds(index, index).contains(e.getPoint())) return;
        list.setSelectedIndex(index);
        Member m = list.getModel().getElementAt(index);
        if (m.isStaff() || result == null) return;

        JPopupMenu menu = new JPopupMenu();
        JMenu moveMenu = new JMenu("다른 조로 이동");
        for (Group g : matcher.candidateGroups(m, m.getAvailability(), result)) {
            boolean fits = m.isAvailable(g.getTime());
            JMenuItem item = new JMenuItem(g.getName() + "   " + g.getTime() + "   " + g.size() + "명"
                    + (fits ? "" : "   (시간 안 맞음)"));
            item.setIcon(fits ? Theme.checkIcon(Theme.GREEN, 12) : Theme.warnIcon(Theme.RED, 12));
            item.addActionListener(ev -> moveMember(m, g));
            moveMenu.add(item);
        }
        if (moveMenu.getItemCount() == 0) moveMenu.setEnabled(false);
        menu.add(moveMenu);
        if (result.findGroupOf(m) != null) {
            JMenuItem out = new JMenuItem("미배정으로 빼기");
            out.addActionListener(ev -> moveMember(m, null));
            menu.add(out);
        }
        menu.show(list, e.getX(), e.getY());
    }

    // ===================== 파일 / 편성 =====================

    private void chooseAndLoad() {
        JFileChooser chooser = new JFileChooser(currentDir());
        chooser.setDialogTitle("부원 명단 CSV 불러오기");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV 파일 (*.csv)", "csv"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            loadFile(chooser.getSelectedFile().toPath());
        }
    }

    private void loadFile(Path path) {
        if (!confirmDiscardChanges("다른 명단을 불러오면")) return;
        List<Member> before = repo.getLoadedFile() == null ? null : new ArrayList<>(repo.findAll());
        boolean hadResult = result != null;
        try {
            repo.load(path);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "읽기 실패 (엑셀에서 열려 있으면 닫아 주세요)\n" + e.getMessage(),
                    "불러오기 실패", JOptionPane.ERROR_MESSAGE);
            return;
        }
        diff = RosterDiff.compare(before, repo.findAll());
        loadedTime = modifiedTime(repo.getLoadedFile());
        changeNotified = false;
        result = null;   // 명단이 바뀌었으니 이전 편성은 무효
        selected = null;
        buildBoard();
        refresh();

        StringBuilder sub = new StringBuilder(now()).append(" · ").append(repo.findAll().size()).append("명");
        if (!repo.getErrors().isEmpty()) sub.append(" · <font color='#E5484D'>제외 ").append(repo.getErrors().size()).append("줄</font>");
        if (!diff.isFirstLoad()) {
            sub.append("<br>").append(diff.hasChanges()
                    ? "추가 " + diff.addedCount() + " · 수정 " + diff.changedCount() + " · 빠짐 " + diff.getRemoved().size()
                    : "변경 없음");
        }
        if (hadResult) sub.append(" · 편성 초기화됨");
        showBanner(repo.getErrors().isEmpty() ? Kind.SUCCESS : Kind.WARN,
                path.getFileName() + " 불러옴", sub.toString(), true);

        if (!repo.getErrors().isEmpty()) {
            JTextArea area = new JTextArea(String.join("\n", repo.getErrors()), 10, 60);
            area.setEditable(false);
            JOptionPane.showMessageDialog(this, new Object[]{
                    "아래 줄은 편성에서 제외돼요.", new JScrollPane(area)},
                    "CSV 오류 " + repo.getErrors().size() + "줄", JOptionPane.WARNING_MESSAGE);
        }
    }

    // ===================== 엑셀 연동 =====================

    /** 불러온 CSV를 Windows에 연결된 프로그램(보통 엑셀)으로 연다 */
    private void openInExcel() {
        Path file = repo.getLoadedFile();
        if (file == null) return;
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            JOptionPane.showMessageDialog(this, "파일을 열 수 없어요.\n" + file);
            return;
        }
        try {
            Desktop.getDesktop().open(file.toFile());
        } catch (IOException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, "파일을 열 수 없어요.\n" + e.getMessage(), "엑셀에서 열기", JOptionPane.ERROR_MESSAGE);
            return;
        }
        showBanner(Kind.INFO, "엑셀에서 열었어요", "저장하면 [다시 불러오기]가 나타나요.", true);
    }

    /** 타이머가 1.5초마다 호출: 파일 수정 시각이 바뀌었으면 배너로 알림 (자동으로 다시 불러오지는 않음) */
    private void checkFileChanged() {
        Path file = repo.getLoadedFile();
        if (file == null || loadedTime == null || changeNotified) return;
        FileTime now = modifiedTime(file);
        if (now == null || now.equals(loadedTime)) return;
        changeNotified = true;
        showBanner(Kind.WARN, "파일이 바뀌었어요",
                now() + (result != null ? " · 다시 불러오면 편성이 초기화돼요" : ""),
                true, "다시 불러오기", () -> loadFile(file));
    }

    private static FileTime modifiedTime(Path file) {
        try {
            return Files.getLastModifiedTime(file);
        } catch (IOException e) {
            return null;
        }
    }

    // ===================== 조 이름 바꾸기 =====================

    private void renameGroup(Group g) {
        Object input = JOptionPane.showInputDialog(this,
                "새 조 이름 (12자 이내)",
                "조 이름 바꾸기", JOptionPane.PLAIN_MESSAGE, null, null, g.getName());
        if (input == null) return;
        String name = input.toString().trim();
        if (name.equals(g.getName())) return;
        String old = g.getName();
        String error = result.rename(g, name);   // 검사와 변경은 MatchResult가 맡음
        if (error != null) {
            JOptionPane.showMessageDialog(this, error, "조 이름 바꾸기", JOptionPane.WARNING_MESSAGE);
            renameGroup(g);   // 다시 입력
            return;
        }
        refresh();
        showBanner(Kind.SUCCESS, old + " → " + name, now(), true);
    }

    private void runMatching() {
        if (repo.findAll().isEmpty()) return;
        if (!confirmDiscardChanges("다시 편성하면")) return;
        result = matcher.match(repo.findAll());
        selected = null;
        buildBoard();
        refresh();
        int assigned = result.assignedCount();
        int warn = ReportView.checkItems(result, repo.getErrors()).size();
        showBanner(Kind.SUCCESS, "편성 완료",
                now() + " · 조 " + result.getGroups().size() + "개 · 배정 " + assigned + "명 · 미배정 "
                        + result.getUnassigned().size() + "명" + (warn == 0 ? "" : " · 확인 필요 " + warn + "건"), true);
    }

    private void saveResult() {
        if (result == null) return;
        JFileChooser chooser = new JFileChooser(currentDir());
        chooser.setDialogTitle("편성 결과 저장 (엑셀용 CSV)");
        chooser.setSelectedFile(new File(currentDir(), "result.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".csv")) file = new File(file.getPath() + ".csv");
        if (file.exists() && JOptionPane.showConfirmDialog(this, file.getName() + "을 덮어쓸까요?",
                "덮어쓰기 확인", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            Files.write(file.toPath(), result.toCsv().getBytes(StandardCharsets.UTF_8));
            showBanner(Kind.SUCCESS, file.getName() + " 저장", now() + " · " + file.getAbsolutePath(), true);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "저장 실패 (엑셀에서 열려 있으면 닫아 주세요)\n" + e.getMessage(),
                    "저장 실패", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** 수동 이동 기록이 있으면 버리기 전에 확인 */
    private boolean confirmDiscardChanges(String action) {
        if (result == null || result.getChanges().isEmpty()) return true;
        return JOptionPane.showConfirmDialog(this,
                action + " 수동 변경 " + result.getChanges().size() + "건이 사라져요. 계속할까요?",
                "확인", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    private File currentDir() {
        return repo.getLoadedFile() != null ? repo.getLoadedFile().getParent().toFile()
                : new File(System.getProperty("user.dir"));
    }

    private static String now() {
        return LocalTime.now().format(CLOCK);
    }

    // ===================== 조 카드 =====================

    /** 조 하나(또는 미배정)를 보여 주는 둥근 카드: 위에 조 정보, 아래에 조원 목록 */
    private static final int UNASSIGNED_CELL_W = 170;

    private class GroupCard extends Theme.Card {
        private final Group group;   // null이면 미배정 카드
        private final JPanel head = Theme.stack();
        private final DefaultListModel<Member> model = new DefaultListModel<>();
        private final JList<Member> list = new JList<>(model);

        GroupCard(Group group) {
            super(new BorderLayout(), 16);
            this.group = group;

            head.setBorder(BorderFactory.createEmptyBorder(group == null ? 10 : 14, 14, 6, 14));
            add(head, BorderLayout.NORTH);

            list.setOpaque(false);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setCellRenderer(new MemberCell(group));
            list.setFixedCellHeight(34);
            if (group == null) {
                list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
                list.setVisibleRowCount(-1);
                list.setFixedCellWidth(UNASSIGNED_CELL_W);
            }
            list.setDragEnabled(true);
            list.setDropMode(DropMode.INSERT);
            list.setTransferHandler(transferHandler);
            list.putClientProperty(MemberTransferHandler.GROUP_KEY, group);
            ToolTipManager.sharedInstance().registerComponent(list);
            list.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) onListSelected(list);
            });
            list.addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) showMovePopup(list, e);   // 오른쪽 클릭 메뉴
                }
            });
            if (group == null) {
                // 미배정은 넘치면 카드 안에서 스크롤
                JScrollPane scroll = Theme.scroll(list, Color.WHITE);
                scroll.setOpaque(false);
                scroll.getViewport().setOpaque(false);
                scroll.setBorder(BorderFactory.createEmptyBorder(0, 6, 10, 6));
                add(scroll, BorderLayout.CENTER);
            } else {
                // 조 카드는 조원 수만큼 커지므로 안쪽 스크롤 영역을 두지 않음
                // → 카드 위에서 휠을 굴려도 바깥 조 편성 화면이 스크롤됨
                JPanel listBox = new JPanel(new BorderLayout());
                listBox.setOpaque(false);
                listBox.setBorder(BorderFactory.createEmptyBorder(0, 6, 10, 6));
                listBox.add(list, BorderLayout.CENTER);
                add(listBox, BorderLayout.CENTER);
            }
            if (group == null) {   // 너비가 바뀌면 한 줄에 들어가는 인원이 달라지므로 높이 다시 계산
                addComponentListener(new java.awt.event.ComponentAdapter() {
                    private int lastHeight = -1;
                    @Override public void componentResized(java.awt.event.ComponentEvent e) {
                        int h = getPreferredSize().height;
                        if (h != lastHeight) { lastHeight = h; unassignedDock.revalidate(); }
                    }
                });
            }
        }

        /** 카드 높이를 조원 수에 맞춤 (카드 안에 스크롤이 생기지 않게) */
        @Override public Dimension getPreferredSize() {
            int rows;
            if (group == null) {
                int width = getWidth() > 0 ? getWidth() : 700;
                int cols = Math.max(1, (width - 24) / UNASSIGNED_CELL_W);
                // 최대 2줄까지만 보이고 넘치면 카드 안에서 스크롤 (조 편성 화면을 가리지 않게)
                rows = Math.min(2, Math.max(1, (model.size() + cols - 1) / cols));
            } else {
                rows = Math.max(3, model.size());
            }
            int height = head.getPreferredSize().height + rows * 34 + 14;
            return new Dimension(230, height);
        }

        void update() {
            model.clear();
            head.removeAll();
            if (group == null) {
                for (Member m : result.getUnassigned().keySet()) model.addElement(m);
                JPanel line = Theme.row(8);
                line.add(Theme.label("미배정", 14f, true, Theme.TEXT));
                line.add(Theme.label(model.size() + "명", 13f, false, Theme.SUBTEXT));
                line.add(Theme.label("·  여기로 끌면 조에서 빠져요", 12f, false, Theme.HINT));
                head.add(Theme.fitWidth(line));
            } else {
                for (Member m : group.getMembers()) model.addElement(m);
                buildHeader();
            }
            head.revalidate();
            updateBorder();
        }

        private void buildHeader() {
            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            JLabel name = Theme.label(group.getName(), 15f, true, Theme.TEXT);
            JLabel edit = new JLabel(Theme.blankIcon(13));   // 연필은 마우스를 올렸을 때만 보임
            edit.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
            JPanel nameBox = Theme.row(0);
            nameBox.add(name);
            nameBox.add(edit);
            nameBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            nameBox.setToolTipText("이름 바꾸기");
            MouseAdapter renameClick = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { renameGroup(group); }
                @Override public void mouseEntered(MouseEvent e) { edit.setIcon(Theme.editIcon(Theme.SUBTEXT, 13)); }
                @Override public void mouseExited(MouseEvent e) { edit.setIcon(Theme.blankIcon(13)); }
            };
            nameBox.addMouseListener(renameClick);
            name.addMouseListener(renameClick);
            edit.addMouseListener(renameClick);
            top.add(nameBox, BorderLayout.WEST);
            top.add(Theme.label(group.size() + "명", 13f, true, Theme.SUBTEXT), BorderLayout.EAST);
            head.add(Theme.fitWidth(top));

            // 시간 · 운영진을 한 줄 회색 글씨로
            JLabel sub = Theme.label(group.getTime() + "  ·  운영진 " + group.getLeader().getName(), 12.5f, false, Theme.SUBTEXT);
            sub.setBorder(BorderFactory.createEmptyBorder(3, 0, 2, 0));
            head.add(Theme.fitWidth(sub));

            // 문제가 있을 때만 빨간 글씨 한 줄 (정상이면 아무것도 표시 안 함)
            List<String> problems = new ArrayList<>();
            if (group.getMembers().isEmpty()) problems.add("부원 없음");
            else if (group.isUnderMinimum()) problems.add("인원 미달");
            int mismatch = group.countTimeMismatch();
            if (mismatch > 0) problems.add("시간 안 맞음 " + mismatch + "명");
            if (!problems.isEmpty()) {
                JLabel warn = Theme.label(String.join(" · ", problems), 12f, false, Theme.RED);
                head.add(Theme.fitWidth(warn));
            }
        }

        /** 테두리: 파랑 = 선택한 부원의 현재 조, 초록 = 옮겨갈 수 있는 조 (문제는 카드 안 빨간 글씨로) */
        void updateBorder() {
            Color color = Theme.LINE;
            float width = 1f;
            if (group != null && result != null) {
                if (selected != null) {
                    if (group.contains(selected)) { color = Theme.BLUE; width = 2.5f; }
                    else if (!selected.isStaff() && group.getStudyClass() == selected.getStudyClass()
                            && selected.isAvailable(group.getTime())) { color = Theme.GREEN; width = 2.5f; }
                }
            }
            setStroke(color, width);
        }
    }

    /** 조원 한 줄: 이름 + 레벨(회색), 시간이 안 맞으면 오른쪽에 빨간 글씨 */
    private class MemberCell extends JPanel implements ListCellRenderer<Member> {
        private final Group group;
        private final JLabel name = new JLabel();
        private final JLabel tag = Theme.label("", 11.5f, false, Theme.RED);
        private boolean selectedRow;

        MemberCell(Group group) {
            super(new BorderLayout(8, 0));
            this.group = group;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
            add(name, BorderLayout.CENTER);
            JPanel right = new JPanel(new BorderLayout());
            right.setOpaque(false);
            right.add(tag, BorderLayout.CENTER);
            right.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            add(right, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Member> list, Member m, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            selectedRow = isSelected;
            String sub;
            if (group == null) sub = m.getStudyClass().getGroupType() + (m.isStaff() ? " · 운영진" : "");
            else sub = m.getLevel().getLabel();
            name.setText("<html><b>" + m.getName() + "</b> <font color='#9CA3AF'>" + sub + "</font></html>");
            name.setFont(Theme.font(12.5f));
            name.setForeground(Theme.TEXT);
            boolean mismatch = group != null && !m.isAvailable(group.getTime());
            tag.setText(mismatch ? "시간 안 맞음" : "");
            tag.setVisible(mismatch);
            String tip = m.getStudentId() + " · 가능 시간: " + m.getAvailability();
            if (group == null && result != null) tip += " · 사유: " + result.getUnassigned().get(m);
            setToolTipText(tip);
            return this;
        }

        @Override protected void paintComponent(Graphics g) {
            if (selectedRow) {
                Graphics2D g2 = Theme.smooth(g);
                g2.setColor(Theme.GRAY_SOFT);
                g2.fillRoundRect(2, 1, getWidth() - 4, getHeight() - 2, 10, 10);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
