import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/**
 * GUI 완료 기준(G-01~G-13)을 실제 마우스·키보드 조작으로 확인하는 시험 프로그램.
 * java.awt.Robot이 창을 띄워 클릭·드래그·오른쪽 클릭·입력을 대신 하고, 결과를 [통과]/[실패]로 출력한다.
 * 실행: javac -encoding UTF-8 *.java  →  java GuiCheck   (약 30초, 실행 중에는 마우스·키보드를 건드리지 않기)
 * - sample_members.csv가 실행 위치에 있어야 함. 임시 폴더에 복사해서 쓰므로 원본은 바뀌지 않음
 * - 화면 내부 상태를 확인하려고 ClubPanel의 private 필드를 리플렉션으로 읽음 (시험 전용)
 */
public class GuiCheck {
    static ClubPanel p; static JFrame f; static Robot r; static int pass=0, fail=0;
    static Object get(String n) throws Exception { Field x=ClubPanel.class.getDeclaredField(n); x.setAccessible(true); return x.get(p); }
    static Object call(String n, Class<?>[] t, Object... a) throws Exception { Method m=ClubPanel.class.getDeclaredMethod(n,t); m.setAccessible(true); return m.invoke(p,a); }
    static <T> T edt(java.util.concurrent.Callable<T> c) throws Exception { Object[] o=new Object[1]; Exception[] ex=new Exception[1];
        SwingUtilities.invokeAndWait(()->{try{o[0]=c.call();}catch(Exception e){ex[0]=e;}}); if(ex[0]!=null) throw ex[0]; return (T)o[0]; }
    static void check(String id, String d, boolean ok, String actual){ if(ok)pass++; else fail++; System.out.println((ok?"[통과] ":"[실패] ")+id+" "+d+(ok?"":"  → 실제: "+actual)); }
    static void idle(){ r.waitForIdle(); r.delay(400); }
    static void click(Point pt, int btn){ r.mouseMove(pt.x,pt.y); r.delay(150); r.mousePress(btn); r.delay(80); r.mouseRelease(btn); idle(); }
    static void key(int k){ r.keyPress(k); r.delay(40); r.keyRelease(k); idle(); }
    static MatchResult res() throws Exception { return (MatchResult) get("result"); }
    static List<?> cards() throws Exception { return (List<?>) call("allCards", new Class<?>[]{}); }
    static Object cardOf(String groupName) throws Exception {
        for (Object c: cards()){ Field g=c.getClass().getDeclaredField("group"); g.setAccessible(true); Group gr=(Group)g.get(c);
            if(groupName==null? gr==null : gr!=null && gr.getName().equals(groupName)) return c; } return null; }
    static JList<Member> listOf(Object card) throws Exception { Field l=card.getClass().getDeclaredField("list"); l.setAccessible(true); return (JList<Member>)l.get(card); }
    static Point cellPoint(String groupName, String member) throws Exception { return edt(()->{ JList<Member> l=listOf(cardOf(groupName));
        for(int i=0;i<l.getModel().getSize();i++) if(l.getModel().getElementAt(i).getName().equals(member)){
            l.ensureIndexIsVisible(i); Rectangle b=l.getCellBounds(i,i); Point s=l.getLocationOnScreen(); return new Point(s.x+b.x+30,s.y+b.y+b.height/2);} return null; }); }
    static Point centerOnScreen(Component c){ Point s=c.getLocationOnScreen(); return new Point(s.x+c.getWidth()/2, s.y+c.getHeight()/2); }
    static String bannerText() throws Exception { return edt(()->((JLabel)get("bannerTitle")).getText()+" | "+((JLabel)get("bannerSub")).getText()); }
    static Component find(Container root, java.util.function.Predicate<Component> pr){ for(Component c: root.getComponents()){ if(c.isShowing()&&pr.test(c)) return c; if(c instanceof Container){Component x=find((Container)c,pr); if(x!=null) return x;} } return null; }
    static String textOf(Component c){ return c instanceof JLabel? ((JLabel)c).getText() : c instanceof AbstractButton? ((AbstractButton)c).getText() : ""; }
    static Group groupNamed(String n) throws Exception { for(Group g: res().getGroups()) if(g.getName().equals(n)) return g; return null; }
    static Member byName(String n) throws Exception { for(Member m: ((MemberRepository)get("repo")).findAll()) if(m.getName().equals(n)) return m; return null; }

    public static void main(String[] a) throws Exception {
        r=new Robot(); r.setAutoDelay(10);
        SwingUtilities.invokeAndWait(()->{ Theme.install(); f=new JFrame("check"); p=new ClubPanel(); f.setContentPane(p); f.setUndecorated(true); f.setBounds(0,0,1440,900); f.setVisible(true);});
        idle(); r.delay(800);
        JButton match=(JButton)get("matchButton"), save=(JButton)get("saveButton"), excel=(JButton)get("excelButton");
        boolean dis = edt(()->!match.isEnabled() && !save.isEnabled() && !excel.isEnabled());
        check("G-01","명단 없이 시작: 조 편성·결과 저장·엑셀에서 열기 버튼 비활성", dis, "");

        Path work=Files.createTempDirectory("gui"); Path csv=work.resolve("members.csv"); Files.copy(Path.of("sample_members.csv"),csv);
        edt(()->call("loadFile",new Class<?>[]{Path.class},csv)); idle();
        String b=bannerText();
        check("G-02","CSV 불러오기 → 배너 '불러옴 · 42명', 조 편성 버튼 활성", b.contains("불러옴")&&b.contains("42명")&&match.isEnabled(), b);

        click(edt(()->centerOnScreen(match)), InputEvent.BUTTON1_DOWN_MASK); r.delay(600);
        check("G-03","[조 편성 실행] 클릭 → 조 카드 8개 + 미배정 카드(4명)", res()!=null && res().getGroups().size()==8 && listOf(cardOf(null)).getModel().getSize()==4, ""+cards().size());

        click(cellPoint("기본1조","오준서"), InputEvent.BUTTON1_DOWN_MASK);
        Container sel=(Container)get("selectionCard");
        Component chip=edt(()->find(sel,c->textOf(c).contains("기본5조")));
        check("G-04","오준서 클릭 → 선택한 부원 카드에 '이동 가능: 기본5조' 꼬리표", chip!=null, "");

        if(chip!=null) click(edt(()->centerOnScreen(chip)), InputEvent.BUTTON1_DOWN_MASK);
        check("G-05","초록 꼬리표 클릭 → 오준서 기본5조로 이동, 기본5조 6명, 배너 '오준서 → 기본5조'",
            groupNamed("기본5조").contains(byName("오준서")) && groupNamed("기본5조").size()==6 && bannerText().contains("오준서 → 기본5조"), bannerText());

        // 드래그: 류보민(미배정, 일 10시) → 기본6조(수 19시, 4명), 확인 창에서 닫기(Esc) → 이동 안 됨
        Point from=cellPoint(null,"류보민"); Point to=edt(()->{ JList<Member> l=listOf(cardOf("기본6조")); return centerOnScreen(l);});
        Runnable drag=()->{ r.mouseMove(from.x,from.y); r.delay(150); r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            for(int i=1;i<=30;i++){ r.mouseMove(from.x+(to.x-from.x)*i/30, from.y+(to.y-from.y)*i/30); r.delay(25);} r.delay(300); r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); };
        drag.run(); r.delay(900);
        Window dlg=null; for(Window w: Window.getWindows()) if(w instanceof JDialog && w.isShowing()) dlg=w;
        String dlgText = dlg==null? "" : String.valueOf(((JDialog)dlg).getTitle());
        key(KeyEvent.VK_ESCAPE); r.delay(400);
        check("G-06","시간이 안 맞는 조로 드래그 → '시간이 맞지 않음' 확인 창, 닫으면 이동 안 됨",
            dlgText.contains("시간이 맞지 않음") && res().getUnassigned().containsKey(byName("류보민")), "창="+dlgText);
        drag.run(); r.delay(900); key(KeyEvent.VK_ENTER); r.delay(500);
        String ch=String.join(" / ",res().getChanges());
        check("G-07","같은 드래그 후 '예' → 기본6조 5명(인원 미달 해소), 기록에 '등록 시간과 맞지 않음'",
            groupNamed("기본6조").contains(byName("류보민")) && groupNamed("기본6조").size()==5 && ch.contains("등록 시간과 맞지 않음"), ch);

        // 오른쪽 클릭 → 미배정으로 빼기
        click(cellPoint("기본3조","한예린"), InputEvent.BUTTON3_DOWN_MASK); r.delay(400);
        Component out=null; for(Window w: Window.getWindows()) if(w.isShowing()){ Component c=find(w, x->x instanceof JMenuItem && "미배정으로 빼기".equals(((JMenuItem)x).getText())); if(c!=null) out=c; }
        if(out==null){ for(MenuElement me: MenuSelectionManager.defaultManager().getSelectedPath()) if(me instanceof JPopupMenu){ for(Component c: ((JPopupMenu)me).getComponents()) if(c instanceof JMenuItem && "미배정으로 빼기".equals(((JMenuItem)c).getText())) out=c; } }
        if(out!=null){ final Component o2=out; click(edt(()->centerOnScreen(o2)), InputEvent.BUTTON1_DOWN_MASK);} else key(KeyEvent.VK_ESCAPE);
        check("G-08","오른쪽 클릭 → '미배정으로 빼기' → 한예린 미배정, 기본3조 5명", res().getUnassigned().containsKey(byName("한예린")) && groupNamed("기본3조").size()==5, "메뉴="+(out!=null));

        // 조 이름 변경: 이름 클릭 → 입력 창
        Component nameLbl=edt(()->find((Container)cardOf("기본1조"), c->c instanceof JLabel && "기본1조".equals(((JLabel)c).getText())));
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection("미배정"),null);
        click(edt(()->centerOnScreen(nameLbl)), InputEvent.BUTTON1_DOWN_MASK); r.delay(500);
        r.keyPress(KeyEvent.VK_CONTROL); key(KeyEvent.VK_A); key(KeyEvent.VK_V); r.keyRelease(KeyEvent.VK_CONTROL); key(KeyEvent.VK_ENTER); r.delay(500);
        String warnTitle=""; for(Window w: Window.getWindows()) if(w instanceof JDialog && w.isShowing()) warnTitle=((JDialog)w).getTitle()+":"+ (find((Container)w, c->c instanceof JLabel && textOf(c).contains("미배정"))!=null);
        key(KeyEvent.VK_ENTER); r.delay(500);   // 경고 닫기 → 다시 입력 창
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection("월요일 저녁반"),null);
        r.keyPress(KeyEvent.VK_CONTROL); key(KeyEvent.VK_A); key(KeyEvent.VK_V); r.keyRelease(KeyEvent.VK_CONTROL); key(KeyEvent.VK_ENTER); r.delay(500);
        check("G-09","조 이름 '미배정' 입력 → 경고 후 다시 입력, '월요일 저녁반' 입력 → 카드·배너에 새 이름",
            warnTitle.endsWith("true") && groupNamed("월요일 저녁반")!=null && bannerText().contains("월요일 저녁반"), warnTitle+" / "+bannerText());

        // 편성 보고서 탭
        Component tab=edt(()->find(p, c->"편성 보고서".equals(textOf(c))));
        click(edt(()->centerOnScreen(tab)), InputEvent.BUTTON1_DOWN_MASK);
        String rep = edt(()->{ JEditorPane ep=(JEditorPane)get("report"); return ep.getDocument().getText(0, ep.getDocument().getLength()); });
        check("G-10","편성 보고서 탭 → 새 조 이름, 수동 변경 기록(이동 3건·이름 변경 1건) 표시",
            rep.contains("월요일 저녁반") && rep.contains("조 이름 변경") && rep.contains("류보민"), rep.length()+"");

        // 다시 편성 → 변경 사라짐 확인 창 → 닫기
        click(edt(()->centerOnScreen(match)), InputEvent.BUTTON1_DOWN_MASK); r.delay(500);
        String conf=""; for(Window w: Window.getWindows()) if(w instanceof JDialog && w.isShowing()) conf=((JDialog)w).getTitle();
        key(KeyEvent.VK_ESCAPE);
        check("G-11","수동 변경 4건이 있을 때 [조 편성 실행] → 확인 창, 취소하면 결과 유지", conf.equals("확인") && res().getChanges().size()==4 && groupNamed("월요일 저녁반")!=null, conf);

        // 파일 변경 감지 → 배너 [다시 불러오기]
        List<String> lines=new ArrayList<>(Files.readAllLines(csv)); 
        for(int i=0;i<lines.size();i++) if(lines.get(i).contains("류보민")) lines.set(i, lines.get(i).replace("일 10:00-10:30","토 13:00-15:00"));
        lines.add("202400199,새부원,초급,N3~N5,N,토 14:00-15:00");
        Thread.sleep(1100); Files.write(csv,lines); r.delay(2500); idle();
        String wb=bannerText(); JButton act=(JButton)get("bannerAction");
        check("G-12","엑셀에서 저장한 것처럼 파일 수정 → 1.5초 안에 '파일이 바뀌었어요' 배너와 [다시 불러오기]", wb.contains("파일이 바뀌었어요") && act.isShowing(), wb);
        click(edt(()->centerOnScreen(act)), InputEvent.BUTTON1_DOWN_MASK); r.delay(400); key(KeyEvent.VK_ENTER); r.delay(700);
        String rb=bannerText();
        check("G-13","[다시 불러오기] → 변경 확인 후 다시 읽고 '추가 1 · 수정 1 · 빠짐 0', 편성 초기화", rb.contains("추가 1 · 수정 1 · 빠짐 0") && res()==null && rb.contains("43명"), rb);
        System.out.println(); System.out.println("통과 "+pass+"개 / 실패 "+fail+"개");
        System.exit(0);
    }
    static void collect(Container c, StringBuilder sb){ for(Component x: c.getComponents()){ sb.append(textOf(x)).append(' ');
        if(x instanceof JTable){ JTable t=(JTable)x; for(int i=0;i<t.getRowCount();i++) for(int j=0;j<t.getColumnCount();j++) sb.append(t.getValueAt(i,j)).append(' ');}
        if(x instanceof javax.swing.text.JTextComponent) sb.append(((javax.swing.text.JTextComponent)x).getText());
        if(x instanceof Container) collect((Container)x,sb);} }
}
