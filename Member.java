/** 동아리 부원 (운영진 포함). 정보 수정은 엑셀(CSV)에서 하므로 읽기 전용 */
public class Member {
    private final String studentId;
    private final String name;
    private final Level level;
    private final StudyClass studyClass;
    private final boolean staff;
    private final Availability availability;

    public Member(String studentId, String name, Level level, StudyClass studyClass,
                  boolean staff, Availability availability) {
        this.studentId = studentId;
        this.name = name;
        this.level = level;
        this.studyClass = studyClass;
        this.staff = staff;
        this.availability = availability;
    }

    public String getStudentId() { return studentId; }
    public String getName() { return name; }
    public Level getLevel() { return level; }
    public StudyClass getStudyClass() { return studyClass; }
    public boolean isStaff() { return staff; }
    public Availability getAvailability() { return availability; }

    public boolean isAvailable(TimeWindow w) {
        return availability.isFree(w);
    }

    /** 목록 출력용 짧은 표기 */
    public String shortInfo() {
        return name + "(" + studentId + ", " + level.getLabel() + (staff ? ", 운영진" : "") + ")";
    }

    @Override
    public String toString() {
        return String.format("%-10s %-6s %-5s %-14s %-4s %s",
                studentId, name, level.getLabel(), studyClass.getLabel(),
                staff ? "운영진" : "부원", availability);
    }
}
