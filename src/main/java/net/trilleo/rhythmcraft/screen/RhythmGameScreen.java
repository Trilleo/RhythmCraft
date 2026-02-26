package net.trilleo.rhythmcraft.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.trilleo.rhythmcraft.game.Note;
import net.trilleo.rhythmcraft.game.NoteType;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The main rhythm game stage screen.
 *
 * <p>Supports 4, 5, or 6 key modes. Notes of type {@link NoteType#TAP} appear as thin
 * horizontal bars; {@link NoteType#HOLD} notes have a head bar, a connecting body, and a
 * tail bar that must all be held through to earn full score.</p>
 */
@Environment(EnvType.CLIENT)
public class RhythmGameScreen extends Screen {

    // ── Layout constants ────────────────────────────────────────────────────

    /** Pixel width of each lane. */
    private static final int LANE_WIDTH = 80;
    /** Distance from the bottom of the screen to the judgment line. */
    private static final int JUDGMENT_OFFSET = 90;
    /** Height in pixels of the key-indicator box below the judgment line. */
    private static final int KEY_BOX_HEIGHT = 34;
    /** Milliseconds a note takes to travel from the top of the play area to the judgment line. */
    private static final int NOTE_TRAVEL_MS = 2000;
    /** Half-window (ms) around the judgment line within which a hit is accepted. */
    private static final int HIT_WINDOW_MS = 150;
    /** Width of the thin "border" line drawn between lanes. */
    private static final int LANE_BORDER = 1;

    // ── Colors ───────────────────────────────────────────────────────────────

    private static final int COLOR_BG              = 0xFF0D1117;
    private static final int COLOR_LANE_EVEN       = 0xFF161B22;
    private static final int COLOR_LANE_ODD        = 0xFF21262D;
    private static final int COLOR_LANE_BORDER     = 0xFF30363D;
    private static final int COLOR_JUDGMENT_LINE   = 0xFFF78166;
    private static final int COLOR_TAP_NOTE        = 0xFFE6EDF3;
    private static final int COLOR_TAP_ACCENT      = 0xFFFFD700;
    private static final int COLOR_HOLD_HEAD       = 0xFF4ECDC4;
    private static final int COLOR_HOLD_BODY       = 0x884ECDC4;
    private static final int COLOR_HOLD_ACTIVE     = 0xFF26C6DA;
    private static final int COLOR_HOLD_BODY_ACTIVE= 0xCC26C6DA;
    private static final int COLOR_KEY_IDLE        = 0xFF2D333B;
    private static final int COLOR_KEY_PRESSED     = 0xFFF78166;
    private static final int COLOR_MISS_FLASH      = 0x44FF0000;

    // ── Key bindings (GLFW codes) per lane count ──────────────────────────────

    /** Key codes for 4-key mode: D F J K */
    private static final int[] KEYS_4 = {
            GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K
    };
    /** Key codes for 5-key mode: D F Space J K */
    private static final int[] KEYS_5 = {
            GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_SPACE,
            GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K
    };
    /** Key codes for 6-key mode: S D F J K L */
    private static final int[] KEYS_6 = {
            GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F,
            GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_L
    };

    private static final String[][] KEY_NAMES = {
            {"D", "F", "J", "K"},
            {"D", "F", "SPC", "J", "K"},
            {"S", "D", "F", "J", "K", "L"}
    };

    // ── Game state ────────────────────────────────────────────────────────────

    private int laneCount;         // 4, 5 or 6
    private int[] laneKeys;        // current active key codes
    private String[] laneKeyNames; // display labels

    private final List<Note> notes = new ArrayList<>();
    private final boolean[] lanePressed  = new boolean[6];
    /** Timestamp of the last hit or miss in each lane, for brief visual feedback. */
    private final long[]    hitEffectTime  = new long[6];
    private final boolean[] hitEffectMiss  = new boolean[6];

    private int  score;
    private int  combo;
    private int  maxCombo;
    private long gameStartTime;
    private boolean gameStarted;
    /** True once every note has been judged. */
    private boolean gameOver;

    // ── Constructors ─────────────────────────────────────────────────────────

    public RhythmGameScreen() {
        this(4);
    }

    public RhythmGameScreen(int laneCount) {
        super(Text.translatable("screen.rhythmcraft.rhythm_game"));
        setLaneCount(laneCount);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setLaneCount(int count) {
        this.laneCount = count;
        switch (count) {
            case 5  -> { laneKeys = KEYS_5; laneKeyNames = KEY_NAMES[1]; }
            case 6  -> { laneKeys = KEYS_6; laneKeyNames = KEY_NAMES[2]; }
            default -> { laneKeys = KEYS_4; laneKeyNames = KEY_NAMES[0]; }
        }
    }

    private void buildSampleNotes() {
        notes.clear();
        // A short demonstration chart that works for any supported lane count.
        // All lane indices are clamped to laneCount-1 automatically in rendering.
        int max = laneCount - 1;
        int mid = laneCount / 2;

        addNote(0,   NoteType.TAP,  1000, 0);
        addNote(max, NoteType.TAP,  1500, 0);
        addNote(mid, NoteType.TAP,  2000, 0);
        addNote(0,   NoteType.TAP,  2500, 0);
        addNote(max, NoteType.TAP,  3000, 0);
        addNote(0,   NoteType.HOLD, 3500, 700);
        addNote(max, NoteType.TAP,  3750, 0);
        addNote(mid, NoteType.HOLD, 4000, 800);
        addNote(max, NoteType.TAP,  4500, 0);
        addNote(0,   NoteType.TAP,  4750, 0);
        addNote(mid, NoteType.TAP,  5000, 0);
        addNote(max, NoteType.TAP,  5250, 0);
        addNote(0,   NoteType.TAP,  5500, 0);
        if (laneCount >= 5) {
            addNote(1,         NoteType.TAP,  5750, 0);
            addNote(laneCount - 2, NoteType.TAP, 5750, 0);
        }
        addNote(0,   NoteType.HOLD, 6000, 1000);
        addNote(max, NoteType.HOLD, 6000, 1000);
        addNote(mid, NoteType.TAP,  6500, 0);
        addNote(mid, NoteType.TAP,  7000, 0);
        addNote(0,   NoteType.TAP,  7250, 0);
        addNote(max, NoteType.TAP,  7250, 0);
        addNote(mid, NoteType.HOLD, 7500, 600);
    }

    private void addNote(int lane, NoteType type, long hitTime, long duration) {
        notes.add(new Note(lane, type, hitTime, duration));
    }

    // ── Screen lifecycle ──────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        buildSampleNotes();
        score = 0;
        combo = 0;
        maxCombo = 0;
        gameOver = false;
        gameStartTime = System.currentTimeMillis();
        gameStarted = true;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long elapsed = gameStarted ? System.currentTimeMillis() - gameStartTime : 0L;

        int playW    = laneCount * LANE_WIDTH;
        int playX    = (this.width - playW) / 2;
        int judgeY   = this.height - JUDGMENT_OFFSET;

        // Background
        context.fill(0, 0, this.width, this.height, COLOR_BG);

        // Lanes
        for (int i = 0; i < laneCount; i++) {
            int lx = playX + i * LANE_WIDTH;
            context.fill(lx, 0, lx + LANE_WIDTH, this.height,
                    (i % 2 == 0) ? COLOR_LANE_EVEN : COLOR_LANE_ODD);
            // right border
            context.fill(lx + LANE_WIDTH - LANE_BORDER, 0,
                    lx + LANE_WIDTH, this.height, COLOR_LANE_BORDER);
        }
        // left border of first lane
        context.fill(playX, 0, playX + LANE_BORDER, this.height, COLOR_LANE_BORDER);

        // Miss-flash overlay per lane
        long now = System.currentTimeMillis();
        for (int i = 0; i < laneCount; i++) {
            if (hitEffectMiss[i] && now - hitEffectTime[i] < 200) {
                int lx = playX + i * LANE_WIDTH;
                context.fill(lx, 0, lx + LANE_WIDTH, judgeY, COLOR_MISS_FLASH);
            }
        }

        // Notes
        for (Note note : notes) {
            if (note.lane >= laneCount) continue;
            renderNote(context, note, elapsed, playX, judgeY);
        }

        // Judgment line
        context.fill(playX, judgeY - 2, playX + playW, judgeY + 2, COLOR_JUDGMENT_LINE);

        // Key-indicator boxes
        renderKeyBoxes(context, playX, judgeY);

        // HUD – score, combo, key-count toggle hint
        renderHud(context, playX, playW, elapsed);

        // Game-over overlay
        if (gameOver) {
            renderGameOver(context);
        }

        // Auto-judge overdue notes
        autoJudge(elapsed);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderNote(DrawContext ctx, Note note, long elapsed,
                            int playX, int judgeY) {
        if (note.isHit() && !note.isHoldActive()) return;

        long timeToHead = note.hitTime - elapsed;
        // Skip notes that are far in the future or long gone
        if (timeToHead > NOTE_TRAVEL_MS + 500) return;

        int noteW = LANE_WIDTH - 6;
        int noteX = playX + note.lane * LANE_WIDTH + 3;
        int headY = judgeY - (int) (timeToHead * judgeY / NOTE_TRAVEL_MS);

        if (note.isTap()) {
            if (timeToHead < -HIT_WINDOW_MS * 3) return;
            // Accent stripe
            ctx.fill(noteX, headY - 10, noteX + noteW, headY - 6, COLOR_TAP_ACCENT);
            // Main bar
            ctx.fill(noteX, headY - 6, noteX + noteW, headY + 4, COLOR_TAP_NOTE);
        } else {
            // HOLD note
            long timeToTail = (note.hitTime + note.duration) - elapsed;
            if (timeToHead < -HIT_WINDOW_MS * 3 && !note.isHoldActive()) return;

            int tailY = judgeY - (int) (timeToTail * judgeY / NOTE_TRAVEL_MS);
            boolean active = note.isHoldActive();
            int bodyColor = active ? COLOR_HOLD_BODY_ACTIVE : COLOR_HOLD_BODY;
            int capColor  = active ? COLOR_HOLD_ACTIVE      : COLOR_HOLD_HEAD;

            // When held, clamp body top to avoid drawing above top of screen
            int bodyTop  = Math.max(0, tailY);
            int bodyBot  = active ? judgeY : headY;  // while held, body extends to judgment
            if (bodyTop < bodyBot) {
                ctx.fill(noteX + noteW / 4, bodyTop, noteX + 3 * noteW / 4, bodyBot, bodyColor);
            }

            // Tail cap (only while tail is still above judgment)
            if (tailY < judgeY) {
                ctx.fill(noteX, tailY - 5, noteX + noteW, tailY + 5, capColor);
            }

            // Head cap (only before head hits judgment, or while not yet pressed)
            if (!active && headY <= judgeY + 20) {
                ctx.fill(noteX, headY - 6, noteX + noteW, headY + 4, capColor);
            }
        }
    }

    private void renderKeyBoxes(DrawContext ctx, int playX, int judgeY) {
        int boxY = judgeY + 6;
        for (int i = 0; i < laneCount; i++) {
            int lx = playX + i * LANE_WIDTH;
            boolean pressed = lanePressed[i];
            int bg = pressed ? COLOR_KEY_PRESSED : COLOR_KEY_IDLE;
            ctx.fill(lx + 4, boxY, lx + LANE_WIDTH - 4, boxY + KEY_BOX_HEIGHT, bg);
            ctx.drawCenteredTextWithShadow(this.textRenderer, laneKeyNames[i],
                    lx + LANE_WIDTH / 2, boxY + (KEY_BOX_HEIGHT - 9) / 2, 0xFFFFFFFF);
        }
    }

    private void renderHud(DrawContext ctx, int playX, int playW, long elapsed) {
        // Title
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.getTitle(),
                this.width / 2, 6, 0xFFE6EDF3);

        // Score
        ctx.drawTextWithShadow(this.textRenderer,
                Text.translatable("screen.rhythmcraft.score", score),
                playX, 20, 0xFFE6EDF3);

        // Combo
        if (combo > 1) {
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.translatable("screen.rhythmcraft.combo", combo),
                    playX, 32, 0xFFFFD700);
        }

        // Mode hint (bottom-right of play area)
        String modeHint = laneCount + "K  [Tab: cycle mode]";
        int hintX = playX + playW - this.textRenderer.getWidth(modeHint);
        ctx.drawTextWithShadow(this.textRenderer, modeHint,
                hintX, this.height - 12, 0xFF6E7681);
    }

    private void renderGameOver(DrawContext ctx) {
        String line1 = "── CLEAR ──";
        Text line2 = Text.translatable("screen.rhythmcraft.final_score", score, maxCombo);
        String line3 = "[Esc] to exit   [Tab] to restart";
        int cx = this.width / 2;
        int cy = this.height / 2 - 16;
        ctx.fill(cx - 130, cy - 8, cx + 130, cy + 44, 0xCC000000);
        ctx.drawCenteredTextWithShadow(this.textRenderer, line1, cx, cy,      0xFFFFD700);
        ctx.drawCenteredTextWithShadow(this.textRenderer, line2, cx, cy + 14, 0xFFE6EDF3);
        ctx.drawCenteredTextWithShadow(this.textRenderer, line3, cx, cy + 28, 0xFF8B949E);
    }

    // ── Game logic ────────────────────────────────────────────────────────────

    /** Called every frame; judges notes that the player is too late to hit or complete. */
    private void autoJudge(long elapsed) {
        boolean allDone = true;
        for (Note note : notes) {
            if (note.lane >= laneCount) continue;

            if (!note.isHit() && !note.isHoldActive()) {
                long timeToHead = note.hitTime - elapsed;
                if (timeToHead < -HIT_WINDOW_MS * 2) {
                    // Miss
                    note.setHit(true);
                    combo = 0;
                    hitEffectTime[note.lane] = System.currentTimeMillis();
                    hitEffectMiss[note.lane] = true;
                }
            }

            // Auto-complete an active hold whose tail has passed the hit window
            if (note.isHold() && note.isHoldActive()) {
                long tailTime = note.hitTime + note.duration;
                if (elapsed > tailTime + HIT_WINDOW_MS) {
                    note.setHoldActive(false);
                    note.setHit(true);
                    addScore(100); // tail auto-completed; head already scored 50
                }
            }

            if (!note.isHit() && !note.isHoldActive()) allDone = false;
        }
        if (!gameOver && allDone && !notes.isEmpty()) {
            gameOver = true;
        }
    }

    private void addScore(int base) {
        score += base;
        combo++;
        if (combo > maxCombo) maxCombo = combo;
    }

    // ── Input handling ────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Cycle key count / restart with Tab
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            int next = laneCount == 6 ? 4 : laneCount + 1; // 4 → 5 → 6 → 4
            setLaneCount(next);
            init();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        for (int i = 0; i < laneCount; i++) {
            if (keyCode == laneKeys[i] && !lanePressed[i]) {
                lanePressed[i] = true;
                tryPressLane(i);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        for (int i = 0; i < laneCount; i++) {
            if (keyCode == laneKeys[i]) {
                lanePressed[i] = false;
                tryReleaseLane(i);
                return true;
            }
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    /** Called when a lane key is first pressed. Scores TAP notes or starts HOLD notes. */
    private void tryPressLane(int lane) {
        if (!gameStarted || gameOver) return;
        long elapsed = System.currentTimeMillis() - gameStartTime;

        Note best = null;
        long bestDiff = Long.MAX_VALUE;
        for (Note note : notes) {
            if (note.isHit() || note.isHoldActive() || note.lane != lane) continue;
            long diff = Math.abs(note.hitTime - elapsed);
            if (diff <= HIT_WINDOW_MS && diff < bestDiff) {
                bestDiff = diff;
                best = note;
            }
        }
        if (best == null) return;

        if (best.isTap()) {
            best.setHit(true);
            int pts = 100 + (int) (50 * (1.0 - (double) bestDiff / HIT_WINDOW_MS));
            addScore(pts);
            hitEffectTime[lane] = System.currentTimeMillis();
            hitEffectMiss[lane] = false;
        } else {
            // HOLD: score the head press (partial); tail press adds the remainder
            best.setHoldActive(true);
            addScore(50);
            hitEffectTime[lane] = System.currentTimeMillis();
            hitEffectMiss[lane] = false;
        }
    }

    /** Called when a lane key is released. Finalises active HOLD notes for that lane. */
    private void tryReleaseLane(int lane) {
        if (!gameStarted) return;
        long elapsed = System.currentTimeMillis() - gameStartTime;

        for (Note note : notes) {
            if (!note.isHold() || !note.isHoldActive() || note.lane != lane) continue;
            note.setHoldActive(false);
            note.setHit(true);
            long tailTime = note.hitTime + note.duration;
            long earlyMs  = Math.max(0, tailTime - elapsed);
            if (earlyMs <= HIT_WINDOW_MS) {
                // Released on time or late — full tail score
                addScore(100);
            } else {
                // Released early — partial score, break combo
                score += 40;
                combo = 0;
            }
            break; // only one hold active per lane
        }
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
