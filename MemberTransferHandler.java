import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

/**
 * 조원 목록(JList)끼리 부원을 드래그해서 옮기는 기능.
 * - 모든 조 목록과 미배정 목록이 이 객체 하나를 같이 사용한다.
 * - 각 JList에는 putClientProperty(GROUP_KEY, 조)로 "이 목록이 어느 조인지"를 붙여 둔다. (미배정 목록은 null)
 * - 실제 이동(시간 확인, 기록 남기기)은 ClubPanel.moveMember가 담당한다.
 */
@SuppressWarnings("serial")
public class MemberTransferHandler extends TransferHandler {
    public static final String GROUP_KEY = "group";

    private static final DataFlavor MEMBER_FLAVOR = createFlavor();

    private final ClubPanel panel;
    /** 지금 끌고 있는 부원. 같은 프로그램 안에서만 옮기므로 필드에 보관 */
    private Member dragging;

    public MemberTransferHandler(ClubPanel panel) {
        this.panel = panel;
    }

    private static DataFlavor createFlavor() {
        try {
            return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=Member");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int getSourceActions(JComponent c) {
        return MOVE;
    }

    /** 드래그 시작: 선택한 부원을 기억. 운영진(조장)은 옮길 수 없음 */
    @Override
    protected Transferable createTransferable(JComponent c) {
        @SuppressWarnings("unchecked")
        JList<Member> list = (JList<Member>) c;
        Member m = list.getSelectedValue();
        if (m == null || m.isStaff()) return null;
        dragging = m;
        panel.dragStarted();   // 화면 끝으로 가면 자동 스크롤 시작
        return new MemberTransferable(m);
    }

    /** 놓을 수 있는 곳인지: 같은 반의 다른 조, 또는 미배정 목록 */
    @Override
    public boolean canImport(TransferSupport support) {
        if (!support.isDrop() || dragging == null) return false;
        if (!support.isDataFlavorSupported(MEMBER_FLAVOR)) return false;
        Group target = targetGroup(support);
        Group current = panel.groupOf(dragging);
        if (target == current) return false;                       // 같은 조로는 이동 안 함
        if (target != null && target.getStudyClass() != dragging.getStudyClass()) return false; // 다른 반 불가
        return true;
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) return false;
        Member m = dragging;
        Group target = targetGroup(support);
        // 확인 창은 드래그가 완전히 끝난 다음에 띄운다 (드래그 도중 창을 띄우면 멈출 수 있음)
        SwingUtilities.invokeLater(() -> panel.moveMember(m, target));
        return true;
    }

    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
        // 목록 갱신은 ClubPanel.refresh()가 결과(MatchResult)를 기준으로 다시 그리므로 여기서는 정리만
        dragging = null;
        panel.dragEnded();
    }

    private static Group targetGroup(TransferSupport support) {
        JComponent c = (JComponent) support.getComponent();
        return (Group) c.getClientProperty(GROUP_KEY);
    }

    /** 드래그로 전달되는 데이터 (부원 객체 그대로) */
    private static class MemberTransferable implements Transferable {
        private final Member member;

        MemberTransferable(Member member) { this.member = member; }

        @Override
        public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{MEMBER_FLAVOR}; }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) { return MEMBER_FLAVOR.equals(flavor); }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
            return member;
        }
    }
}
