import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

/**
 * 일본어 동아리 스터디 조 편성 프로그램 (콘솔)
 * - 부원 등록/수정/삭제는 엑셀에서 members.csv를 편집
 * - 이 프로그램은 CSV를 읽어 검증 → 조 편성 → 조 이동 → 결과 저장
 * 실행: javac -encoding UTF-8 *.java  →  java Main
 */
public class Main {
    private static final String MEMBER_FILE = "members.csv";
    private static final String RESULT_CSV = "result.csv";

    private final Scanner sc = new Scanner(System.in);
    private final MemberRepository repo = new MemberRepository();
    private final GroupMatcher matcher = new GroupMatcher();
    private MatchResult lastResult;

    public static void main(String[] args) {
        new Main().run();
    }

    private void run() {
        System.out.println("부원 정보는 엑셀에서 " + MEMBER_FILE + "를 편집하세요. (엑셀 저장 형식: CSV UTF-8)");
        loadFile(Paths.get(MEMBER_FILE), true);
        while (true) {
            printMenu();
            String cmd = sc.nextLine().trim();
            switch (cmd) {
                case "1": loadMenu(); break;
                case "2": listMembers(); break;
                case "3": runMatching(); break;
                case "4": showResult(); break;
                case "5": recommendAndMove(); break;
                case "6": saveResult(); break;
                case "0":
                    System.out.println("종료합니다.");
                    return;
                default: System.out.println("메뉴 번호를 다시 입력하세요.");
            }
        }
    }

    private void printMenu() {
        System.out.println();
        String file = repo.getLoadedFile() == null ? "없음" : repo.getLoadedFile().getFileName().toString();
        System.out.println("===== 일본어 동아리 스터디 조 편성 ===== (" + file + ", 부원 " + repo.findAll().size() + "명)");
        System.out.println("1. CSV 불러오기 / 다시 불러오기     2. 부원 목록");
        System.out.println("3. 조 편성 실행                     4. 편성 결과 보기");
        System.out.println("5. 다른 조 추천 / 조 이동           6. 편성 결과 저장 (엑셀용 csv)");
        System.out.println("0. 종료");
        System.out.print("선택> ");
    }

    // ---------------- 1. 불러오기 + 검증 ----------------
    private void loadMenu() {
        System.out.print("파일 이름 (Enter = " + MEMBER_FILE + ", 예: sample_members.csv): ");
        String name = sc.nextLine().trim();
        loadFile(Paths.get(name.isEmpty() ? MEMBER_FILE : name), false);
    }

    private void loadFile(Path path, boolean quietIfMissing) {
        if (!Files.exists(path)) {
            if (quietIfMissing) {
                System.out.println(path + " 파일이 없습니다. 1번에서 sample_members.csv를 불러오거나 엑셀로 만들어 두세요.");
            } else {
                System.out.println("파일을 찾을 수 없습니다: " + path.toAbsolutePath());
            }
            return;
        }
        try {
            repo.load(path);
        } catch (IOException e) {
            System.out.println("파일 읽기 실패 (엑셀에서 열려 있으면 닫고 다시 시도): " + e.getMessage());
            return;
        }
        lastResult = null; // 명단이 바뀌었으니 이전 편성은 무효
        System.out.println("불러옴: " + repo.getLoadedFile() + " [" + repo.getEncoding() + "]");
        printValidationSummary();
    }

    private void printValidationSummary() {
        List<Member> all = repo.findAll();
        System.out.println("정상 " + all.size() + "명 / 오류 " + repo.getErrors().size() + "줄");
        for (StudyClass c : StudyClass.values()) {
            int staff = 0, normal = 0;
            for (Member m : all) {
                if (m.getStudyClass() != c) continue;
                if (m.isStaff()) staff++; else normal++;
            }
            System.out.println("  " + c.getLabel() + ": 운영진 " + staff + "명, 부원 " + normal + "명");
        }
        if (!repo.getErrors().isEmpty()) {
            System.out.println("※ 아래 줄은 편성에서 제외됩니다. 엑셀에서 고친 뒤 1번으로 다시 불러오세요.");
            for (String e : repo.getErrors()) System.out.println("  ! " + e);
        }
    }

    // ---------------- 2. 목록 ----------------
    private void listMembers() {
        List<Member> all = repo.findAll();
        if (all.isEmpty()) { System.out.println("불러온 부원이 없습니다. 1번을 먼저 실행하세요."); return; }
        System.out.printf("%-10s %-6s %-5s %-14s %-4s %s%n", "학번", "이름", "레벨", "반", "구분", "가능 시간");
        for (StudyClass c : StudyClass.values()) {
            for (Member m : all) if (m.getStudyClass() == c && m.isStaff()) System.out.println(m);
            for (Member m : all) if (m.getStudyClass() == c && !m.isStaff()) System.out.println(m);
        }
        if (!repo.getErrors().isEmpty()) {
            System.out.println("(오류로 제외된 줄 " + repo.getErrors().size() + "개 — 1번에서 확인)");
        }
    }

    // ---------------- 3, 4. 편성 ----------------
    private void runMatching() {
        if (repo.findAll().isEmpty()) { System.out.println("불러온 부원이 없습니다. 1번을 먼저 실행하세요."); return; }
        lastResult = matcher.match(repo.findAll());
        System.out.println(lastResult.toReport());
        System.out.println("※ 결과를 엑셀 파일로 남기려면 6번을 실행하세요.");
    }

    private void showResult() {
        if (lastResult == null) { System.out.println("편성 결과가 없습니다. 3번을 먼저 실행하세요."); return; }
        System.out.println(lastResult.toReport());
    }

    // ---------------- 5. 추천 / 이동 ----------------
    private void recommendAndMove() {
        if (lastResult == null) { System.out.println("편성 결과가 없습니다. 3번을 먼저 실행하세요."); return; }
        System.out.print("학번: ");
        Member m = repo.findById(sc.nextLine().trim());
        if (m == null) { System.out.println("해당 학번의 부원이 없습니다."); return; }
        if (m.isStaff()) {
            System.out.println("운영진은 담당 조 시간이 기준입니다. 엑셀에서 시간을 고친 뒤 1번 → 3번으로 재편성하세요.");
            return;
        }
        Availability schedule = m.getAvailability();
        while (true) {
            Group current = lastResult.findGroupOf(m);
            System.out.println();
            System.out.println(m.shortInfo());
            System.out.println("  현재 조 : " + (current == null ? "미배정" : current.getName() + " (" + current.getTime() + ")"));
            System.out.println("  확인 시간표: " + schedule + (schedule == m.getAvailability() ? " (등록 시간)" : " (임시 입력)"));
            if (current != null) {
                System.out.println(schedule.isFree(current.getTime())
                        ? "  → 이 시간표로 현재 조에 계속 참여할 수 있습니다."
                        : "  → 이 시간표로는 현재 조 시간에 참여할 수 없습니다.");
            }

            List<Group> groups = matcher.candidateGroups(m, schedule, lastResult);
            if (groups.isEmpty()) {
                System.out.println("같은 반에 옮겨갈 다른 조가 없습니다.");
                return;
            }
            System.out.println("같은 반의 다른 조 (참여 가능한 조 먼저, 인원 적은 순):");
            boolean anyFits = false;
            for (int i = 0; i < groups.size(); i++) {
                Group g = groups.get(i);
                boolean ok = schedule.isFree(g.getTime());
                anyFits |= ok;
                System.out.printf("  %d) %s | %s | 현재 %d명 | 운영진 %s | %s%n", i + 1, g.getName(), g.getTime(),
                        g.size(), g.getLeader().getName(), ok ? "추천 (참여 가능)" : "시간 안 맞음");
            }
            if (!anyFits) System.out.println("  ※ 이 시간표로 참여 가능한 조가 없습니다. T로 다른 시간표를 넣어 보세요.");

            System.out.println("[입력 방법]");
            System.out.println("  조 번호 입력  → 그 조로 이동     예) 1   (목록의 1번 조 " + groups.get(0).getName() + "로 이동)");
            System.out.println("  T 입력        → 바뀐 시간표로 다시 확인");
            System.out.println("  Enter         → 이동하지 않고 끝내기");
            System.out.print("입력> ");
            String in = sc.nextLine().trim();
            if (in.isEmpty()) return;
            if (in.equalsIgnoreCase("T")) {
                Availability changed = readSchedule();
                if (changed != null) schedule = changed;
                continue;
            }
            Group target;
            try {
                target = groups.get(Integer.parseInt(in) - 1);
            } catch (NumberFormatException | IndexOutOfBoundsException e) {
                System.out.println("잘못된 입력입니다. 1~" + groups.size() + " 사이의 번호, T, 또는 Enter를 입력하세요.");
                continue;
            }
            boolean ok = schedule.isFree(target.getTime());
            if (!ok && !readYesNo(target.getName() + "는 시간이 맞지 않습니다. 그래도 이동할까요? (Y/N): ")) continue;
            lastResult.move(m, target, ok);
            System.out.println(m.getName() + " → " + target.getName() + " 이동 완료 (" + target.getTime() + ")");
            if (current != null && current.isUnderMinimum()) {
                System.out.println("※ " + current.getName() + "가 " + current.size() + "명이 되어 최소 인원 미달입니다.");
            }
            if (schedule != m.getAvailability()) {
                System.out.println("※ 바뀐 시간표는 엑셀(" + MEMBER_FILE + ")에도 반영해 두세요.");
            }
            System.out.println("※ 이동 내용을 파일에 남기려면 6번으로 저장하세요.");
            return;
        }
    }

    /** 바뀐 시간표를 임시로 입력받음. Enter면 취소(null) */
    private Availability readSchedule() {
        System.out.println("[바뀐 시간표 입력 방법] 요일 시작-끝 형식, 30분 단위, 09:00~23:00");
        System.out.println("  예) 토 13:00-15:00");
        System.out.println("  예) 월 19:00-21:00, 수 18:30-20:00, 토 13:00-15:00   (여러 개는 쉼표로 구분)");
        System.out.println("  Enter → 취소");
        while (true) {
            System.out.print("시간표: ");
            String in = sc.nextLine().trim();
            if (in.isEmpty()) return null;
            try {
                Availability av = Availability.parse(in);
                if (!av.isEmpty()) return av;
                System.out.println("최소 한 개 이상 입력하세요.");
            } catch (IllegalArgumentException e) {
                System.out.println("입력 오류 - " + e.getMessage());
                System.out.println("  다시 입력하세요. 예) 월 19:00-21:00, 토 13:00-15:00");
            }
        }
    }

    private boolean readYesNo(String prompt) {
        while (true) {
            System.out.print(prompt);
            String in = sc.nextLine().trim();
            if (in.equalsIgnoreCase("Y")) return true;
            if (in.equalsIgnoreCase("N")) return false;
        }
    }

    // ---------------- 6. 결과 저장 ----------------
    private void saveResult() {
        if (lastResult == null) { System.out.println("편성 결과가 없습니다. 3번을 먼저 실행하세요."); return; }
        Path csv = Paths.get(RESULT_CSV);
        try {
            Files.write(csv, lastResult.toCsv().getBytes(StandardCharsets.UTF_8));
            System.out.println("편성 결과 저장 완료: " + csv.toAbsolutePath());
        } catch (IOException e) {
            System.out.println("저장 실패 (result.csv가 엑셀에서 열려 있으면 닫고 다시 시도): " + e.getMessage());
        }
    }
}
