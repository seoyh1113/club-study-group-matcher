import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 조 편성 결과: 조 목록 + 배정 못 된 부원(사유) + 경고 + 수동 이동 기록 */
public class MatchResult {
    private final List<Group> groups = new ArrayList<>();
    private final Map<Member, String> unassigned = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> changes = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();   // 편성 과정 설명

    public List<Group> getGroups() { return groups; }
    public Map<Member, String> getUnassigned() { return unassigned; }
    public List<String> getWarnings() { return warnings; }
    public List<String> getNotes() { return notes; }

    /** 고정 경고 + 지금 조 상태로 계산한 인원 경고. 수동 이동 뒤에도 항상 현재 상태를 보여 준다 */
    public List<String> allWarnings() {
        List<String> list = new ArrayList<>(warnings);
        for (Group g : groups) {
            if (g.getMembers().isEmpty()) {
                list.add(g.getName() + ": 시간이 맞는 부원이 없어 운영진 혼자입니다. 시간 조정이 필요합니다.");
            } else if (g.isUnderMinimum()) {
                list.add(g.getName() + ": 운영진 포함 " + g.size() + "명으로 최소 인원(5명) 미달");
            }
        }
        return list;
    }

    /** 조에 배정된 인원 합계 (운영진 포함) */
    public int assignedCount() {
        int total = 0;
        for (Group g : groups) total += g.size();
        return total;
    }

    public Group findGroupOf(Member m) {
        for (Group g : groups) if (g.contains(m)) return g;
        return null;
    }

    /** 부원을 target 조로 이동 (미배정 부원도 가능) */
    public void move(Member m, Group target, boolean timeFits) {
        Group from = findGroupOf(m);
        if (from != null) from.remove(m);
        unassigned.remove(m);
        target.add(m);
        changes.add(m.shortInfo() + " : " + (from == null ? "미배정" : from.getName())
                + " → " + target.getName() + " (" + target.getTime() + ")"
                + (timeFits ? "" : " ※ 등록 시간과 맞지 않음, 운영진 판단으로 이동"));
    }

    /** 부원을 조에서 빼서 미배정으로 돌림 (GUI에서 미배정 칸으로 드래그할 때) */
    public void unassign(Member m, String reason) {
        Group from = findGroupOf(m);
        if (from == null) return;
        from.remove(m);
        unassigned.put(m, reason);
        changes.add(m.shortInfo() + " : " + from.getName() + " → 미배정 (" + reason + ")");
    }

    public List<String> getChanges() { return changes; }

    /** 조 이름 최대 글자 수 */
    public static final int MAX_NAME_LENGTH = 12;

    /**
     * 새 조 이름 검사. 문제가 없으면 null, 있으면 사유.
     * - 쉼표·따옴표: result.csv의 칸이 밀림
     * - '미배정': result.csv에서 미배정 부원과 구분할 수 없음
     */
    public String checkGroupName(Group g, String name) {
        if (name == null || name.trim().isEmpty()) return "이름을 입력하세요.";
        name = name.trim();
        if (name.length() > MAX_NAME_LENGTH) return MAX_NAME_LENGTH + "자 이하로 입력하세요.";
        if (name.contains(",") || name.contains("\"")) return "쉼표·따옴표는 쓸 수 없어요.";
        if (name.equals("미배정")) return "'미배정'은 결과 파일에서 미배정 부원을 뜻해 쓸 수 없어요.";
        for (Group other : groups) {
            if (other != g && other.getName().equals(name)) return "이미 있는 이름이에요.";
        }
        return null;
    }

    /** 조 이름 변경 (GUI). 검사를 통과하면 바꾸고 이동 기록에도 남긴다. 반환: 실패 사유 또는 null */
    public String rename(Group g, String newName) {
        String error = checkGroupName(g, newName);
        if (error != null) return error;
        String name = newName.trim();
        String old = g.getName();
        if (old.equals(name)) return null;
        g.setName(name);
        changes.add("조 이름 변경: " + old + " → " + name);
        return null;
    }

    public String toReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("===== 편성 기준 =====\n");
        for (int i = 0; i < GroupMatcher.RULES.length; i++) {
            sb.append("  ").append(i + 1).append(". ").append(GroupMatcher.RULES[i]).append("\n");
        }
        if (!notes.isEmpty()) {
            sb.append("\n===== 편성 과정 =====\n");
            for (String n : notes) sb.append("  - ").append(n).append("\n");
        }
        sb.append("\n===== 조 편성 결과 =====\n");
        for (Group g : groups) sb.append(g.toReport()).append("\n\n");
        sb.append("배정 인원: ").append(assignedCount()).append("명 / 조 ").append(groups.size()).append("개\n");
        if (!unassigned.isEmpty()) {
            sb.append("\n----- 미배정 부원 (").append(unassigned.size()).append("명) -----\n");
            for (Map.Entry<Member, String> e : unassigned.entrySet()) {
                sb.append("  ").append(e.getKey().shortInfo()).append(" : ").append(e.getValue()).append("\n");
            }
        }
        List<String> current = allWarnings();
        if (!current.isEmpty()) {
            sb.append("\n----- 참고/경고 -----\n");
            for (String w : current) sb.append("  - ").append(w).append("\n");
        }
        if (!changes.isEmpty()) {
            sb.append("\n----- 수동 이동 기록 -----\n");
            for (String c : changes) sb.append("  - ").append(c).append("\n");
        }
        return sb.toString();
    }

    /** 엑셀용 CSV (BOM 포함). 한 줄 = 한 사람 */
    public String toCsv() {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("조,반,요일,시간,조인원,역할,학번,이름,레벨,참여가능시간,비고\n");
        for (Group g : groups) {
            TimeWindow t = g.getTime();
            String time = Availability.slotToTime(t.getStartSlot()) + "-"
                    + Availability.slotToTime(t.getStartSlot() + t.getLength());
            String prefix = g.getName() + "," + g.getStudyClass().getLabel() + ","
                    + Availability.DAYS[t.getDay()] + "," + time + "," + g.size() + ",";
            String note = g.meetsMinimum() ? ""
                    : (g.getStudyClass().isSmallGroupAllowed() ? "5명 미만(심화조라 허용)" : "5명 미만");
            sb.append(prefix).append(row("운영진", g.getLeader(), note));
            for (Member m : g.getMembers()) sb.append(prefix).append(row("부원", m, note));
        }
        for (Map.Entry<Member, String> e : unassigned.entrySet()) {
            Member m = e.getKey();
            sb.append("미배정,").append(m.getStudyClass().getLabel()).append(",,,,")
              .append(row(m.isStaff() ? "운영진" : "부원", m, e.getValue()));
        }
        return sb.toString();
    }

    private static String row(String role, Member m, String note) {
        // 가능 시간은 따옴표로 감싸 엑셀에서 한 칸에 들어가게 함
        String times = "\"" + m.getAvailability().toText().replace("\"", "\"\"") + "\"";
        return role + "," + m.getStudentId() + "," + m.getName() + "," + m.getLevel().getLabel() + ","
                + times + "," + note + "\n";
    }
}
