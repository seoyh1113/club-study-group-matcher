/** 희망 스터디 반 (회화반은 통합 운영으로 조 편성 대상에서 제외) */
public enum StudyClass {
    N1_N2("시험대비 N1~N2", "심화", true),
    N3_N5("시험대비 N3~N5", "기본", false);

    private final String label;
    /** 조 이름 앞에 붙는 말 (심화1조, 기본1조 …) */
    private final String groupType;
    /** 고급자가 적은 반은 최소 인원(5명) 예외 허용 */
    private final boolean smallGroupAllowed;

    StudyClass(String label, String groupType, boolean smallGroupAllowed) {
        this.label = label;
        this.groupType = groupType;
        this.smallGroupAllowed = smallGroupAllowed;
    }

    public String getLabel() { return label; }
    public String getGroupType() { return groupType; }

    /** 안내 문구용: "심화조(N1~N2)" */
    public String getDisplayName() { return groupType + "조(" + label.replace("시험대비 ", "") + ")"; }

    public boolean isSmallGroupAllowed() { return smallGroupAllowed; }

    /** "N1~N2", "N3~N5"로 반 찾기 (N1-N2, n1~n2 같은 표기도 허용). 못 찾으면 null */
    public static StudyClass fromText(String text) {
        String t = text.trim().toUpperCase().replace(" ", "").replace("-", "~");
        if (t.equals("N1~N2")) return N1_N2;
        if (t.equals("N3~N5")) return N3_N5;
        return null;
    }
}
