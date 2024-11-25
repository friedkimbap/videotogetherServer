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
import java.util.Objects;
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
                userList.clear();
                videoList.clear();
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
            int i = 0; // 현재 접속해 있는 유저의 index
            int v = 0; // 현재 보고 있는 상영회의 index
            try {
                ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(clientSocket.getInputStream()));
                out = new ObjectOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));
                while ((user = (UserObj) in.readObject()) != null) {
                    switch (user.mode) {
                        case UserObj.MODE_LogIn -> {
                            if(!userList.isEmpty()){
                                for( i = 0; i< userList.size(); i++){ // 이거는 닉네임 중복 없게 하는 것
                                    if(user.name.equals(userList.get(i).name)) {
                                        printDisplay(">> 이미 존재하는 닉네임입니다.");
                                        break;
                                    }
                                }
                            }
                            userList.add(user);
                            printDisplay(">> 현재 핸들러 수 :"+String.valueOf(ClientHandlerList.size()));
                            if(videoList.isEmpty()){

                            }else{
                                send(videoList); // 상영회 목록 화면을 나타내기 위함
                            }
                        }
                        case UserObj.MODE_LogOut -> { // while문을 탈출하면 바로 로그아웃
                            break;
                        }
                        case UserObj.MODE_JoinVideo -> { // 유저가 상영회에 접속할 때 해야할 것
                            for(v = 0;v<videoList.size();v++) {
                                if(user.chat.equals(videoList.get(v).id)) {
                                    send(videoList.get(v)); // 그 유저에게 videoObject를 전송해 비디오 정보를 알 수 있게 함
                                }
                            }
                        }
                        case UserObj.MODE_StartVideo -> { // 유저가 상영회를 열었을 때
                            videoList.add(user.video);
                            v=videoList.size()-1;
                            printDisplay(">> 상영회를 추가하였습니다. 현재 상영회 수: "+videoList.size());
                        }
                        case UserObj.MODE_EndVideo -> { // 유저가 상영회를 종료했을 때
                            videoList.remove(user.video);
                            send(videoList); // 다시 상영회 목록을 보여줘야 함
                        }
                        case UserObj.MODE_ChatStr -> {
                            String chat = user.name+" : "+user.chat;
                            user.chat = chat;
                            printDisplay("채팅 >> " + chat);
                            broadcasting(user); // userObj의 chat 변수에 참조하여 이름 및 채팅을 참조하기 위함
                        }
                        case UserObj.MODE_WatchingVideo -> {
                            printDisplay(user.video.o_name+"님의 상영회 상태 변경: "+ user.video.videoMode);
                            videoList.set(v, user.video);
                            printDisplay("바뀔 재생 시간 >>"+user.video.videoTime);
                            broadcasting(user);
                        }
                    }
                }

                ClientHandlerList.removeElement(this);
                printDisplay(">> "+ user.name + " 퇴장. 현재 참가자 수: " + ClientHandlerList.size());
                userList.remove(i);
            } catch (IOException e) {
                e.printStackTrace();
                ClientHandlerList.removeElement(this);
                printDisplay(user.video.o_name+"님의 상영회가 종료되었습니다.");
                printDisplay(">> "+ user.name + " 연결 끊김. 현재 참가자 수: " + ClientHandlerList.size());
                if(videoList.get(v).o_name.equals(user.video.o_name)) {
                    user.mode = UserObj.MODE_EndVideo;
                    broadcasting(user);
                    videoList.remove(v);
                }
                userList.remove(i);
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
                out.writeObject(new UserObj(user));
                out.flush();
            } catch (IOException e) {
                System.err.println(">> 클라이언트 일반 전송 오류: "+e.getMessage());
            }
        }

        public void send(VideoObj video) {
            try {
                out.writeObject(new VideoObj(video));
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

        public void broadcasting(VideoObj video) {
            for(ClientHandler c : ClientHandlerList) {
                c.send(video);
            }
        }

        public void run() {
            receiveMessage(clientSocket);
        }

    }

    public static void main(String[] args) {
        new VTServer(54320);
    }
}