package kr.pyke.client.gui.hud;

import kr.pyke.client.manager.chzzk.ChzzkManager;
import kr.pyke.client.manager.soop.SoopManager;
import kr.pyke.client.state.ConnectionStatus;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public class DebugHud implements HudElement {
    private static boolean visible = false;

    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int BOX_GAP = 2;

    public static void toggle() { visible = !visible; }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, @NonNull DeltaTracker deltaTracker) {
        if (!visible) { return; }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) { return; }

        List<StatusLine> lines = buildLines();
        if (lines.isEmpty()) { return; }

        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int boxHeight = LINE_HEIGHT + PADDING * 2;
        int totalHeight = lines.size() * boxHeight + (lines.size() - 1) * BOX_GAP;
        int cursorY = screenHeight - totalHeight - PADDING;

        for (StatusLine line : lines) {
            int textWidth = font.width(line.text);
            int boxWidth = textWidth + PADDING * 2;
            int boxX = screenWidth - boxWidth - PADDING;

            graphics.fill(boxX, cursorY, boxX + boxWidth, cursorY + boxHeight, 0x80000000);
            graphics.text(font, line.text, boxX + PADDING, cursorY + PADDING, line.color, false);

            cursorY += boxHeight + BOX_GAP;
        }
    }

    private List<StatusLine> buildLines() {
        List<StatusLine> lines = new ArrayList<>();

        ConnectionStatus soopStatus = SoopManager.getInstance().getLocalStatus();
        ConnectionStatus chzzkStatus = ChzzkManager.getInstance().getLocalStatus();

        if (!soopStatus.tokenExists() && !chzzkStatus.tokenExists()) { return lines; }

        if (soopStatus.tokenExists()) {
            lines.add(toStatusLine("숲", soopStatus));
        }

        if (chzzkStatus.tokenExists()) {
            lines.add(toStatusLine("치지직", chzzkStatus));
        }

        return lines;
    }

    private StatusLine toStatusLine(String platformName, ConnectionStatus status) {
        return switch (status.state()) {
            case CONNECTED -> new StatusLine("● " + platformName + " 연동 중", 0xFF55FF55);
            case DISCONNECTED -> new StatusLine("○ " + platformName + " 연동 안 됨", 0xFF888888);
            case TOKEN_EXPIRED -> new StatusLine("✕ " + platformName + " 토큰 만료", 0xFFFF5555);
            case ERROR -> new StatusLine("△ " + platformName + " 연결 오류", 0xFFFFAA00);
        };
    }

    private record StatusLine(String text, int color) { }
}