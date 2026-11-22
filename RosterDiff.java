import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CSV를 다시 불러왔을 때 이전 명단과 무엇이 달라졌는지 (학번 기준).
 * - added: 새로 생긴 학번, changed: 이름·레벨·반·운영진·가능 시간 중 하나라도 바뀐 학번, removed: 빠진 부원
 * - 처음 불러온 경우(firstLoad)는 비교하지 않음
 */
public class RosterDiff {
    private final boolean firstLoad;
    private final Set<String> added = new HashSet<>();
    private final Set<String> changed = new HashSet<>();
    private final List<Member> removed = new ArrayList<>();

    private RosterDiff(boolean firstLoad) { this.firstLoad = firstLoad; }

    /** before가 null이면 처음 불러온 것 */
    public static RosterDiff compare(List<Member> before, List<Member> after) {
        RosterDiff d = new RosterDiff(before == null);
        if (before == null) return d;
        Map<String, Member> old = new HashMap<>();
        for (Member m : before) old.put(m.getStudentId(), m);
        for (Member m : after) {
            Member o = old.remove(m.getStudentId());
            if (o == null) d.added.add(m.getStudentId());
            else if (!sameInfo(o, m)) d.changed.add(m.getStudentId());
        }
        d.removed.addAll(old.values());
        return d;
    }

    private static boolean sameInfo(Member a, Member b) {
        return a.getName().equals(b.getName()) && a.getLevel() == b.getLevel()
                && a.getStudyClass() == b.getStudyClass() && a.isStaff() == b.isStaff()
                && a.getAvailability().toText().equals(b.getAvailability().toText());
    }

    public boolean isFirstLoad() { return firstLoad; }
    public boolean isAdded(Member m) { return added.contains(m.getStudentId()); }
    public boolean isChanged(Member m) { return changed.contains(m.getStudentId()); }
    public int addedCount() { return added.size(); }
    public int changedCount() { return changed.size(); }
    public List<Member> getRemoved() { return removed; }
    public boolean hasChanges() { return !added.isEmpty() || !changed.isEmpty() || !removed.isEmpty(); }
}
