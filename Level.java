/** 일본어 레벨 */
public enum Level {
    NOBASE("노베이스"), BEGINNER("초급"), INTERMEDIATE("중급"), ADVANCED("고급");

    private final String label;

    Level(String label) { this.label = label; }

    public String getLabel() { return label; }

    /** "고급" 같은 한글 이름으로 레벨 찾기. 못 찾으면 null */
    public static Level fromText(String text) {
        for (Level l : values()) if (l.label.equals(text.trim())) return l;
        return null;
    }
}
