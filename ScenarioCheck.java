import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 완료 기준(AC-01~AC-22)을 코드로 확인하는 시험 프로그램 (JUnit 없이 실행).
 * 실행: javac -encoding UTF-8 *.java  →  java ScenarioCheck
 * 결과: 기준마다 [통과]/[실패]와 실제 값을 출력
 */
public class ScenarioCheck {
    private static int pass = 0, fail = 0;

    public static void main(String[] args) throws IOException {
        acParse();
        acParseErrors();
        acLoadSample();
        acLoadErrors();
        acEncoding();
        acQuotedTimes();
        acScenario();
        acBalance();
        acNoOverlap();
        acHalfHourOnly();
        acNoStaff();
        acStaffNoWindow();
        acMinimum();
        acMove();
        acMoveMismatch();
        acCandidates();
        acCsv();
        acRename();
        acRenameReject();
        acUnassign();
        acRosterDiff();
        acCombination();
        System.out.println();
        System.out.println("통과 " + pass + "개 / 실패 " + fail + "개");
    }

    // ---------- 도우미 ---------- (번호는 개발 문서 2.4 완료 기준 번호와 같음)
    private static void check(String id, String desc, boolean ok, String actual) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "[통과] " : "[실패] ") + id + " " + desc + (ok ? "" : "  → 실제: " + actual));
    }

    private static Member m(String id, String name, String cls, boolean staff, String times) {
        return new Member(id, name, Level.BEGINNER, StudyClass.fromText(cls), staff, Availability.parse(times));
    }

    private static MemberRepository load(String content, Charset cs) throws IOException {
        Path p = Files.createTempFile("members", ".csv");
        Files.write(p, content.getBytes(cs));
        MemberRepository r = new MemberRepository();
        r.load(p);
        return r;
    }

    private static Group groupOf(MatchResult r, String name) {
        for (Group g : r.getGroups()) if (g.getLeader().getName().equals(name)) return g;
        return null;
    }

    private static String names(Group g) {
        List<String> n = new ArrayList<>();
        for (Member x : g.getMembers()) n.add(x.getName());
        return g.getTime() + " " + g.size() + "명 " + n;
    }

    // ---------- 불러오기 ----------
    static void acParse() {
        Availability a = Availability.parse("화 10:00-12:00; 화 18:00-21:00");
        boolean ok = a.isFree(1, 2, 4)            // 화 10:00-12:00
                && a.isFree(1, 18, 6)              // 화 18:00-21:00
                && !a.isFree(1, 5, 2)              // 화 11:30-12:30 불가
                && !a.isFree(1, 23, 2);            // 화 20:30-21:30 불가
        check("AC-02", "하루 두 구간을 30분 칸으로 저장, 구간 밖은 불가", ok, a.toText());
    }

    static void acParseErrors() {
        String[] bad = {"화 21:00-19:00", "화 08:00-10:00", "화요일 저녁", "화 18:15-19:00", "금 19:00-24:00"};
        List<String> notRejected = new ArrayList<>();
        for (String b : bad) {
            try { Availability.parse(b); notRejected.add(b); } catch (IllegalArgumentException e) { /* 거절 */ }
        }
        check("AC-06", "잘못된 시간 5종(역순·범위 밖·형식·30분 아님·23시 초과) 거절", notRejected.isEmpty(),
                "거절 안 됨 " + notRejected);
    }

    static void acLoadSample() throws IOException {
        MemberRepository r = new MemberRepository();
        r.load(Path.of("sample_members.csv"));
        int s1 = 0, n1 = 0, s2 = 0, n2 = 0;
        for (Member x : r.findAll()) {
            if (x.getStudyClass() == StudyClass.N1_N2) { if (x.isStaff()) s1++; else n1++; }
            else { if (x.isStaff()) s2++; else n2++; }
        }
        boolean ok = r.findAll().size() == 42 && r.getErrors().isEmpty() && s1 == 2 && n1 == 8 && s2 == 6 && n2 == 26;
        check("AC-01", "샘플 42명 등록 (심화 운영진2·부원8 / 기본 운영진6·부원26)", ok,
                r.findAll().size() + "명, 오류 " + r.getErrors().size());
    }

    static void acLoadErrors() throws IOException {
        String csv = "학번,이름,레벨,반,운영진,가능시간\n"
                + "202400001,정상,초급,N3~N5,N,월 19:00-21:00\n"
                + "abc,학번오류,초급,N3~N5,N,월 19:00-21:00\n"
                + "202400001,중복,초급,N3~N5,N,월 19:00-21:00\n"
                + "202400003,레벨오류,초보,N3~N5,N,월 19:00-21:00\n"
                + "202400004,반오류,초급,회화,N,월 19:00-21:00\n"
                + "202400005,운영진오류,초급,N3~N5,예,월 19:00-21:00\n"
                + "202400006,시간없음,초급,N3~N5,N,\n"
                + "202400007,,초급,N3~N5,N,월 19:00-21:00\n"
                + ",,,,,\n";
        MemberRepository r = load(csv, StandardCharsets.UTF_8);
        boolean lineNo = r.getErrors().size() == 7 && r.getErrors().get(0).startsWith("3번째 줄");
        check("AC-05", "잘못된 줄 7개는 줄 번호·사유와 함께 제외, 정상 1명만 등록, 빈 줄 무시",
                r.findAll().size() == 1 && lineNo, r.findAll().size() + "명 / " + r.getErrors());
    }

    static void acEncoding() throws IOException {
        String csv = "학번,이름,레벨,반,운영진,가능시간\n202400001,김가람,초급,N3~N5,N,월 19:00-21:00\n";
        MemberRepository cp = load(csv, Charset.forName("MS949"));
        MemberRepository bom = load("﻿" + csv, StandardCharsets.UTF_8);
        boolean ok = cp.findAll().size() == 1 && cp.findAll().get(0).getName().equals("김가람")
                && cp.getEncoding().startsWith("CP949")
                && bom.findAll().size() == 1 && bom.getEncoding().equals("UTF-8");
        check("AC-04", "엑셀 기본 CSV(CP949)와 UTF-8(BOM) 모두 한글 깨짐 없이 읽음", ok,
                cp.getEncoding() + " / " + bom.getEncoding());
    }

    static void acQuotedTimes() throws IOException {
        String csv = "학번,이름,레벨,반,운영진,가능시간\n"
                + "202400001,따옴표,초급,N3~N5,N,\"월 19:00-21:00, 수 18:00-20:00\"\n"
                + "202400002,쉼표,초급,N3~N5,N,월 19:00-21:00, 수 18:00-20:00\n";
        MemberRepository r = load(csv, StandardCharsets.UTF_8);
        boolean ok = r.findAll().size() == 2;
        for (Member x : r.findAll()) ok &= x.getAvailability().isFree(2, 18, 2);
        check("AC-03", "가능 시간을 따옴표로 감싸거나 쉼표로 이어 써도 한 사람으로 읽음", ok, r.getErrors().toString());
    }

    // ---------- 자동 편성 ----------
    static void acScenario() {
        List<Member> all = new ArrayList<>();
        all.add(m("100", "A", "N3~N5", true, "화 18:00-19:00"));
        all.add(m("101", "B", "N3~N5", true, "목 19:00-20:00"));
        for (int i = 1; i <= 6; i++) all.add(m("" + i, "부원" + i, "N3~N5", false, "화 18:00-19:00"));
        all.add(m("7", "부원7", "N3~N5", false, "화 18:00-19:00; 목 19:00-20:00"));
        all.add(m("8", "부원8", "N3~N5", false, "목 10:00-12:00; 목 19:00-21:00"));
        all.add(m("9", "부원9", "N3~N5", false, "목 19:00-20:00"));
        all.add(m("10", "부원10", "N3~N5", false, "목 19:00-20:00"));
        all.add(m("11", "부원11", "N3~N5", false, "일 10:00-11:00"));
        MatchResult r = new GroupMatcher().match(all);
        Group a = groupOf(r, "A"), b = groupOf(r, "B");
        boolean ok = a != null && b != null && a.size() == 7 && b.size() == 5
                && b.getMembers().stream().anyMatch(x -> x.getName().equals("부원7"))
                && a.getTime().toString().equals("화 18:00-19:00")
                && r.getUnassigned().size() == 1
                && r.getUnassigned().keySet().iterator().next().getName().equals("부원11");
        check("AC-07", "대표 시나리오: A조 7명(화18), B조 5명(부원7 포함, 목19), 부원11 미배정", ok,
                (a == null ? "-" : names(a)) + " / " + (b == null ? "-" : names(b)) + " / 미배정 " + r.getUnassigned().size());
    }

    static void acBalance() {
        List<Member> all = new ArrayList<>();
        all.add(m("100", "C", "N3~N5", true, "화 18:00-19:00"));
        all.add(m("101", "D", "N3~N5", true, "화 18:00-19:00"));
        for (int i = 1; i <= 8; i++) all.add(m("" + i, "부원" + i, "N3~N5", false, "화 18:00-19:00"));
        MatchResult r = new GroupMatcher().match(all);
        boolean ok = r.getGroups().size() == 2 && r.getGroups().get(0).size() == 5 && r.getGroups().get(1).size() == 5;
        check("AC-09", "같은 시간 운영진 2명·부원 8명 → 5명/5명", ok,
                r.getGroups().size() + "개 조 " + (r.getGroups().isEmpty() ? "" : names(r.getGroups().get(0))));
    }

    static void acNoOverlap() {
        List<Member> all = new ArrayList<>();
        all.add(m("100", "E", "N3~N5", true, "화 18:00-20:00"));
        all.add(m("1", "부원1", "N3~N5", false, "화 18:00-19:00"));
        all.add(m("2", "부원2", "N3~N5", false, "화 19:00-20:00"));
        all.add(m("3", "부원3", "N3~N5", false, "화 18:00-19:00"));
        MatchResult r = new GroupMatcher().match(all);
        Group g = r.getGroups().get(0);
        boolean together = g.getMembers().stream().anyMatch(x -> x.getName().equals("부원1"))
                && g.getMembers().stream().anyMatch(x -> x.getName().equals("부원2"));
        check("AC-08", "겹치는 시간이 없는 두 부원은 같은 조가 되지 않음 (조 시간 화18, 부원2 미배정)",
                !together && g.getTime().toString().equals("화 18:00-19:00") && r.getUnassigned().size() == 1,
                names(g));
    }

    static void acHalfHourOnly() {
        List<Member> all = new ArrayList<>();
        all.add(m("100", "F", "N3~N5", true, "일 10:00-12:00"));
        all.add(m("1", "부원1", "N3~N5", false, "일 10:00-10:30"));
        MatchResult r = new GroupMatcher().match(all);
        String reason = r.getUnassigned().values().stream().findFirst().orElse("");
        check("AC-10", "30분만 가능한 부원은 미배정, 사유 \"어느 조 시간에도 참여할 수 없음\"",
                reason.startsWith("어느 조 시간에도 참여할 수 없음"), reason);
    }

    static void acNoStaff() {
        List<Member> all = new ArrayList<>();
        all.add(m("1", "부원1", "N1~N2", false, "월 19:00-21:00"));
        all.add(m("2", "부원2", "N1~N2", false, "월 19:00-21:00"));
        MatchResult r = new GroupMatcher().match(all);
        boolean ok = r.getGroups().isEmpty() && r.getUnassigned().size() == 2
                && r.getUnassigned().values().iterator().next().contains("운영진이 없음")
                && !r.getWarnings().isEmpty();
        check("AC-11", "운영진 없는 반은 조를 만들지 않고 부원 전원 미배정 + 경고", ok, r.getUnassigned().toString());
    }

    static void acStaffNoWindow() {
        List<Member> all = new ArrayList<>();
        all.add(m("100", "G", "N3~N5", true, "월 19:00-19:30"));   // 1시간 연속 없음
        all.add(m("101", "H", "N3~N5", true, "화 18:00-19:00"));
        for (int i = 1; i <= 4; i++) all.add(m("" + i, "부원" + i, "N3~N5", false, "화 18:00-19:00"));
        MatchResult r = new GroupMatcher().match(all);
        String csv = r.toCsv();
        boolean inResult = r.findGroupOf(all.get(0)) != null || r.getUnassigned().containsKey(all.get(0));
        check("AC-12", "1시간 연속 가능 시간이 없는 운영진은 경고 + 미배정 목록·결과 CSV에 남음",
                inResult && csv.contains(",G,") && !r.getWarnings().isEmpty(),
                "미배정 목록 포함 " + inResult + ", CSV 포함 " + csv.contains(",G,"));
    }

    static void acMinimum() {
        List<Member> all = new ArrayList<>();
        all.add(m("100", "I", "N3~N5", true, "화 18:00-19:00"));
        for (int i = 1; i <= 3; i++) all.add(m("" + i, "기본" + i, "N3~N5", false, "화 18:00-19:00"));
        all.add(m("200", "J", "N1~N2", true, "수 18:00-19:00"));
        for (int i = 1; i <= 3; i++) all.add(m("2" + i, "심화" + i, "N1~N2", false, "수 18:00-19:00"));
        MatchResult r = new GroupMatcher().match(all);
        String rep = r.toReport();
        boolean ok = r.allWarnings().size() == 1 && r.allWarnings().get(0).contains("최소 인원")
                && rep.contains("※ 5명 미만") && rep.contains("심화조라 허용");
        check("AC-13", "기본조 4명은 최소 인원 경고, 심화조 4명은 \"허용\" 표시만", ok, r.allWarnings().toString());
    }

    // ---------- 조 이동 ----------
    private static List<Member> moveData() {
        List<Member> all = new ArrayList<>();
        all.add(m("100", "K", "N3~N5", true, "화 18:00-19:00"));
        all.add(m("101", "L", "N3~N5", true, "목 19:00-20:00"));
        for (int i = 1; i <= 5; i++) all.add(m("" + i, "화" + i, "N3~N5", false, "화 18:00-19:00"));
        for (int i = 1; i <= 3; i++) all.add(m("1" + i, "목" + i, "N3~N5", false, "목 19:00-20:00"));
        all.add(m("99", "늦은부원", "N3~N5", false, "일 10:00-11:00"));
        return all;
    }

    static void acMove() {
        List<Member> all = moveData();
        GroupMatcher gm = new GroupMatcher();
        MatchResult r = gm.match(all);
        Group l = groupOf(r, "L");
        Member late = all.get(all.size() - 1);
        boolean warnedBefore = r.allWarnings().stream().anyMatch(w -> w.startsWith(l.getName()));
        r.move(late, l, true);    // 미배정 → L조 (4명 → 5명)
        String rep = r.toReport();
        boolean ok = warnedBefore && l.size() == 5 && !r.getUnassigned().containsKey(late)
                && rep.contains("미배정 → " + l.getName())
                && !rep.contains(l.getName() + ": 운영진 포함 4명");   // 이동 후 낡은 경고가 남으면 안 됨
        check("AC-14", "미배정 부원을 4명 조로 이동 → 5명, 이동 기록 표시, 최소 인원 경고 사라짐", ok,
                l.size() + "명, 경고 " + r.allWarnings());
    }

    static void acMoveMismatch() {
        List<Member> all = moveData();
        MatchResult r = new GroupMatcher().match(all);
        Group k = groupOf(r, "K"), l = groupOf(r, "L");
        Member hwa = k.getMembers().get(0);
        r.move(hwa, l, false);   // 시간이 안 맞지만 운영진 판단으로 이동
        String rep = r.toReport();
        boolean ok = l.contains(hwa) && !k.contains(hwa) && rep.contains("등록 시간과 맞지 않음, 운영진 판단으로 이동");
        check("AC-15", "시간이 안 맞는 조로 이동하면 기록에 \"등록 시간과 맞지 않음\" 표시", ok, rep);
    }

    static void acCandidates() {
        List<Member> all = moveData();
        all.add(m("102", "M", "N3~N5", true, "토 13:00-14:00"));
        all.add(m("103", "N", "N1~N2", true, "토 13:00-14:00"));
        all.add(m("14", "토부원", "N3~N5", false, "토 13:00-14:00"));
        GroupMatcher gm = new GroupMatcher();
        MatchResult r = gm.match(all);
        Member late = all.get(10);  // 늦은부원 (일 10시만 가능)
        List<Group> c = gm.candidateGroups(late, Availability.parse("토 13:00-15:00"), r);
        boolean ok = c.size() == 3 && c.get(0).getLeader().getName().equals("M")
                && c.stream().noneMatch(g -> g.getStudyClass() == StudyClass.N1_N2);
        List<String> order = new ArrayList<>();
        for (Group g : c) order.add(g.getName() + "(" + g.getTime() + ")");
        check("AC-16", "임시 시간표(토 13-15)로 추천: 같은 반 조만, 참여 가능한 조가 맨 앞", ok, order.toString());
    }

    // ---------- 저장 ----------
    static void acCsv() throws IOException {
        MemberRepository repo = new MemberRepository();
        repo.load(Path.of("sample_members.csv"));
        MatchResult r = new GroupMatcher().match(repo.findAll());
        String[] lines = r.toCsv().split("\n");
        int people = lines.length - 1;
        boolean header = lines[0].replace("﻿", "").startsWith("조,반,요일,시간,조인원,역할,학번,이름,레벨,참여가능시간,비고");
        long unassigned = 0;
        for (String line : lines) if (line.startsWith("미배정,")) unassigned++;
        check("AC-17", "result.csv: 머리글 + 한 줄에 한 사람, 42명 전원(미배정 " + r.getUnassigned().size() + "명 포함)",
                header && people == 42 && unassigned == r.getUnassigned().size(), "사람 줄 " + people);
    }

    // ---------- 12주차 추가 (GUI 기능의 판단 부분) ----------
    static void acRename() {
        MatchResult r = new GroupMatcher().match(moveData());
        Group k = groupOf(r, "K");
        String old = k.getName();
        String error = r.rename(k, "  화요일반 ");     // 앞뒤 공백은 지움
        String csv = r.toCsv();
        boolean ok = error == null && k.getName().equals("화요일반")
                && csv.contains("\n화요일반,") && !csv.contains("\n" + old + ",")
                && r.toReport().contains("조 이름 변경: " + old + " → 화요일반");
        check("AC-18", "조 이름 변경 → 보고서·result.csv에 새 이름, 이동 기록에 '조 이름 변경' 남음", ok,
                error + " / " + k.getName());
    }

    static void acRenameReject() {
        MatchResult r = new GroupMatcher().match(moveData());
        Group k = groupOf(r, "K"), l = groupOf(r, "L");
        String old = k.getName();
        String[] bad = {"", "   ", "가나다라마바사아자차카타파", "A,B", "A\"B", "미배정", l.getName()};
        List<String> accepted = new ArrayList<>();
        for (String b : bad) if (r.rename(k, b) == null) accepted.add("[" + b + "]");
        boolean ok = accepted.isEmpty() && k.getName().equals(old) && r.getChanges().isEmpty();
        check("AC-19", "빈 이름·13자·쉼표·따옴표·'미배정'·다른 조와 같은 이름은 거절, 이름과 기록 그대로", ok,
                "받아들인 이름 " + accepted);
    }

    static void acUnassign() {
        MatchResult r = new GroupMatcher().match(moveData());
        Group l = groupOf(r, "L");
        Member m = l.getMembers().get(0);
        r.unassign(m, "운영진이 직접 미배정으로 옮김");
        String csv = r.toCsv();
        boolean ok = !l.contains(m) && r.getUnassigned().containsKey(m) && l.size() == 3
                && csv.contains("미배정,시험대비 N3~N5,,,,부원," + m.getStudentId())
                && r.allWarnings().stream().anyMatch(w -> w.startsWith(l.getName()))
                && r.toReport().contains(l.getName() + " → 미배정");
        check("AC-20", "조에서 미배정으로 옮기기(GUI 드래그) → 미배정 목록·CSV·이동 기록 반영, 인원 경고 다시 계산", ok,
                l.size() + "명, " + r.allWarnings());
    }

    static void acRosterDiff() {
        List<Member> before = new ArrayList<>();
        before.add(m("1", "가", "N3~N5", false, "화 18:00-19:00"));
        before.add(m("2", "나", "N3~N5", false, "화 18:00-19:00"));
        before.add(m("3", "다", "N3~N5", false, "화 18:00-19:00"));
        List<Member> after = new ArrayList<>();
        after.add(m("1", "가", "N3~N5", false, "화 18:00-19:00"));   // 그대로
        after.add(m("2", "나", "N3~N5", false, "목 18:00-19:00"));   // 시간 수정
        after.add(m("4", "라", "N3~N5", false, "화 18:00-19:00"));   // 새로 추가
        RosterDiff d = RosterDiff.compare(before, after);
        boolean ok = !d.isFirstLoad() && d.addedCount() == 1 && d.changedCount() == 1
                && d.getRemoved().size() == 1 && d.getRemoved().get(0).getName().equals("다")
                && RosterDiff.compare(null, after).isFirstLoad();
        check("AC-21", "다시 불러오기 비교: 추가 1·수정 1·빠짐 1(다) 표시, 처음 불러오기는 비교 안 함", ok,
                "추가 " + d.addedCount() + ", 수정 " + d.changedCount() + ", 빠짐 " + d.getRemoved().size());
    }

    /** 1차로 고른 조 시간이 다른 조의 시간을 막는 경우 → 조합 비교로 바꿔 미배정을 줄임 */
    static void acCombination() {
        List<Member> all = new ArrayList<>();
        all.add(m("100", "A", "N3~N5", true, "화 18:00-19:00; 금 17:00-18:00"));  // 후보 2개 → 먼저 고름
        all.add(m("200", "B", "N3~N5", true, "화 18:00-20:00"));                  // 후보 3개 (모두 화요일)
        for (int i = 1; i <= 4; i++) all.add(m(String.valueOf(i), "부원" + i, "N3~N5", false, "화 18:00-19:00"));
        all.add(m("5", "부원5", "N3~N5", false, "금 17:00-18:00"));
        MatchResult r = new GroupMatcher().match(all);
        Group a = null, b = null;
        for (Group g : r.getGroups()) { if (g.getLeader().getName().equals("A")) a = g; else b = g; }
        boolean ok = a != null && b != null && r.getUnassigned().isEmpty()
                && a.getTime().toString().equals("금 17:00-18:00") && b.getTime().toString().equals("화 18:00-19:00")
                && b.size() == 5 && r.getNotes().stream().anyMatch(n -> n.contains("미배정 1명 → 0명"));
        check("AC-22", "1차로 고른 시간(A 화18)이 금요일 부원을 막으면 조합 비교로 A를 금17로 바꿔 미배정 1명 → 0명", ok,
                (a == null ? "-" : a.getTime() + " " + a.size() + "명") + " / " + (b == null ? "-" : b.getTime() + " " + b.size() + "명")
                + " / 미배정 " + r.getUnassigned().size() + " / " + r.getNotes());
    }
}
