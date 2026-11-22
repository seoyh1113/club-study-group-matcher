import javax.swing.JEditorPane;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * "편성 보고서" 탭. 콘솔 보고서(toReport)와 같은 내용을 표와 색으로 정리해서 보여 준다.
 * 순서: 요약 숫자 → 확인 필요 → 조별 편성표 → 미배정 → 수동 이동·이름 변경 기록 → 편성 기준·과정
 */
@SuppressWarnings("serial")
public class ReportView extends JEditorPane {

    public ReportView() {
        setEditable(false);
        setContentType("text/html");
        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet css = kit.getStyleSheet();
        String f = Theme.fontFamily();
        css.addRule("body { font-family: '" + f + "'; font-size: 11pt; color: #1F2328; margin: 14px 18px; }");
        css.addRule("h2 { font-size: 15pt; margin: 4px 0 6px 0; }");
        css.addRule("h3 { font-size: 12.5pt; margin: 18px 0 6px 0; }");
        css.addRule("td { font-family: '" + f + "'; font-size: 11pt; }");
        css.addRule("th { font-family: '" + f + "'; font-size: 10.5pt; color: #6B7280; text-align: left; font-weight: normal; }");
        css.addRule(".sub { color: #6B7280; font-size: 10pt; }");
        css.addRule(".num { font-size: 18pt; font-weight: bold; }");
        css.addRule(".red { color: #E5484D; }");
        css.addRule(".green { color: #179A5E; }");
        css.addRule("li { margin-bottom: 3px; }");
        setEditorKit(kit);
        setBackground(Color.WHITE);
    }

    /**
     * 확인이 필요한 항목 모음: CSV에서 제외된 줄 + 조 경고(인원 미달 등) + 조 시간과 안 맞는 부원.
     * 미배정 부원은 아래 "미배정" 표에 따로 나오므로 여기에는 넣지 않음.
     */
    public static List<String> checkItems(MatchResult r, List<String> csvErrors) {
        List<String> items = new ArrayList<>();
        for (String e : csvErrors) items.add("CSV 제외: " + e);
        if (r == null) return items;
        items.addAll(r.allWarnings());
        for (Group g : r.getGroups()) {
            for (Member m : g.getMembers()) {
                if (!m.isAvailable(g.getTime())) items.add(m.getName() + ": " + g.getName() + " 시간(" + g.getTime() + ") 참여 불가");
            }
        }
        return items;
    }

    public void update(MatchResult r, List<String> csvErrors) {
        if (r == null) {
            StringBuilder sb = new StringBuilder("<html><body><p class='sub'>편성 후 표시돼요.</p>");
            appendChecks(sb, checkItems(null, csvErrors));
            setText(sb.append("</body></html>").toString());
        } else {
            setText(build(r, csvErrors));
        }
        setCaretPosition(0);
    }

    private static void appendChecks(StringBuilder sb, List<String> items) {
        if (items.isEmpty()) return;
        sb.append("<h3>확인 필요 <span class='sub'>").append(items.size()).append("건</span></h3><ul>");
        for (String w : items) sb.append("<li class='red'>").append(Theme.escape(w)).append("</li>");
        sb.append("</ul>");
    }

    private String build(MatchResult r, List<String> csvErrors) {
        StringBuilder sb = new StringBuilder("<html><body>");
        List<String> checks = checkItems(r, csvErrors);
        int assigned = r.assignedCount();

        // ---- 요약 ----
        sb.append("<h2>편성 결과 요약</h2><table cellspacing='6' cellpadding='0'><tr>");
        summary(sb, "조", r.getGroups().size() + "개", "#F5F6F8", "#1F2328");
        summary(sb, "배정 인원", assigned + "명", "#F5F6F8", "#1F2328");
        summary(sb, "미배정", r.getUnassigned().size() + "명", "#F5F6F8",
                r.getUnassigned().isEmpty() ? "#1F2328" : "#E5484D");
        summary(sb, "확인 필요", checks.size() + "건", "#F5F6F8",
                checks.isEmpty() ? "#1F2328" : "#E5484D");
        summary(sb, "수동 변경", r.getChanges().size() + "건", "#F5F6F8", "#1F2328");
        sb.append("</tr></table>");

        // ---- 확인 필요 (요약 바로 아래에서 먼저 보이게) ----
        appendChecks(sb, checks);

        // ---- 조별 편성표 ----
        for (StudyClass sc : StudyClass.values()) {
            List<Group> list = new ArrayList<>();
            for (Group g : r.getGroups()) if (g.getStudyClass() == sc) list.add(g);
            if (list.isEmpty()) continue;
            sb.append("<h3>").append(sc.getDisplayName()).append(" <span class='sub'>").append(list.size())
              .append("개 조</span></h3>");
            sb.append("<table width='100%' cellspacing='1' cellpadding='7' bgcolor='#E5E7EB'>")
              .append("<tr bgcolor='#F7F8FA'><th>조</th><th>시간</th><th nowrap>인원</th>")
              .append("<th>운영진</th><th>부원</th><th>상태</th></tr>");
            for (Group g : list) {
                sb.append("<tr bgcolor='#FFFFFF'><td nowrap><b>").append(g.getName()).append("</b></td>")
                  .append("<td nowrap>").append(g.getTime()).append("</td>")
                  .append("<td nowrap><b>").append(g.size()).append("</b>명</td>")
                  .append("<td nowrap>").append(g.getLeader().getName()).append(" <span class='sub'>")
                  .append(g.getLeader().getLevel().getLabel()).append("</span></td><td>");
                List<String> names = new ArrayList<>();
                for (Member m : g.getMembers()) {
                    names.add(m.isAvailable(g.getTime()) ? m.getName()
                            : "<span class='red'>" + m.getName() + "(시간X)</span>");
                }
                sb.append(names.isEmpty() ? "<span class='sub'>없음</span>" : String.join(", ", names))
                  .append("</td><td nowrap>").append(status(g)).append("</td></tr>");
            }
            sb.append("</table>");
        }

        // ---- 미배정 ----
        if (!r.getUnassigned().isEmpty()) {
            sb.append("<h3>미배정 부원 <span class='sub'>").append(r.getUnassigned().size()).append("명</span></h3>")
              .append("<table width='100%' cellspacing='1' cellpadding='7' bgcolor='#E5E7EB'>")
              .append("<tr bgcolor='#F7F8FA'><th>이름</th><th>학번</th><th>반</th><th>사유</th></tr>");
            for (Map.Entry<Member, String> e : r.getUnassigned().entrySet()) {
                Member m = e.getKey();
                sb.append("<tr bgcolor='#FFFFFF'><td><b>").append(m.getName()).append("</b>")
                  .append(m.isStaff() ? " <span class='sub'>운영진</span>" : "").append("</td><td>")
                  .append(m.getStudentId()).append("</td><td>").append(m.getStudyClass().getGroupType())
                  .append("</td><td>").append(Theme.escape(e.getValue())).append("</td></tr>");
            }
            sb.append("</table>");
        }

        // ---- 수동 이동·이름 변경 기록 ----
        sb.append("<h3>수동 이동·이름 변경 기록</h3>");
        if (r.getChanges().isEmpty()) {
            sb.append("<p class='sub'>없음</p>");
        } else {
            sb.append("<ol>");
            for (String c : r.getChanges()) sb.append("<li>").append(Theme.escape(c)).append("</li>");
            sb.append("</ol>");
        }

        // ---- 편성 기준·과정 ----
        sb.append("<h3>편성 기준</h3><ol class='sub'>");
        for (String rule : GroupMatcher.RULES) sb.append("<li>").append(rule).append("</li>");
        sb.append("</ol>");
        if (!r.getNotes().isEmpty()) {
            sb.append("<h3>편성 과정</h3><ul class='sub'>");
            for (String n : r.getNotes()) sb.append("<li>").append(n).append("</li>");
            sb.append("</ul>");
        }
        return sb.append("</body></html>").toString();
    }

    private static void summary(StringBuilder sb, String label, String value, String bg, String fg) {
        sb.append("<td bgcolor='").append(bg).append("' width='110'>")
          .append("<table cellpadding='8'><tr><td><span class='sub'>").append(label).append("</span><br>")
          .append("<span class='num'><font color='").append(fg).append("'>").append(value)
          .append("</font></span></td></tr></table></td>");
    }

    private static String status(Group g) {
        if (g.getMembers().isEmpty()) return "<span class='red'><b>부원 없음</b></span>";
        if (g.isUnderMinimum()) return "<span class='red'><b>인원 미달</b></span>";
        if (!g.meetsMinimum()) return "<span class='sub'>5명 미만(허용)</span>";
        return "<span class='sub'>정상</span>";
    }
}
