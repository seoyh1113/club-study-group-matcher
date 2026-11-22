import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 조 편성 알고리즘.
 *
 * 규칙
 *  1. 반(N1~N2 / N3~N5)별로 따로 편성한다.
 *  2. 조마다 운영진 1명이 담당하며, 운영진은 담당 조에서 함께 공부한다.
 *  3. 스터디 시간은 같은 요일의 연속 1시간(30분 2칸)으로 정한다.
 *  4. 부원은 그 조 시간에 가능한 경우에만 배정한다.
 *  5. 모두를 조건대로 배정할 수 없으면 "조 인원 균형"을 우선한다.
 *
 * 순서
 *  (1) 운영진마다 조 시간 1차 결정: 아직 다른 조로 커버되지 않은 부원을 가장 많이 품는 1시간 구간 선택
 *  (2) 조 시간 조합 비교: (1)의 조합에서 시작해 다른 조합을 시험 배정해 보고 더 나은 조합으로 교체
 *      - 비교 순서: 미배정 부원 수 → 인원 미달 조 수 → 가장 큰 조와 작은 조의 인원 차이 (모두 적을수록 좋음)
 *      - 조합 수가 COMBINATION_LIMIT 이하면 모두 비교, 넘으면 한 조씩 시간을 바꿔 보며 개선
 *      - 더 나은 조합이 없으면 (1)의 조합을 그대로 쓴다
 *  (3) 부원 배정: 갈 수 있는 조가 적은 부원부터, 가능한 조 중 인원이 가장 적은 조로
 *  (4) 균형 조정: 큰 조에서 작은 조로 옮길 수 있는 부원을 옮겨 인원 차이를 줄임
 */
public class GroupMatcher {
    public static final int MIN_GROUP_SIZE = 5;  // 운영진 포함
    public static final int STUDY_SLOTS = 2;     // 1시간 = 30분 x 2
    /** 조 시간 조합을 모두 비교하는 최대 개수. 넘으면 한 조씩 바꿔 보며 개선 (동아리 규모에서는 거의 모두 비교) */
    public static final long COMBINATION_LIMIT = 100_000;

    /** 편성 결과 앞에 출력할 편성 기준 설명 */
    public static final String[] RULES = {
        "N1~N2 희망자는 심화조, N3~N5 희망자는 기본조로 나눠서 편성합니다.",
        "운영진 1명이 조 1개를 맡고, 운영진도 그 조에서 같이 공부합니다.",
        "스터디 시간은 1시간이며, 조장인 운영진의 스터디 가능 시간 중에서 정합니다.",
        "조 시간은 반마다 가능한 조합을 비교해, 미배정 부원이 가장 적은 조합으로 정합니다. (같으면 인원 미달 조가 적고 조 인원이 고른 조합)",
        "부원은 참여 가능한 시간의 조에만 편성됩니다.",
        "배정 가능한 조의 수가 적은 부원부터 먼저 조에 배치합니다.",
        "배정 가능한 조가 여러 개면 사람이 가장 적은 조에 배치됩니다.",
        "배정이 완료된 뒤 조끼리 인원 차이가 큰 경우 다른 시간도 가능한 부원을 옮겨 인원을 맞춥니다.",
        "운영진 포함 5명이 안 되는 조는 따로 알려 줍니다. (심화조는 고급자가 적어 5명 미만도 허용)"
    };

    public MatchResult match(List<Member> all) {
        MatchResult result = new MatchResult();
        for (StudyClass sc : StudyClass.values()) {
            matchClass(sc, all, result);
        }
        return result;
    }

    private void matchClass(StudyClass sc, List<Member> all, MatchResult result) {
        List<Member> staff = new ArrayList<>();
        List<Member> normal = new ArrayList<>();
        for (Member m : all) {
            if (m.getStudyClass() != sc) continue;
            if (m.isStaff()) staff.add(m); else normal.add(m);
        }
        if (staff.isEmpty() && normal.isEmpty()) return;

        String cls = sc.getDisplayName();
        result.getNotes().add(cls + ": 운영진 " + staff.size() + "명, 부원 " + normal.size()
                + "명 → 운영진 1명당 1개씩, 최대 " + staff.size() + "개 조");

        if (staff.isEmpty()) {
            for (Member m : normal) result.getUnassigned().put(m, sc.getLabel() + "에 운영진이 없음");
            result.getWarnings().add(sc.getLabel() + ": 담당 운영진이 없어 조를 만들 수 없습니다.");
            return;
        }

        // (1) 운영진별 조 시간 1차 결정 — 가능한 시간이 적은 운영진부터 고른다
        Map<Member, List<TimeWindow>> windows = new HashMap<>();
        for (Member s : staff) windows.put(s, candidateWindows(s));
        staff.sort(Comparator.comparingInt(s -> windows.get(s).size()));
        List<Member> leaders = new ArrayList<>();
        List<TimeWindow> times = new ArrayList<>();
        for (Member leader : staff) {
            List<TimeWindow> candidates = windows.get(leader);
            if (candidates.isEmpty()) {
                result.getWarnings().add(leader.shortInfo() + ": 연속 1시간 이상 가능한 시간이 없어 조를 맡지 못함");
                // 결과·CSV에서 빠지지 않도록 미배정 목록에 남긴다 (시험 AC-12에서 발견)
                result.getUnassigned().put(leader, "운영진이지만 연속 1시간 이상 가능한 시간이 없음");
                continue;
            }
            TimeWindow best = null;
            int bestAvail = -1, bestNew = -1;
            for (TimeWindow w : candidates) {
                int available = 0, uncovered = 0;
                for (Member m : normal) {
                    if (!m.isAvailable(w)) continue;
                    available++;
                    if (!coveredByOthers(m, times)) uncovered++;
                }
                if (uncovered > bestNew || (uncovered == bestNew && available > bestAvail)) {
                    best = w; bestNew = uncovered; bestAvail = available;
                }
            }
            leaders.add(leader);
            times.add(best);
        }
        if (leaders.isEmpty()) {
            for (Member m : normal) result.getUnassigned().put(m, "조를 맡을 수 있는 운영진이 없음");
            return;
        }

        // (2) 조 시간 조합 비교 — 1차 조합보다 나은 조합이 있으면 바꾼다
        Plan first = assign(sc, leaders, times, normal);
        Plan plan = first;
        List<List<TimeWindow>> options = new ArrayList<>();
        long combos = 1;
        for (Member leader : leaders) {
            options.add(windows.get(leader));
            combos = Math.min(combos * windows.get(leader).size(), Long.MAX_VALUE / 100);
        }
        boolean exhaustive = combos <= COMBINATION_LIMIT;
        plan = exhaustive ? searchAll(sc, leaders, options, normal, plan, combos)
                   : searchStepwise(sc, leaders, options, normal, plan);
        if (plan == first) {
            result.getNotes().add(cls + ": 조 시간 조합 " + (exhaustive ? combos + "가지를 모두" : "을 한 조씩 바꿔 가며")
                    + " 비교했지만 1차로 고른 시간이 가장 좋아 그대로 사용");
        } else {
            result.getNotes().add(cls + ": 조 시간 조합 " + (exhaustive ? combos + "가지를 모두" : "을 한 조씩 바꿔 가며")
                    + " 비교해 시간 변경 → 미배정 " + first.unassigned.size() + "명 → " + plan.unassigned.size()
                    + "명, 인원 미달 조 " + first.underCount() + "개 → " + plan.underCount() + "개");
        }

        // 조 이름·시간 선정 이유
        int seq = 1;
        for (int i = 0; i < plan.groups.size(); i++) {
            Group g = plan.groups.get(i);
            g.setName(sc.getGroupType() + (seq++) + "조");
            int available = 0;
            for (Member m : normal) if (m.isAvailable(g.getTime())) available++;
            boolean changed = plan.times.get(i) != first.times.get(i);
            g.setReason(String.format(changed
                    ? "조장 %s의 스터디 가능 시간 중, 다른 조 시간과 함께 비교했을 때 미배정 부원이 가장 적어지는 시간 (참여 가능 부원 %d명)"
                    : "조장 %s의 스터디 가능 시간 중, 조가 정해지지 않은 부원이 가장 많이 참여할 수 있는 시간 (참여 가능 부원 %d명)",
                    g.getLeader().getName(), available));
        }
        for (Member m : plan.unassigned) {
            result.getUnassigned().put(m, m.getAvailability().isEmpty()
                    ? "가능 시간 미입력" : "어느 조 시간에도 참여할 수 없음 (등록 시간: " + m.getAvailability() + ")");
        }
        result.getNotes().add(cls + ": 배정 가능한 조가 1개뿐인 부원 " + plan.onlyOne
                + "명을 먼저 배치하고, 나머지는 사람이 적은 조부터 배치");
        result.getNotes().add(cls + ": 조끼리 인원을 맞추려고 옮긴 부원 " + plan.moved + "명");

        // 조 인원 경고는 저장하지 않고 MatchResult가 출력할 때마다 계산한다 (시험 AC-14에서 발견)
        result.getGroups().addAll(plan.groups);
    }

    /** 조 시간 조합 하나로 시험 배정한 결과 */
    private static class Plan {
        List<TimeWindow> times;
        List<Group> groups = new ArrayList<>();
        List<Member> unassigned = new ArrayList<>();
        int onlyOne, moved;

        int underCount() {
            int n = 0;
            for (Group g : groups) if (g.isUnderMinimum()) n++;
            return n;
        }

        int spread() {
            int max = 0, min = Integer.MAX_VALUE;
            for (Group g : groups) { max = Math.max(max, g.size()); min = Math.min(min, g.size()); }
            return max - min;
        }

        /** 미배정 → 인원 미달 조 → 인원 차이 순으로 비교해 이 조합이 더 좋으면 true */
        boolean betterThan(Plan o) {
            if (unassigned.size() != o.unassigned.size()) return unassigned.size() < o.unassigned.size();
            if (underCount() != o.underCount()) return underCount() < o.underCount();
            return spread() < o.spread();
        }
    }

    /** 정해진 조 시간으로 (3) 부원 배정 + (4) 균형 조정 */
    private Plan assign(StudyClass sc, List<Member> leaders, List<TimeWindow> times, List<Member> normal) {
        Plan p = new Plan();
        p.times = new ArrayList<>(times);
        for (int i = 0; i < leaders.size(); i++) p.groups.add(new Group("", sc, leaders.get(i), times.get(i)));

        // (3) 부원 배정 — 선택지가 적은 부원부터 (선택지 수는 한 번만 계산)
        Map<Member, List<Group>> feasible = new HashMap<>();
        for (Member m : normal) feasible.put(m, feasibleGroups(m, p.groups));
        List<Member> order = new ArrayList<>(normal);
        order.sort(Comparator.comparingInt(m -> feasible.get(m).size()));
        for (Member m : order) {
            List<Group> list = feasible.get(m);
            if (list.isEmpty()) { p.unassigned.add(m); continue; }
            if (list.size() == 1) p.onlyOne++;
            Group smallest = list.get(0);
            for (Group g : list) if (g.size() < smallest.size()) smallest = g;
            smallest.add(m);
        }
        // (4) 인원 균형 조정
        p.moved = rebalance(p.groups);
        return p;
    }

    /** 모든 조합 비교 (조합 번호를 운영진별 후보 번호로 풀어서 사용) */
    private Plan searchAll(StudyClass sc, List<Member> leaders, List<List<TimeWindow>> options,
                           List<Member> normal, Plan best, long combos) {
        for (long k = 0; k < combos; k++) {
            List<TimeWindow> t = new ArrayList<>();
            long x = k;
            for (List<TimeWindow> opt : options) { t.add(opt.get((int) (x % opt.size()))); x /= opt.size(); }
            Plan p = assign(sc, leaders, t, normal);
            if (p.betterThan(best)) best = p;
        }
        return best;
    }

    /** 조합이 너무 많을 때: 한 조씩 시간을 바꿔 보고 나아지면 받아들이기를 더 나아지지 않을 때까지 반복 */
    private Plan searchStepwise(StudyClass sc, List<Member> leaders, List<List<TimeWindow>> options,
                                List<Member> normal, Plan best) {
        boolean improved = true;
        for (int round = 0; improved && round < 50; round++) {
            improved = false;
            for (int i = 0; i < leaders.size(); i++) {
                for (TimeWindow w : options.get(i)) {
                    List<TimeWindow> t = new ArrayList<>(best.times);
                    t.set(i, w);
                    Plan p = assign(sc, leaders, t, normal);
                    if (p.betterThan(best)) { best = p; improved = true; }
                }
            }
        }
        return best;
    }

    private boolean coveredByOthers(Member m, List<TimeWindow> decided) {
        for (TimeWindow w : decided) if (m.isAvailable(w)) return true;
        return false;
    }

    /** 운영진이 가능한 모든 1시간 구간 */
    private static List<TimeWindow> candidateWindows(Member m) {
        List<TimeWindow> list = new ArrayList<>();
        for (int d = 0; d < Availability.DAYS.length; d++) {
            for (int s = 0; s + STUDY_SLOTS <= Availability.SLOTS; s++) {
                if (m.getAvailability().isFree(d, s, STUDY_SLOTS)) list.add(new TimeWindow(d, s, STUDY_SLOTS));
            }
        }
        return list;
    }

    /** 부원이 참여 가능한 조 (groups는 같은 반의 조만 들어옴) */
    private static List<Group> feasibleGroups(Member m, List<Group> groups) {
        List<Group> list = new ArrayList<>();
        for (Group g : groups) if (m.isAvailable(g.getTime())) list.add(g);
        return list;
    }

    /** 인원 차이가 2명 이상 나고 옮길 수 있는 부원이 있으면 큰 조 → 작은 조로 이동. 이동 인원 반환 */
    private static int rebalance(List<Group> groups) {
        int count = 0;
        boolean moved = true;
        int guard = 0;
        while (moved && guard++ < 1000) {
            moved = false;
            for (Group from : groups) {
                for (Member m : new ArrayList<>(from.getMembers())) {
                    for (Group to : groups) {
                        if (to == from || to.size() + 1 >= from.size()) continue;
                        if (!m.isAvailable(to.getTime())) continue;
                        from.remove(m);
                        to.add(m);
                        moved = true;
                        count++;
                        break;
                    }
                }
            }
        }
        return count;
    }

    /** 같은 반의 다른 조 전체 (현재 소속 조 제외). 시간 맞는 조 먼저, 그 안에서 인원 적은 순 */
    public List<Group> candidateGroups(Member m, Availability schedule, MatchResult result) {
        List<Group> list = new ArrayList<>();
        for (Group g : result.getGroups()) {
            if (!g.contains(m) && g.getStudyClass() == m.getStudyClass()) list.add(g);
        }
        list.sort(Comparator.comparing((Group g) -> !schedule.isFree(g.getTime())).thenComparingInt(Group::size));
        return list;
    }
}
