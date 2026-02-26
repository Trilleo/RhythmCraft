package net.trilleo.rhythmcraft.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.trilleo.rhythmcraft.game.Chart;
import net.trilleo.rhythmcraft.game.Judgment;
import net.trilleo.rhythmcraft.game.Note;
import net.trilleo.rhythmcraft.game.NoteType;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The main rhythm game stage screen.
 *
 * <p>Supports 4, 5, or 6 key modes. Judgment follows Chunithm conventions:
 * <ul>
 *   <li>CRITICAL JUSTICE – within ±50 ms of the judgment line → 101% of note-unit value</li>
 *   <li>JUSTICE          – within ±83 ms → 100% of note-unit value; combo continues</li>
 *   <li>ATTACK           – within ±116 ms → 60% of note-unit value; combo broken</li>
 *   <li>MISS             – outside ±116 ms → 0%; combo broken</li>
 * </ul>
 * Maximum score per chart = 1 010 000 (achieved when every note-unit is CRITICAL JUSTICE).
 */
@Environment(EnvType.CLIENT)
public class RhythmGameScreen extends Screen {

    // ── Timing windows (ms) ───────────────────────────────────────────────────

    private static final int WINDOW_CJ  = 50;
    private static final int WINDOW_J   = 83;
    private static final int WINDOW_ATK = 116;

    // ── Layout constants ──────────────────────────────────────────────────────

    private static final int LANE_WIDTH      = 80;
    private static final int JUDGMENT_OFFSET = 90;
    private static final int KEY_BOX_HEIGHT  = 34;
    private static final int NOTE_TRAVEL_MS  = 2000;
    private static final int LANE_BORDER     = 1;

    // ── Colors ────────────────────────────────────────────────────────────────

    private static final int COLOR_BG               = 0xFF0D1117;
    private static final int COLOR_LANE_EVEN        = 0xFF161B22;
    private static final int COLOR_LANE_ODD         = 0xFF21262D;
    private static final int COLOR_LANE_BORDER      = 0xFF30363D;
    private static final int COLOR_JUDGMENT_LINE    = 0xFFF78166;
    private static final int COLOR_TAP_NOTE         = 0xFFE6EDF3;
    private static final int COLOR_TAP_ACCENT       = 0xFFFFD700;
    private static final int COLOR_HOLD_HEAD        = 0xFF4ECDC4;
    private static final int COLOR_HOLD_BODY        = 0x884ECDC4;
    private static final int COLOR_HOLD_ACTIVE      = 0xFF26C6DA;
    private static final int COLOR_HOLD_BODY_ACTIVE = 0xCC26C6DA;
    private static final int COLOR_KEY_IDLE         = 0xFF2D333B;
    private static final int COLOR_KEY_PRESSED      = 0xFFF78166;
    private static final int COLOR_MISS_FLASH       = 0x44FF0000;

    private static final int COLOR_J_CJ   = 0xFF00FFFF;
    private static final int COLOR_J_J    = 0xFFFFCC00;
    private static final int COLOR_J_ATK  = 0xFFFF8800;
    private static final int COLOR_J_MISS = 0xFFFF4444;

    // ── Key bindings per lane count ────────────────────────────────────────────

    private static final int[] KEYS_4 = {
            GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K
    };
    private static final int[] KEYS_5 = {
            GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_SPACE,
            GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K
    };
    private static final int[] KEYS_6 = {
            GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F,
            GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_L
    };
    private static final String[][] KEY_NAMES = {
            {"D", "F", "J", "K"},
            {"D", "F", "SPC", "J", "K"},
            {"S", "D", "F", "J", "K", "L"}
    };

    // ── Chart / game state ────────────────────────────────────────────────────

    /** Optional chart loaded from a file; null = use built-in sample. */
    private final Chart  chart;
    /** Optional parent screen to return to when closed (e.g. the chart maker). */
    private final Screen parent;

    private int      laneCount;
    private int[]    laneKeys;
    private String[] laneKeyNames;

    private final List<Note>  notes             = new ArrayList<>();
    private final boolean[]   lanePressed       = new boolean[6];
    private final long[]      hitEffectTime     = new long[6];
    /** Per-lane last judgment for the flash display; null = no active flash. */
    private final Judgment[]  hitEffectJudgment = new Judgment[6];

    // Chunithm scoring accumulators
    private int  totalNotes;   // note-units (TAP=1, HOLD head+tail=2)
    private int  cjCount;
    private int  jCount;
    private int  attackCount;
    private int  missCount;

    private int     combo;
    private int     maxCombo;
    private long    gameStartTime;
    private boolean gameStarted;
    private boolean gameOver;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Opens the demo stage (sample notes, no parent). */
    public RhythmGameScreen() {
        this(null, null, 4);
    }

    /** Opens the demo stage with a specific key count. */
    public RhythmGameScreen(int laneCount) {
        this(null, null, laneCount);
    }

    /** Opens the stage with a chart (loaded from the chart maker, no parent). */
    public RhythmGameScreen(Chart chart) {
        this(chart, null, chart != null ? chart.keyCount : 4);
    }

    /** Opens the stage with a chart and a parent screen to return to when closed. */
    public RhythmGameScreen(Chart chart, Screen parent) {
        this(chart, parent, chart != null ? chart.keyCount : 4);
    }

    private RhythmGameScreen(Chart chart, Screen parent, int laneCount) {
        super(Text.translatable("screen.rhythmcraft.rhythm_game"));
        this.chart  = chart;
        this.parent = parent;
        setLaneCount(laneCount);
    }

    // ── Lane helpers ──────────────────────────────────────────────────────────

    private void setLaneCount(int count) {
        this.laneCount = count;
        switch (count) {
            case 5  -> { laneKeys = KEYS_5; laneKeyNames = KEY_NAMES[1]; }
            case 6  -> { laneKeys = KEYS_6; laneKeyNames = KEY_NAMES[2]; }
            default -> { laneKeys = KEYS_4; laneKeyNames = KEY_NAMES[0]; }
        }
    }

    // ── Screen lifecycle ──────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();

        notes.clear();
        if (chart != null && !chart.notes.isEmpty()) {
            setLaneCount(chart.keyCount);
            notes.addAll(chart.toNotes());
        } else {
            buildSampleNotes();
        }
        totalNotes = computeTotalNotes();

        cjCount = jCount = attackCount = missCount = 0;
        combo = maxCombo = 0;
        for (int i = 0; i < 6; i++) hitEffectJudgment[i] = null;

        gameOver      = false;
        gameStartTime = System.currentTimeMillis();
        gameStarted   = true;
    }

    private void buildSampleNotes() {
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
            addNote(1,             NoteType.TAP, 5750, 0);
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

    /** Counts total scored note-units: TAP = 1, HOLD = 2 (head + tail). */
    private int computeTotalNotes() {
        int n = 0;
        for (Note note : notes) {
            if (note.lane < laneCount) {
                n++;
                if (note.isHold()) n++;
            }
        }
        return n; // 0 is handled by computeScore()'s guard
    }

    // ── Scoring ───────────────────────────────────────────────────────────────

    /**
     * Scoring formula (max = 1 010 000):
     * <pre>
     *   unit  = 1 000 000 / totalNotes
     *   score = CJ × unit×101% + J × unit×100% + A × unit×60%
     *         = (cjCount×101 + jCount×100 + attackCount×60) × 10 000 / totalNotes
     * </pre>
     */
    private int computeScore() {
        if (totalNotes <= 0) return 0;
        long raw = (long) (cjCount * 101 + jCount * 100 + attackCount * 60) * 10_000L / totalNotes;
        return (int) Math.min(raw, 1_010_000L);
    }

    private static Judgment judgeByDiff(long diffMs) {
        if (diffMs <= WINDOW_CJ)  return Judgment.CRITICAL_JUSTICE;
        if (diffMs <= WINDOW_J)   return Judgment.JUSTICE;
        if (diffMs <= WINDOW_ATK) return Judgment.ATTACK;
        return Judgment.MISS;
    }

    private void recordJudgment(int lane, Judgment j) {
        switch (j) {
            case CRITICAL_JUSTICE -> { cjCount++;     combo++; if (combo > maxCombo) maxCombo = combo; }
            case JUSTICE          -> { jCount++;      combo++; if (combo > maxCombo) maxCombo = combo; }
            case ATTACK           -> { attackCount++; combo = 0; }
            case MISS             -> { missCount++;   combo = 0; }
        }
        hitEffectJudgment[lane] = j;
        hitEffectTime[lane]     = System.currentTimeMillis();
    }

    private static String gradeFor(int score) {
        if (score >= 1_005_000) return "SSS+";
        if (score >= 1_000_000) return "SSS";
        if (score >=   990_000) return "SS";
        if (score >=   975_000) return "S";
        if (score >=   950_000) return "AAA";
        if (score >=   900_000) return "AA";
        if (score >=   800_000) return "A";
        if (score >=   700_000) return "B";
        if (score >=   500_000) return "C";
        return "D";
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long elapsed = gameStarted ? System.currentTimeMillis() - gameStartTime : 0L;

        int playW  = laneCount * LANE_WIDTH;
        int playX  = (this.width - playW) / 2;
        int judgeY = this.height - JUDGMENT_OFFSET;

        context.fill(0, 0, this.width, this.height, COLOR_BG);

        for (int i = 0; i < laneCount; i++) {
            int lx = playX + i * LANE_WIDTH;
            context.fill(lx, 0, lx + LANE_WIDTH, this.height,
                    (i % 2 == 0) ? COLOR_LANE_EVEN : COLOR_LANE_ODD);
            context.fill(lx + LANE_WIDTH - LANE_BORDER, 0,
                    lx + LANE_WIDTH, this.height, COLOR_LANE_BORDER);
        }
        context.fill(playX, 0, playX + LANE_BORDER, this.height, COLOR_LANE_BORDER);

        // Attack / Miss lane flash
        long now = System.currentTimeMillis();
        for (int i = 0; i < laneCount; i++) {
            Judgment j = hitEffectJudgment[i];
            if (j != null && (j == Judgment.ATTACK || j == Judgment.MISS)
                    && now - hitEffectTime[i] < 200) {
                int lx = playX + i * LANE_WIDTH;
                context.fill(lx, 0, lx + LANE_WIDTH, judgeY, COLOR_MISS_FLASH);
            }
        }

        for (Note note : notes) {
            if (note.lane < laneCount) renderNote(context, note, elapsed, playX, judgeY);
        }

        context.fill(playX, judgeY - 2, playX + playW, judgeY + 2, COLOR_JUDGMENT_LINE);
        renderKeyBoxes(context, playX, judgeY);
        renderHud(context, playX, playW);

        autoJudge(elapsed);

        if (gameOver) renderGameOver(context);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderNote(DrawContext ctx, Note note, long elapsed,
                            int playX, int judgeY) {
        if (note.isHit() && !note.isHoldActive()) return;

        long timeToHead = note.hitTime - elapsed;
        if (timeToHead > NOTE_TRAVEL_MS + 500) return;

        int noteW = LANE_WIDTH - 6;
        int noteX = playX + note.lane * LANE_WIDTH + 3;
        int headY = judgeY - (int) (timeToHead * judgeY / NOTE_TRAVEL_MS);

        if (note.isTap()) {
            if (timeToHead < -WINDOW_ATK * 3) return;
            ctx.fill(noteX, headY - 10, noteX + noteW, headY - 6, COLOR_TAP_ACCENT);
            ctx.fill(noteX, headY - 6,  noteX + noteW, headY + 4, COLOR_TAP_NOTE);
        } else {
            long timeToTail = (note.hitTime + note.duration) - elapsed;
            if (timeToHead < -WINDOW_ATK * 3 && !note.isHoldActive()) return;

            int tailY    = judgeY - (int) (timeToTail * judgeY / NOTE_TRAVEL_MS);
            boolean active = note.isHoldActive();
            int bodyColor = active ? COLOR_HOLD_BODY_ACTIVE : COLOR_HOLD_BODY;
            int capColor  = active ? COLOR_HOLD_ACTIVE      : COLOR_HOLD_HEAD;

            int bodyTop = Math.max(0, tailY);
            int bodyBot = active ? judgeY : headY;
            if (bodyTop < bodyBot) {
                ctx.fill(noteX + noteW / 4, bodyTop, noteX + 3 * noteW / 4, bodyBot, bodyColor);
            }
            if (tailY < judgeY) {
                ctx.fill(noteX, tailY - 5, noteX + noteW, tailY + 5, capColor);
            }
            if (!active && headY <= judgeY + 20) {
                ctx.fill(noteX, headY - 6, noteX + noteW, headY + 4, capColor);
            }
        }
    }

    private void renderKeyBoxes(DrawContext ctx, int playX, int judgeY) {
        int boxY = judgeY + 6;
        long now = System.currentTimeMillis();
        for (int i = 0; i < laneCount; i++) {
            int lx = playX + i * LANE_WIDTH;
            int bg = lanePressed[i] ? COLOR_KEY_PRESSED : COLOR_KEY_IDLE;
            ctx.fill(lx + 4, boxY, lx + LANE_WIDTH - 4, boxY + KEY_BOX_HEIGHT, bg);
            ctx.drawCenteredTextWithShadow(this.textRenderer, laneKeyNames[i],
                    lx + LANE_WIDTH / 2, boxY + (KEY_BOX_HEIGHT - 9) / 2, 0xFFFFFFFF);

            Judgment j = hitEffectJudgment[i];
            if (j != null && now - hitEffectTime[i] < 600) {
                String jText = switch (j) {
                    case CRITICAL_JUSTICE -> "CJ";
                    case JUSTICE          -> "J";
                    case ATTACK           -> "ATK";
                    case MISS             -> "MISS";
                };
                int jColor = switch (j) {
                    case CRITICAL_JUSTICE -> COLOR_J_CJ;
                    case JUSTICE          -> COLOR_J_J;
                    case ATTACK           -> COLOR_J_ATK;
                    case MISS             -> COLOR_J_MISS;
                };
                ctx.drawCenteredTextWithShadow(this.textRenderer, jText,
                        lx + LANE_WIDTH / 2, boxY - 14, jColor);
            }
        }
    }

    private void renderHud(DrawContext ctx, int playX, int playW) {
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.getTitle(),
                this.width / 2, 6, 0xFFE6EDF3);

        ctx.drawTextWithShadow(this.textRenderer,
                Text.translatable("screen.rhythmcraft.score", computeScore()),
                playX, 20, 0xFFE6EDF3);

        if (combo > 1) {
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.translatable("screen.rhythmcraft.combo", combo),
                    playX, 32, 0xFFFFD700);
        }

        String modeHint = laneCount + "K  [Tab: " + (chart == null ? "cycle mode" : "retry") + "]";
        int hintX = playX + playW - this.textRenderer.getWidth(modeHint);
        ctx.drawTextWithShadow(this.textRenderer, modeHint,
                hintX, this.height - 12, 0xFF6E7681);
    }

    private void renderGameOver(DrawContext ctx) {
        int finalScore = computeScore();
        String grade   = gradeFor(finalScore);
        int cx = this.width / 2;
        int cy = this.height / 2 - 40;

        ctx.fill(cx - 150, cy - 8, cx + 150, cy + 78, 0xCC000000);

        ctx.drawCenteredTextWithShadow(this.textRenderer, "── CLEAR ──",
                cx, cy,      0xFFFFD700);
        ctx.drawCenteredTextWithShadow(this.textRenderer, grade,
                cx, cy + 12, 0xFFFFD700);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.rhythmcraft.score", finalScore),
                cx, cy + 26, 0xFFE6EDF3);

        String breakdown = String.format("CJ:%d  J:%d  ATK:%d  MISS:%d",
                cjCount, jCount, attackCount, missCount);
        ctx.drawCenteredTextWithShadow(this.textRenderer, breakdown,
                cx, cy + 40, 0xFF8B949E);

        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.rhythmcraft.max_combo", maxCombo),
                cx, cy + 52, 0xFFE6EDF3);

        String hint = chart == null
                ? "[Tab] Retry / cycle mode   [Esc] Exit"
                : "[Tab] Retry   [Esc] Exit";
        ctx.drawCenteredTextWithShadow(this.textRenderer, hint,
                cx, cy + 66, 0xFF6E7681);
    }

    // ── Game logic ────────────────────────────────────────────────────────────

    private void autoJudge(long elapsed) {
        boolean allDone = true;
        for (Note note : notes) {
            if (note.lane >= laneCount) continue;

            if (!note.isHit() && !note.isHoldActive()) {
                if (note.hitTime - elapsed < -WINDOW_ATK) {
                    note.setHit(true);
                    note.setJudgment(Judgment.MISS);
                    recordJudgment(note.lane, Judgment.MISS);
                    // For a HOLD whose head was never pressed, the tail is also implicitly
                    // missed.  We do NOT call recordJudgment a second time (that would cause
                    // a duplicate flash); instead increment missCount directly for the
                    // result-screen breakdown.
                    if (note.isHold()) missCount++;
                }
            }

            // Auto-complete an active hold whose tail has passed the window → CJ tail
            if (note.isHold() && note.isHoldActive()) {
                long tailTime = note.hitTime + note.duration;
                if (elapsed > tailTime + WINDOW_ATK) {
                    note.setHoldActive(false);
                    note.setHit(true);
                    recordJudgment(note.lane, Judgment.CRITICAL_JUSTICE);
                }
            }

            if (!note.isHit() && !note.isHoldActive()) allDone = false;
        }
        if (!gameOver && allDone && !notes.isEmpty()) gameOver = true;
    }

    // ── Input handling ────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            if (chart == null) {
                int next = laneCount == 6 ? 4 : laneCount + 1;
                setLaneCount(next);
            }
            init();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
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

    private void tryPressLane(int lane) {
        if (!gameStarted || gameOver) return;
        long elapsed = System.currentTimeMillis() - gameStartTime;

        Note best     = null;
        long bestDiff = Long.MAX_VALUE;
        for (Note note : notes) {
            if (note.isHit() || note.isHoldActive() || note.lane != lane) continue;
            long diff = Math.abs(note.hitTime - elapsed);
            if (diff <= WINDOW_ATK && diff < bestDiff) {
                bestDiff = diff;
                best     = note;
            }
        }
        if (best == null) return;

        Judgment j = judgeByDiff(bestDiff);
        best.setJudgment(j);
        if (best.isTap()) {
            best.setHit(true);
        } else {
            best.setHoldActive(true); // HOLD head — tail judged on release
        }
        recordJudgment(lane, j);
    }

    private void tryReleaseLane(int lane) {
        if (!gameStarted) return;
        long elapsed = System.currentTimeMillis() - gameStartTime;

        for (Note note : notes) {
            if (!note.isHold() || !note.isHoldActive() || note.lane != lane) continue;
            note.setHoldActive(false);
            note.setHit(true);

            long tailTime = note.hitTime + note.duration;
            long earlyMs  = tailTime - elapsed; // positive = released before tail
            long diff     = Math.abs(tailTime - elapsed);
            Judgment tailJ;
            if (earlyMs > WINDOW_ATK) {
                tailJ = Judgment.MISS;   // released far too early
            } else if (earlyMs > 0) {
                // Released early but within the hit window: cap at JUSTICE
                tailJ = diff <= WINDOW_J ? Judgment.JUSTICE : Judgment.ATTACK;
            } else {
                // Released on time or slightly late
                tailJ = judgeByDiff(diff);
            }
            recordJudgment(lane, tailJ);
            break;
        }
    }

    // ── Screen behaviour ──────────────────────────────────────────────────────

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
