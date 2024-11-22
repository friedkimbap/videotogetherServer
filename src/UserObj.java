import java.io.Serial;
import java.io.Serializable;

public class UserObj implements Serializable {
  @Serial
  private static final long serialVersionUID = 2L;

  public final static int MODE_LogIn = 1;
  public final static int MODE_LogOut = 2;
  public final static int MODE_JoinVideo = 3; // 상영회 참가
  public final static int MODE_WatchingVideo = 4;
  public final static int MODE_StartVideo = 5; // 상영회 개설
  public final static int MODE_EndVideo = 6; // 상영회 종료
  public final static int MODE_ChatStr = 7;

  String name;
  String chat;
  int mode;
  VideoObj video;

  UserObj(String name, int mode) { // 클라이언트에서 로그인이나 로그아웃
    this.name = name;
    this.mode = mode;
  }


  UserObj(String name, int mode, String chatOrId) { // 채팅하는 경우
    this.name = name;
    this.mode = mode;
    this.chat = chatOrId;
  }


  UserObj(String name, int mode, VideoObj video) { // 서버에서 영상을 보내주는 경우 혹은 클라이언트가 상영회를 여는 경우
    this.name = name;
    this.mode = mode;
    this.video = video;
  }
}
