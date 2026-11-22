import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 엑셀에서 관리하는 부원 CSV를 읽어 검증한다. (프로그램은 이 파일을 수정하지 않음)
 * 형식: 학번,이름,레벨,반,운영진(Y/N),가능시간
 * 예)   202533754,서유화,고급,N1~N2,Y,월 19:00-21:00; 수 18:00-20:00
 * - UTF-8(BOM 포함/미포함)과 엑셀 기본 CSV(CP949) 모두 읽음
 * - 엑셀이 붙이는 따옴표("...")와 뒤쪽 빈 칸도 처리
 */
public class MemberRepository {
    private final List<Member> members = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private Path loadedFile;
    private String encoding;

    public List<Member> findAll() { return members; }
    public List<String> getErrors() { return errors; }
    public Path getLoadedFile() { return loadedFile; }
    public String getEncoding() { return encoding; }

    public Member findById(String studentId) {
        for (Member m : members) if (m.getStudentId().equals(studentId)) return m;
        return null;
    }

    /** 파일을 읽어 기존 목록을 교체. 잘못된 줄은 건너뛰고 errors에 사유 기록 */
    public void load(Path path) throws IOException {
        List<String> lines = readLines(Files.readAllBytes(path));
        members.clear();
        errors.clear();
        loadedFile = path.toAbsolutePath();

        int lineNo = 0;
        for (String line : lines) {
            lineNo++;
            if (lineNo == 1 && line.startsWith("﻿")) line = line.substring(1); // BOM 제거
            if (line.replace(",", "").isBlank()) continue;  // 빈 줄(엑셀의 ",,,,," 포함)
            if (line.startsWith("학번")) continue;           // 헤더
            try {
                members.add(parseLine(line));
            } catch (IllegalArgumentException e) {
                errors.add(lineNo + "번째 줄: " + e.getMessage() + "  →  " + line);
            }
        }
    }

    private Member parseLine(String line) {
        List<String> p = splitCsv(line);
        while (p.size() < 6) p.add("");
        String id = p.get(0), name = p.get(1);
        if (!id.matches("\\d{6,10}")) throw new IllegalArgumentException("학번 오류(숫자 6~10자리): \"" + id + "\"");
        if (findById(id) != null) throw new IllegalArgumentException("학번 중복: " + id);
        if (name.isEmpty()) throw new IllegalArgumentException("이름 없음");
        Level level = Level.fromText(p.get(2));
        if (level == null) throw new IllegalArgumentException("레벨 오류(노베이스/초급/중급/고급): \"" + p.get(2) + "\"");
        StudyClass sc = StudyClass.fromText(p.get(3));
        if (sc == null) throw new IllegalArgumentException("반 오류(N1~N2/N3~N5): \"" + p.get(3) + "\"");
        String staff = p.get(4);
        if (!staff.equalsIgnoreCase("Y") && !staff.equalsIgnoreCase("N")) {
            throw new IllegalArgumentException("운영진 칸은 Y 또는 N: \"" + staff + "\"");
        }
        // 따옴표 없이 쉼표로 시간대를 구분한 경우 칸이 나뉘므로 6번째 칸부터 끝까지 합쳐서 읽음
        Availability av = Availability.parse(String.join(",", p.subList(5, p.size())));
        if (av.isEmpty()) throw new IllegalArgumentException("가능 시간 없음");
        return new Member(id, name, level, sc, staff.equalsIgnoreCase("Y"), av);
    }

    /** 따옴표를 지원하는 간단한 CSV 분리 */
    static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                else quoted = !quoted;
            } else if (c == ',' && !quoted) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString().trim());
        return out;
    }

    /** UTF-8로 먼저 읽고, 실패하면 엑셀 기본 저장 인코딩(CP949)으로 읽기 */
    private List<String> readLines(byte[] bytes) throws IOException {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            encoding = "UTF-8";
            return new ArrayList<>(Arrays.asList(text.split("\\r?\\n")));
        } catch (CharacterCodingException e) {
            encoding = "CP949(엑셀 기본 CSV)";
            String text = new String(bytes, Charset.forName("MS949"));
            return new ArrayList<>(Arrays.asList(text.split("\\r?\\n")));
        }
    }
}
