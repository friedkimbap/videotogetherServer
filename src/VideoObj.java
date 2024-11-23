import java.io.Serial;
import java.io.Serializable;

public class VideoObj implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public final static int MODE_Create = 1;
    public final static int MODE_Play = 2;
    public final static int MODE_Pause = 3;
    public final static int Mode_ChangeTime = 5;

    String id; // 영상 ID
    String name;
    String o_name;
    int videoMode;
    int videoTime;
    int user_num = 1;

    VideoObj (VideoObj videoObj){
        this.id = videoObj.id;
        this.name = videoObj.name;
        this.o_name = videoObj.o_name;
        this.videoMode = videoObj.videoMode;
        this.videoTime = videoObj.videoTime;
        this.user_num = videoObj.user_num;
    }

    VideoObj(String name) {
        this.name = name;
        user_num = 0;
    }
}
