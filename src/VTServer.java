import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Vector;

public class VTServer extends JFrame {
    private int port;
    private ServerSocket serverSocket = null;
    private Thread acceptThread = null;
    private Vector<ClientHandler> ClientHandlerList = new Vector<>();
    private Vector<VideoObj> videoList = new Vector<>();
    private Vector<UserObj> userList = new Vector<>();

    private DefaultStyledDocument document;
    private JTextPane t_display;
    private JButton b_connect, b_disconnect, b_exit;

    public VTServer(int port) {
        super("VideoTogether Server");

        buildGUI();

        setBounds(500,100,400,300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setVisible(true);

        this.port = port;
    }

    public void buildGUI() {
        add(createDisplayPanel(), BorderLayout.CENTER);
        add(createControlPanel(), BorderLayout.SOUTH);

    }

    public JPanel createDisplayPanel() {
        JPanel p = new JPanel(new BorderLayout());
        document = new DefaultStyledDocument();
        t_display= new JTextPane(document);

        JScrollPane scrollPane = new JScrollPane(t_display);

        t_display.setEditable(false);

        p.add(scrollPane, BorderLayout.CENTER);

        return p;
    }

    public JPanel createControlPanel() {
        JPanel p = new JPanel(new GridLayout());
        b_connect = new JButton("서버 시작");
        b_disconnect = new JButton("서버 종료");
        b_exit = new JButton("종료");

        p.add(b_connect);
        p.add(b_disconnect);
        p.add(b_exit);

        b_disconnect.setEnabled(false);

        b_connect.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                b_connect.setEnabled(false);
                b_disconnect.setEnabled(true);
                acceptThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        startServer();
                    }
                });
                acceptThread.start();
            }
        });

        b_disconnect.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                b_connect.setEnabled(true);
                b_disconnect.setEnabled(false);
                disconnect();
            }
        });

        b_exit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                disconnect();
                System.exit(0);
            }
        });

        return p;
    }

    public String getLocalAddr() {
        String local_address = null;

        try {
            InetAddress local = InetAddress.getLocalHost();
            local_address = local.getHostAddress();
        } catch (UnknownHostException e) {
            System.out.println(e.getMessage());
            b_connect.setEnabled(true);
            b_disconnect.setEnabled(false);
        }

        return local_address;
    }

    public void startServer() {
        Socket clientSocket = null;

        try {
            serverSocket = new ServerSocket(port);
            printDisplay(">> 서버가 시작되었습니다: "+getLocalAddr());

            while(acceptThread == Thread.currentThread()) {
                clientSocket = serverSocket.accept();

                String cAddr = clientSocket.getInetAddress().getHostAddress();
                printDisplay(">> 클라이언트가 연결되었습니다: "+cAddr);

                ClientHandler cHandler = new ClientHandler(clientSocket);
                ClientHandlerList.add(cHandler);
                cHandler.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            ClientHandlerList.clear();
        } catch (IOException e) {
            printDisplay(e.getMessage());
        }finally {
            acceptThread = null;
        }
    }

    public void printDisplay(String str){
        int len = t_display.getDocument().getLength();

        t_display.setCaretPosition(len);

        try {
            document.insertString(len,str+"\n",null);
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
    }

    class ClientHandler extends Thread{
        private Socket clientSocket;
        private ObjectOutputStream out;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        void receiveMessage(Socket cs) {
            UserObj user = null;
            try {
                ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(clientSocket.getInputStream()));
                out = new ObjectOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));
                int i = 0;
                while ((user = (UserObj) in.readObject()) != null) {
                    switch (user.mode) {
                        case UserObj.MODE_LogIn -> {
                        for( i = 0; i< userList.size(); i++){ // 이거는 닉네임 중복 없게 하는 것
                            if(user.name.equals(userList.get(i).name)) {
                                printDisplay(">> 이미 존재하는 닉네임입니다.");
                                break;
                            }
                        }
                            ClientHandlerList.add(this);
                            userList.add(user);
                            send(videoList); // 상영회 목록 화면을 나타내기 위함
                        }
                        case UserObj.MODE_LogOut -> {
                            break;
                        }
                        case UserObj.MODE_VT -> { // 유저가 상영회에 접속할 때 해야할 것
                            for(int v=0;v<videoList.size();v++) {
                                if(user.chat.equals(videoList.get(v).id)) send(videoList.get(v));
                            }
                        }
                        case UserObj.MODE_SV -> {
                            videoList.add(user.video);
                        }
                        case UserObj.MODE_Exit -> {
                            videoList.remove(user.video);
                            user.mode = UserObj.MODE_LogIn;
                            send(user);
                        }
                        case UserObj.MODE_ChatStr -> {
                            broadcasting(user);
                        }
                    }
                }

                ClientHandlerList.removeElement(this);
                printDisplay(">> "+ user.name + " 퇴장. 현재 참가자 수: " + ClientHandlerList.size());
                userList.removeElement(user);
            } catch (IOException e) {
                e.printStackTrace();
                ClientHandlerList.removeElement(this);
                printDisplay(">> "+ user.name + " 연결 끊김. 현재 참가자 수: " + ClientHandlerList.size());
                userList.removeElement(user);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    cs.close();
                } catch (IOException e) {
                    System.err.println(">> 서버 닫기 오류: "+e.getMessage());
                    System.exit(-1);
                }
            }
        }

        private void send(Vector<VideoObj> videoList) {
            try {
                out.writeObject(videoList);
                out.flush();
            } catch (IOException e) {
                System.err.println(">> 클라이언트 일반 전송 오류: "+e.getMessage());
            }
        }

        public void send(UserObj user) {
            try {
                out.writeObject(user);
                out.flush();
            } catch (IOException e) {
                System.err.println(">> 클라이언트 일반 전송 오류: "+e.getMessage());
            }
        }

        public void send(VideoObj video) {
            try {
                out.writeObject(video);
                out.flush();
            } catch (IOException e) {
                System.err.println(">> 클라이언트 일반 전송 오류: "+e.getMessage());
            }
        }

        public void broadcasting(UserObj user) {
            for(ClientHandler c : ClientHandlerList) {
                c.send(user);
            }
        }

        public void run() {
            receiveMessage(clientSocket);
        }

    }

    public static void main(String[] args) {
        new VTServer(54321);
    }
}