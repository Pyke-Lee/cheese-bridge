package kr.pyke.util;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Arrays;
import java.nio.ByteBuffer;

public class SoopProtocol {
    public static final String DELIMITER = "\f";
    public static final byte ESC = 27;
    public static final byte TAB = 9;

    public static final int SVC_KEEPALIVE = 0;
    public static final int SVC_LOGIN = 1;
    public static final int SVC_JOINCH = 2;
    public static final int SVC_CHATMESG = 5;
    public static final int SVC_SDK_LOGIN = 16;
    public static final int SVC_SENDBALLOON = 18;       // 일반 별풍선
    public static final int SVC_SENDBALLOONSUB = 33;    // 중계방 별풍선
    public static final int SVC_VIDEO_BALLOON = 105;    // 영상풍선
    public static final int SVC_MISSION = 121;          // 대결/도전 미션 (JSON, type 으로 구분)
    public static final int SVC_MISSION_SETTLE = 125;   // 도전미션 정산 후원자 목록 (미사용)

    public static byte[] makePacket(int serviceCode, List<String> bodyParts) {
        StringBuilder bodyBuilder = new StringBuilder();
        bodyBuilder.append(DELIMITER);
        for (String part : bodyParts) {
            bodyBuilder.append(part).append(DELIMITER);
        }

        byte[] bodyBytes = bodyBuilder.toString().getBytes(StandardCharsets.UTF_8);
        String serviceCodeStr = String.format("%04d", serviceCode);
        String lengthStr = String.format("%06d", bodyBytes.length);

        ByteBuffer buffer = ByteBuffer.allocate(14 + bodyBytes.length);
        buffer.put(ESC);
        buffer.put(TAB);
        buffer.put(serviceCodeStr.getBytes(StandardCharsets.UTF_8));
        buffer.put(lengthStr.getBytes(StandardCharsets.UTF_8));
        buffer.put("00".getBytes(StandardCharsets.UTF_8));
        buffer.put(bodyBytes);

        return buffer.array();
    }

    public static List<String> parseBody(String message) {
        return Arrays.asList(message.split(DELIMITER, -1));
    }
}