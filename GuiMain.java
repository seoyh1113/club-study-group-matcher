import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * 일본어 동아리 스터디 조 편성 프로그램 (GUI)
 * - 콘솔 버전(Main)과 같은 편성 로직(GroupMatcher)을 그대로 사용하고 화면만 바꾼 것
 * 실행: javac -encoding UTF-8 *.java  →  java GuiMain
 */
public class GuiMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Windows에서는 Windows 모양 버튼/파일 선택 창 사용
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // 실패하면 기본 모양으로 실행
            }
            Theme.install();   // 글꼴·툴팁 모양을 앱 디자인에 맞춤
            JFrame frame = new JFrame("일본어 동아리 스터디 조 편성");
            ClubPanel panel = new ClubPanel();
            frame.setContentPane(panel);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1440, 900);
            frame.setMinimumSize(new java.awt.Dimension(1100, 700));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
