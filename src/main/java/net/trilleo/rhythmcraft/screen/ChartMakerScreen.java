package net.trilleo.rhythmcraft.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.trilleo.rhythmcraft.game.Chart;
import net.trilleo.rhythmcraft.game.ChartSerializer;
import net.trilleo.rhythmcraft.game.NoteType;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Chart-maker screen for creating and editing rhythm-game charts.
 *
 * <p>Layout:
 * <ul>
 *   <li>Top area – metadata form (title, BPM, key count, music path, save filename)</li>
 *   <li>Middle area – horizontal timeline editor (lane rows, BPM grid, scrollable)</li>
 *   <li>Bottom bar – note-type selector + action buttons</li>
 * </ul>
 *
 * <p>Controls:
 * <ul>
 *   <li>Left-click on timeline – place a note of the selected type.
 *       For HOLD, keep the mouse button held and drag right to set duration.</li>
 *   <li>Right-click on a note – remove it.</li>
 *   <li>Scroll wheel (or ← / →) – scroll through time.</li>
 *   <li>Tab – switch between TAP and HOLD placement mode.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class ChartMakerScreen extends Screen {

    // ── Layout constants ──────────────────────────────────────────────────────

    /** Width of the lane-label column on the left of the timeline. */
    private static final int TL_LABEL_W  = 52;
    /** Height of the metadata form at the top. */
    private static final int TOP_AREA_H  = 76;
    /** Height of the button bar at the bottom. */
    private static final int BOTTOM_BAR_H = 28;
    /** Pixel height of each lane row in the timeline. */
    private static final int LANE_H      = 28;
    /** Visible time span in the timeline (ms). */
    private static final long VIEW_MS    = 8_000L;

    // ── Colors ────────────────────────────────────────────────────────────────

    private static final int C_BG         = 0xFF0D1117;
    private static final int C_LANE_EVEN  = 0xFF161B22;
    private static final int C_LANE_ODD   = 0xFF21262D;
    private static final int C_LABEL_BG   = 0xFF1C2128;
    private static final int C_BORDER     = 0xFF30363D;
    private static final int C_BEAT       = 0xFF3D4451;
    private static final int C_SUBDIV     = 0x552D333B;
    private static final int C_TAP        = 0xFFFFD700;
    private static final int C_HOLD_BODY  = 0xAA4ECDC4;
    private static final int C_HOLD_CAP   = 0xFF4ECDC4;
    private static final int C_DRAG_BODY  = 0x664ECDC4;
    private static final int C_DRAG_CAP   = 0xFF4ECDC4;
    private static final int C_CURSOR     = 0x88FFFFFF;
    private static final int C_TEXT       = 0xFFE6EDF3;
    private static final int C_MUTED      = 0xFF6E7681;

    // ── Chart state ───────────────────────────────────────────────────────────

    private Chart chart = new Chart();
    /** Currently selected note type for placement. */
    private NoteType selectedType = NoteType.TAP;
    /** Timeline scroll position in ms. */
    private long scrollMs = 0;

    // Pending hold-note drag (left button held on timeline)
    private boolean holdDragging  = false;
    private int     holdDragLane  = -1;
    private long    holdDragStart = -1;

    // Status feedback
    private String statusMsg  = "";
    private long   statusTime = 0;

    // ── Widgets ───────────────────────────────────────────────────────────────

    private TextFieldWidget titleField;
    private TextFieldWidget musicPathField;
    private TextFieldWidget bpmField;
    private TextFieldWidget offsetField;
    private TextFieldWidget fileNameField;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ChartMakerScreen() {
        super(Text.translatable("screen.rhythmcraft.chart_maker"));
    }

    // ── Screen lifecycle ──────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();

        final int fh = 16; // field height
        final int fw = 110;

        // Row 1  (y = 8): Title + key-count buttons + BPM + Offset
        titleField = addDrawableChild(new TextFieldWidget(
                textRenderer, 46, 8, fw, fh, Text.empty()));
        titleField.setMaxLength(64);
        titleField.setText(chart.title);

        int kx = 46 + fw + 4;
        addDrawableChild(ButtonWidget.builder(Text.literal("4K"), b -> setKeyCount(4))
                .dimensions(kx, 8, 20, fh).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("5K"), b -> setKeyCount(5))
                .dimensions(kx + 22, 8, 20, fh).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("6K"), b -> setKeyCount(6))
                .dimensions(kx + 44, 8, 20, fh).build());

        int bpmX = kx + 70;
        bpmField = addDrawableChild(new TextFieldWidget(
                textRenderer, bpmX + 30, 8, 44, fh, Text.empty()));
        bpmField.setMaxLength(5);
        bpmField.setText(String.valueOf(chart.bpm));

        offsetField = addDrawableChild(new TextFieldWidget(
                textRenderer, bpmX + 110, 8, 48, fh, Text.empty()));
        offsetField.setMaxLength(8);
        offsetField.setText(String.valueOf(chart.offsetMs));

        // Row 2 (y = 28): Music path
        musicPathField = addDrawableChild(new TextFieldWidget(
                textRenderer, 46, 28, this.width - 50, fh, Text.empty()));
        musicPathField.setMaxLength(256);
        musicPathField.setText(chart.musicPath);

        // Row 3 (y = 48): Save filename + file-list hint
        fileNameField = addDrawableChild(new TextFieldWidget(
                textRenderer, 46, 48, 160, fh, Text.empty()));
        fileNameField.setMaxLength(64);
        fileNameField.setText(sanitizeFilename(chart.title));

        // Bottom bar buttons
        int by = this.height - BOTTOM_BAR_H + 5;
        addDrawableChild(ButtonWidget.builder(Text.literal("TAP"),  b -> { selectedType = NoteType.TAP; })
                .dimensions(4, by, 28, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("HOLD"), b -> { selectedType = NoteType.HOLD; })
                .dimensions(34, by, 36, 18).build());

        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.rhythmcraft.maker.save"),  b -> saveChart())
                .dimensions(this.width - 212, by, 46, 18).build());
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.rhythmcraft.maker.load"),  b -> loadChart())
                .dimensions(this.width - 164, by, 46, 18).build());
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.rhythmcraft.maker.test"),  b -> testPlay())
                .dimensions(this.width - 116, by, 46, 18).build());
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.rhythmcraft.maker.new"),   b -> newChart())
                .dimensions(this.width - 68,  by, 46, 18).build());
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.rhythmcraft.maker.back"),  b -> close())
                .dimensions(this.width - 20,  by, 18, 18).build());
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, C_BG);

        // Metadata labels
        ctx.drawTextWithShadow(textRenderer, "Title:",  4,  12, C_TEXT);
        ctx.drawTextWithShadow(textRenderer, "BPM:",    46 + 110 + 4 + 68, 12, C_TEXT);
        ctx.drawTextWithShadow(textRenderer, "Offset:", 46 + 110 + 4 + 68 + 80, 12, C_TEXT);
        ctx.drawTextWithShadow(textRenderer, "Music:",  4,  32, C_TEXT);
        ctx.drawTextWithShadow(textRenderer, "File:",   4,  52, C_TEXT);

        // Current key count indicator
        String kcLabel = chart.keyCount + "K";
        ctx.drawTextWithShadow(textRenderer, kcLabel,
                46 + 110 + 4 + 22, 12, 0xFFFFD700);

        // Timeline
        renderTimeline(ctx, mouseX, mouseY);

        // Mode indicator
        String modeLabel = "Mode: " + (selectedType == NoteType.TAP ? "TAP" : "HOLD")
                + "  [Tab to toggle]";
        int modeColor = selectedType == NoteType.TAP ? C_TEXT : 0xFF4ECDC4;
        ctx.drawTextWithShadow(textRenderer, modeLabel,
                4, this.height - BOTTOM_BAR_H - 14, modeColor);

        // Status message (fades after 3 s)
        if (System.currentTimeMillis() - statusTime < 3_000) {
            ctx.drawCenteredTextWithShadow(textRenderer, statusMsg,
                    this.width / 2, this.height - BOTTOM_BAR_H - 14, 0xFFFFD700);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderTimeline(DrawContext ctx, int mouseX, int mouseY) {
        int tlX  = TL_LABEL_W;
        int tlY  = TOP_AREA_H;
        int tlW  = this.width - TL_LABEL_W - 4;
        int tlH  = LANE_H * chart.keyCount;
        int tlX2 = tlX + tlW;
        int tlY2 = tlY + tlH;

        if (tlW <= 0 || tlH <= 0) return;

        // Timeline background
        ctx.fill(0, tlY, this.width, tlY2, C_BG);

        // Lane rows + lane labels
        String[] labels = getLaneLabels();
        for (int i = 0; i < chart.keyCount; i++) {
            int ly  = tlY + i * LANE_H;
            int ly2 = ly + LANE_H;
            ctx.fill(0,    ly, TL_LABEL_W - 2, ly2, C_LABEL_BG);
            ctx.fill(TL_LABEL_W - 2, ly, TL_LABEL_W, ly2, C_BORDER);
            ctx.fill(tlX,  ly, tlX2, ly2, (i % 2 == 0) ? C_LANE_EVEN : C_LANE_ODD);
            ctx.drawCenteredTextWithShadow(textRenderer, labels[i],
                    TL_LABEL_W / 2, ly + (LANE_H - 8) / 2, C_TEXT);
            // Row separator
            ctx.fill(0, ly2 - 1, this.width, ly2, C_BORDER);
        }

        // BPM grid lines
        int bpm       = parseBpm();
        long beatMs   = 60_000L / bpm;
        long subdivMs = beatMs / 4; // 16th-note grid
        double pxPerMs = (double) tlW / VIEW_MS;

        long firstSub = (scrollMs / subdivMs) * subdivMs;
        for (long t = firstSub; t <= scrollMs + VIEW_MS + subdivMs; t += subdivMs) {
            if (t < 0) continue;
            int x = tlX + (int) ((t - scrollMs) * pxPerMs);
            if (x < tlX || x > tlX2) continue;
            boolean isBeat = (t % beatMs == 0);
            ctx.fill(x, tlY, x + 1, tlY2, isBeat ? C_BEAT : C_SUBDIV);
            if (isBeat) {
                ctx.drawTextWithShadow(textRenderer,
                        String.valueOf(t / beatMs),
                        x + 2, tlY2 + 2, C_MUTED);
            }
        }

        // Notes
        for (Chart.ChartNote note : chart.notes) {
            if (note.lane < 0 || note.lane >= chart.keyCount) continue;
            long t = note.hitTime;
            if (t + note.duration < scrollMs - 500 || t > scrollMs + VIEW_MS + 500) continue;

            int nx  = tlX + (int) ((t - scrollMs) * pxPerMs);
            int ny  = tlY + note.lane * LANE_H + 2;
            int nh  = LANE_H - 4;

            if ("HOLD".equals(note.type)) {
                int nw = Math.max(4, (int) (note.duration * pxPerMs));
                ctx.fill(nx,          ny, nx + nw,     ny + nh, C_HOLD_BODY);
                ctx.fill(nx,          ny, nx + 3,      ny + nh, C_HOLD_CAP);
                ctx.fill(nx + nw - 3, ny, nx + nw,     ny + nh, C_HOLD_CAP);
            } else {
                ctx.fill(nx - 1, ny, nx + 3, ny + nh, C_TAP);
            }
        }

        // Dragging hold preview
        if (holdDragging && holdDragLane >= 0 && holdDragLane < chart.keyCount) {
            long curTime = timeAtMouseX(mouseX, tlX, pxPerMs);
            long rawDur  = curTime - holdDragStart;
            int nx = tlX + (int) ((holdDragStart - scrollMs) * pxPerMs);
            int ny = tlY + holdDragLane * LANE_H + 2;
            int nh = LANE_H - 4;
            if (rawDur > 0) {
                int nw = Math.max(4, (int) (rawDur * pxPerMs));
                ctx.fill(nx, ny, nx + nw, ny + nh, C_DRAG_BODY);
                ctx.fill(nx, ny, nx + 3,  ny + nh, C_DRAG_CAP);
            }
        }

        // Cursor line + time tooltip
        if (mouseX >= tlX && mouseX <= tlX2 && mouseY >= tlY && mouseY <= tlY2) {
            ctx.fill(mouseX, tlY, mouseX + 1, tlY2, C_CURSOR);
            long hoverTime = snapToGrid(timeAtMouseX(mouseX, tlX, pxPerMs));
            ctx.drawTextWithShadow(textRenderer,
                    String.format("%.3fs", hoverTime / 1000.0),
                    mouseX + 3, tlY + 2, C_MUTED);
        }

        // Scroll range + note count
        ctx.drawTextWithShadow(textRenderer,
                String.format("%.1fs – %.1fs", scrollMs / 1000.0, (scrollMs + VIEW_MS) / 1000.0),
                tlX, tlY2 + 2, C_MUTED);
        ctx.drawTextWithShadow(textRenderer,
                "Notes: " + chart.notes.size(),
                tlX2 - 60, tlY2 + 2, C_MUTED);
    }

    // ── Mouse input ───────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int tlX   = TL_LABEL_W;
        int tlY   = TOP_AREA_H;
        int tlW   = this.width - TL_LABEL_W - 4;
        int tlH   = LANE_H * chart.keyCount;
        double px = (double) tlW / VIEW_MS;

        if (mx >= tlX && mx <= tlX + tlW && my >= tlY && my <= tlY + tlH) {
            int lane = laneAtY((int) my, tlY);
            if (lane < 0 || lane >= chart.keyCount) return super.mouseClicked(mx, my, button);

            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                long time = snapToGrid(timeAtMouseX((int) mx, tlX, px));
                if (time < 0) time = 0;
                if (selectedType == NoteType.HOLD) {
                    holdDragging  = true;
                    holdDragLane  = lane;
                    holdDragStart = time;
                } else {
                    addNoteToChart(lane, NoteType.TAP, time, 0);
                }
                return true;

            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                removeNoteAt((int) mx, (int) my, tlX, tlY, px);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && holdDragging) {
            int  tlX  = TL_LABEL_W;
            int  tlW  = this.width - TL_LABEL_W - 4;
            double px = (double) tlW / VIEW_MS;
            long endTime  = snapToGrid(timeAtMouseX((int) mx, tlX, px));
            long duration = endTime - holdDragStart;
            if (duration < stepMs()) duration = stepMs(); // minimum one grid step
            addNoteToChart(holdDragLane, NoteType.HOLD, holdDragStart, duration);
            holdDragging = false;
            holdDragLane = -1;
            holdDragStart = -1;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        int tlX = TL_LABEL_W;
        int tlY = TOP_AREA_H;
        int tlH = LANE_H * chart.keyCount;
        if (mx >= tlX && my >= tlY && my <= tlY + tlH) {
            scrollMs = Math.max(0, scrollMs - (long) (vertical * stepMs() * 4));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            scrollMs = Math.max(0, scrollMs - stepMs() * 4);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            scrollMs += stepMs() * 4;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            selectedType = (selectedType == NoteType.TAP) ? NoteType.HOLD : NoteType.TAP;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            commitMetadata();
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Coordinate helpers ────────────────────────────────────────────────────

    private long timeAtMouseX(int mx, int tlX, double pxPerMs) {
        return scrollMs + (long) ((mx - tlX) / pxPerMs);
    }

    private int laneAtY(int my, int tlY) {
        return (my - tlY) / LANE_H;
    }

    private int parseBpm() {
        try { return Math.max(1, Integer.parseInt(bpmField.getText().trim())); }
        catch (NumberFormatException e) { return chart.bpm > 0 ? chart.bpm : 120; }
    }

    private long stepMs() {
        return Math.max(1L, 60_000L / parseBpm() / 4); // 16th-note duration
    }

    private long snapToGrid(long timeMs) {
        if (timeMs < 0) timeMs = 0;
        long step = stepMs();
        return Math.round((double) timeMs / step) * step;
    }

    // ── Note operations ───────────────────────────────────────────────────────

    private void addNoteToChart(int lane, NoteType type, long hitTime, long duration) {
        // Replace any existing note at the exact same lane + hitTime
        chart.notes.removeIf(n -> n.lane == lane && n.hitTime == hitTime);
        chart.notes.add(new Chart.ChartNote(lane, type, hitTime, duration));
        chart.notes.sort((a, b) -> Long.compare(a.hitTime, b.hitTime));
    }

    private void removeNoteAt(int mx, int my, int tlX, int tlY, double pxPerMs) {
        long time     = timeAtMouseX(mx, tlX, pxPerMs);
        int  lane     = laneAtY(my, tlY);
        long tolerance = (long) (6 / pxPerMs); // ±6 px in time
        chart.notes.removeIf(n ->
                n.lane == lane && Math.abs(n.hitTime - time) <= tolerance);
    }

    // ── Metadata helpers ──────────────────────────────────────────────────────

    private void setKeyCount(int count) {
        commitMetadata();
        chart.keyCount = count;
    }

    private void commitMetadata() {
        chart.title     = titleField.getText().isBlank() ? "Untitled" : titleField.getText().trim();
        chart.musicPath = musicPathField.getText().trim();
        chart.bpm       = parseBpm();
        try { chart.offsetMs = Long.parseLong(offsetField.getText().trim()); }
        catch (NumberFormatException ignored) {}
    }

    private String sanitizeFilename(String raw) {
        if (raw == null || raw.isBlank()) return "untitled";
        // Strip directory separators and dots to prevent path traversal
        String clean = raw.trim().replaceAll("[^\\w\\- ]", "_");
        return clean.isBlank() ? "untitled" : clean;
    }

    private String[] getLaneLabels() {
        return switch (chart.keyCount) {
            case 5  -> new String[]{"D", "F", "SPC", "J", "K"};
            case 6  -> new String[]{"S", "D", "F", "J", "K", "L"};
            default -> new String[]{"D", "F", "J", "K"};
        };
    }

    // ── Button actions ────────────────────────────────────────────────────────

    private void saveChart() {
        commitMetadata();
        String filename = fileNameField.getText().isBlank()
                ? sanitizeFilename(chart.title) : fileNameField.getText().trim();
        try {
            Path dir  = ChartSerializer.getChartsDir();
            Files.createDirectories(dir);
            Path file = dir.resolve(filename + ".json");
            ChartSerializer.save(chart, file);
            setStatus("Saved → " + file.getFileName());
        } catch (IOException e) {
            setStatus("Save failed: " + e.getMessage());
        }
    }

    private void loadChart() {
        String filename = fileNameField.getText().trim();
        if (filename.isBlank()) {
            // Show available files when no filename given
            try {
                Path dir = ChartSerializer.getChartsDir();
                if (Files.isDirectory(dir)) {
                    List<String> files = Files.list(dir)
                            .filter(p -> p.toString().endsWith(".json"))
                            .map(p -> p.getFileName().toString().replace(".json", ""))
                            .sorted()
                            .collect(Collectors.toList());
                    setStatus(files.isEmpty() ? "No charts found." : "Charts: " + String.join(", ", files));
                } else {
                    setStatus("No charts directory yet.");
                }
            } catch (IOException e) {
                setStatus("Error listing charts: " + e.getMessage());
            }
            return;
        }
        try {
            Path file = ChartSerializer.getChartsDir().resolve(filename + ".json");
            chart = ChartSerializer.load(file);
            titleField.setText(chart.title);
            musicPathField.setText(chart.musicPath);
            bpmField.setText(String.valueOf(chart.bpm));
            offsetField.setText(String.valueOf(chart.offsetMs));
            setStatus("Loaded: " + file.getFileName());
        } catch (IOException e) {
            setStatus("Load failed: " + e.getMessage());
        }
    }

    private void testPlay() {
        commitMetadata();
        if (this.client != null) {
            this.client.setScreen(new RhythmGameScreen(chart, this));
        }
    }

    private void newChart() {
        chart = new Chart();
        chart.keyCount = 4;
        titleField.setText(chart.title);
        musicPathField.setText("");
        bpmField.setText("120");
        offsetField.setText("0");
        fileNameField.setText("untitled");
        scrollMs = 0;
        setStatus("New chart created.");
    }

    private void setStatus(String msg) {
        statusMsg  = msg;
        statusTime = System.currentTimeMillis();
    }

    // ── Screen behaviour ──────────────────────────────────────────────────────

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
