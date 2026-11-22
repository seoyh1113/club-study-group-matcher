import java.util.ArrayList;
import java.util.List;

/** 편성된 스터디 조 하나 */
public class Group {
    private String name;          // GUI에서 이름 변경 가능
    private final StudyClass studyClass;
    private final Member leader;       // 담당 운영진 (같은 조에서 함께 공부)
    private final TimeWindow time;
    private final List<Member> members = new ArrayList<>(); // 운영진 제외 부원
    private String reason = "";  // 조 시간 선정 이유

    public void setReason(String reason) { this.reason = reason; }

    public Group(String name, StudyClass studyClass, Member leader, TimeWindow time) {
        this.name = name;
        this.studyClass = studyClass;
        this.leader = leader;
        this.time = time;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public StudyClass getStudyClass() { return studyClass; }
    public Member getLeader() { return leader; }
    public TimeWindow getTime() { return time; }
    public List<Member> getMembers() { return members; }

    /** 운영진 포함 인원 */
    public int size() { return members.size() + 1; }

    public void add(Member m) { members.add(m); }
    public boolean remove(Member m) { return members.remove(m); }

    public boolean contains(Member m) { return leader == m || members.contains(m); }

    public boolean meetsMinimum() { return size() >= GroupMatcher.MIN_GROUP_SIZE; }

    /** 최소 인원 미달이면서 예외(심화조)도 아닌 경우 = 경고 대상 */
    public boolean isUnderMinimum() { return !meetsMinimum() && !studyClass.isSmallGroupAllowed(); }

    /** 조 시간에 참여할 수 없는 부원 수 (운영진이 직접 옮긴 경우에 생김) */
    public int countTimeMismatch() {
        int count = 0;
        for (Member m : members) if (!m.isAvailable(time)) count++;
        return count;
    }

    public String toReport() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%s] %s | %s | %d명", name, studyClass.getLabel(), time, size()));
        if (!meetsMinimum()) {
            sb.append(studyClass.isSmallGroupAllowed() ? "  (5명 미만, 심화조라 허용)" : "  ※ 5명 미만");
        }
        if (!reason.isEmpty()) sb.append("\n    시간 선정 이유: ").append(reason);
        sb.append("\n    운영진: ").append(leader.shortInfo());
        for (Member m : members) sb.append("\n    부원  : ").append(m.shortInfo());
        return sb.toString();
    }
}
