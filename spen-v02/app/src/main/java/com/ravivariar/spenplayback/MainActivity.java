package com.ravivariar.spenplayback;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    static class PageState {
        String strokes;
        long playbackPosition;
        PageState(String strokes, long playbackPosition) {
            this.strokes = strokes;
            this.playbackPosition = playbackPosition;
        }
    }

    InkView ink;
    Button play, erase, speed, prevPage, nextPage, addPage, deletePage;
    SeekBar seek;
    TextView time, pageLabel;
    boolean seeking = false;
    SharedPreferences prefs;
    final ArrayList<PageState> pages = new ArrayList<>();
    int currentPage = 0;

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    String formatTime(long millis) {
        long seconds = millis / 1000;
        return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60);
    }

    String speedText(float value) {
        if (value == 0.5f) return "0.5×";
        if (value == 1f) return "1×";
        if (value == 2f) return "2×";
        return "4×";
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("spen_mvp", 0);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("S Pen Playback  •  MVP 0.2 Pages");
        title.setTextSize(18);
        title.setPadding(dp(12), dp(8), dp(12), dp(6));
        root.addView(title);

        LinearLayout pageBar = new LinearLayout(this);
        pageBar.setGravity(Gravity.CENTER_VERTICAL);
        pageBar.setPadding(dp(6), 0, dp(6), 0);
        prevPage = button("◀");
        nextPage = button("▶");
        addPage = button("+ Page");
        deletePage = button("Delete");
        pageLabel = new TextView(this);
        pageLabel.setTextSize(16);
        pageLabel.setGravity(Gravity.CENTER);
        pageLabel.setPadding(dp(8), 0, dp(8), 0);
        pageBar.addView(prevPage);
        pageBar.addView(pageLabel, new LinearLayout.LayoutParams(0, dp(48), 1));
        pageBar.addView(nextPage);
        pageBar.addView(addPage);
        pageBar.addView(deletePage);
        root.addView(pageBar);

        HorizontalScrollView toolScroll = new HorizontalScrollView(this);
        LinearLayout tools = new LinearLayout(this);
        toolScroll.addView(tools);
        root.addView(toolScroll);
        Button undo = button("Undo");
        Button clear = button("Clear");
        erase = button("Eraser: Off");
        play = button("Play");
        speed = button("1×");
        for (Button b : new Button[]{undo, clear, erase, play, speed}) tools.addView(b);

        LinearLayout progressRow = new LinearLayout(this);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        seek = new SeekBar(this);
        seek.setMax(1000);
        progressRow.addView(seek, new LinearLayout.LayoutParams(0, dp(44), 1));
        time = new TextView(this);
        time.setText("00:00 / 00:00");
        time.setPadding(dp(8), 0, dp(8), 0);
        progressRow.addView(time);
        root.addView(progressRow);

        TextView hint = new TextView(this);
        hint.setText("S Pen writes • finger input is ignored on the canvas • pages save automatically");
        hint.setTextColor(Color.GRAY);
        hint.setTextSize(12);
        hint.setPadding(dp(12), 0, dp(12), dp(6));
        root.addView(hint);

        ink = new InkView(this);
        root.addView(ink, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        undo.setOnClickListener(v -> ink.undo());
        clear.setOnClickListener(v -> ink.clearAll());
        erase.setOnClickListener(v -> erase.setText(ink.toggleEraser() ? "Eraser: On" : "Eraser: Off"));
        play.setOnClickListener(v -> { if (ink.playing) ink.pause(); else ink.play(); });
        speed.setOnClickListener(v -> speed.setText(speedText(ink.nextSpeed())));

        prevPage.setOnClickListener(v -> switchPage(currentPage - 1));
        nextPage.setOnClickListener(v -> switchPage(currentPage + 1));
        addPage.setOnClickListener(v -> addPageAfterCurrent());
        deletePage.setOnClickListener(v -> confirmDeletePage());

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    seeking = true;
                    ink.seek(progress / 1000f);
                }
            }
            public void onStartTrackingTouch(SeekBar bar) {
                seeking = true;
                ink.pause();
            }
            public void onStopTrackingTouch(SeekBar bar) {
                seeking = false;
                saveCurrentPage();
            }
        });

        ink.listener = (position, duration, isPlaying) -> runOnUiThread(() -> {
            if (!seeking) seek.setProgress(duration == 0 ? 0 : (int)(1000.0 * position / duration));
            time.setText(formatTime(position) + " / " + formatTime(duration));
            play.setText(isPlaying ? "Pause" : "Play");
        });
        ink.changeListener = this::saveCurrentPage;

        loadNotebook();
        updatePageUi();
        ink.notifyListener();
    }

    void loadNotebook() {
        pages.clear();
        String raw = prefs.getString("notebook_v2", null);
        if (raw != null) {
            try {
                JSONObject notebook = new JSONObject(raw);
                JSONArray savedPages = notebook.getJSONArray("pages");
                for (int i = 0; i < savedPages.length(); i++) {
                    JSONObject p = savedPages.getJSONObject(i);
                    pages.add(new PageState(
                            p.getJSONArray("strokes").toString(),
                            p.optLong("playbackPosition", 0)
                    ));
                }
                currentPage = notebook.optInt("currentPage", 0);
            } catch (Exception ignored) {
                pages.clear();
            }
        }

        if (pages.isEmpty()) {
            String oldSinglePage = prefs.getString("note", null);
            pages.add(new PageState(oldSinglePage != null ? oldSinglePage : "[]", 0));
            currentPage = 0;
        }

        currentPage = Math.max(0, Math.min(currentPage, pages.size() - 1));
        PageState page = pages.get(currentPage);
        ink.loadPage(page.strokes, page.playbackPosition);
        persistNotebook();
    }

    void saveCurrentPage() {
        if (ink == null || pages.isEmpty()) return;
        PageState page = pages.get(currentPage);
        page.strokes = ink.serialize();
        page.playbackPosition = ink.getResumePosition();
        persistNotebook();
    }

    void persistNotebook() {
        try {
            JSONObject notebook = new JSONObject();
            notebook.put("currentPage", currentPage);
            JSONArray savedPages = new JSONArray();
            for (PageState page : pages) {
                JSONObject p = new JSONObject();
                p.put("strokes", new JSONArray(page.strokes));
                p.put("playbackPosition", page.playbackPosition);
                savedPages.put(p);
            }
            notebook.put("pages", savedPages);
            prefs.edit().putString("notebook_v2", notebook.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    void switchPage(int index) {
        if (index < 0 || index >= pages.size() || index == currentPage) return;
        ink.pause();
        saveCurrentPage();
        currentPage = index;
        PageState page = pages.get(currentPage);
        ink.loadPage(page.strokes, page.playbackPosition);
        persistNotebook();
        updatePageUi();
    }

    void addPageAfterCurrent() {
        ink.pause();
        saveCurrentPage();
        currentPage++;
        pages.add(currentPage, new PageState("[]", 0));
        ink.loadPage("[]", 0);
        persistNotebook();
        updatePageUi();
        Toast.makeText(this, "Page " + (currentPage + 1) + " added", Toast.LENGTH_SHORT).show();
    }

    void confirmDeletePage() {
        new AlertDialog.Builder(this)
                .setTitle("Delete page?")
                .setMessage("Delete Page " + (currentPage + 1) + "? This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> deleteCurrentPage())
                .show();
    }

    void deleteCurrentPage() {
        ink.pause();
        if (pages.size() == 1) {
            ink.clearAll();
            pages.get(0).strokes = "[]";
            pages.get(0).playbackPosition = 0;
        } else {
            pages.remove(currentPage);
            if (currentPage >= pages.size()) currentPage = pages.size() - 1;
            PageState page = pages.get(currentPage);
            ink.loadPage(page.strokes, page.playbackPosition);
        }
        persistNotebook();
        updatePageUi();
    }

    void updatePageUi() {
        pageLabel.setText("Page " + (currentPage + 1) + " of " + pages.size());
        prevPage.setEnabled(currentPage > 0);
        nextPage.setEnabled(currentPage < pages.size() - 1);
    }

    @Override
    protected void onPause() {
        ink.pause();
        saveCurrentPage();
        super.onPause();
    }
}

class InkView extends View {
    static class Point {
        float x, y, pressure;
        long time;
        Point(float x, float y, float pressure, long time) {
            this.x = x;
            this.y = y;
            this.pressure = pressure;
            this.time = time;
        }
    }

    static class Stroke {
        ArrayList<Point> points = new ArrayList<>();
    }

    interface PlaybackListener {
        void update(long position, long duration, boolean playing);
    }

    PlaybackListener listener;
    Runnable changeListener;
    ArrayList<Stroke> strokes = new ArrayList<>();
    Stroke currentStroke;
    Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    boolean eraser = false;
    boolean playing = false;
    boolean playMode = false;
    float speed = 1f;
    long position = 0;
    long resumePosition = 0;
    long playStartClock = 0;
    long eventStart = 0;
    long timelineStart = 0;
    long lastEventTime = -1;

    Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!playing) return;
            position = Math.min(duration(), Math.round((SystemClock.uptimeMillis() - playStartClock) * speed));
            resumePosition = position;
            invalidate();
            notifyListener();
            if (position >= duration()) {
                playing = false;
                resumePosition = 0;
                notifyListener();
            } else {
                postOnAnimation(this);
            }
        }
    };

    InkView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(252, 252, 252));
        pen.setColor(Color.rgb(20, 20, 25));
        pen.setStrokeCap(Paint.Cap.ROUND);
        pen.setStrokeJoin(Paint.Join.ROUND);
        grid.setColor(Color.rgb(232, 232, 236));
        grid.setStrokeWidth(dp(1));
    }

    float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    float clamp(float value) {
        return Math.max(0, Math.min(1, value));
    }

    long duration() {
        if (currentStroke != null && !currentStroke.points.isEmpty()) {
            return currentStroke.points.get(currentStroke.points.size() - 1).time;
        }
        if (strokes.isEmpty()) return 0;
        Stroke stroke = strokes.get(strokes.size() - 1);
        return stroke.points.isEmpty() ? 0 : stroke.points.get(stroke.points.size() - 1).time;
    }

    long getResumePosition() {
        return resumePosition;
    }

    void notifyListener() {
        if (listener != null) listener.update(position, duration(), playing);
    }

    void changed() {
        if (changeListener != null) changeListener.run();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (float y = dp(32); y < getHeight(); y += dp(32)) {
            canvas.drawLine(0, y, getWidth(), y, grid);
        }

        if (playMode) {
            for (Stroke stroke : strokes) drawStroke(canvas, stroke, position);
        } else {
            for (Stroke stroke : strokes) drawStroke(canvas, stroke, Long.MAX_VALUE);
            if (currentStroke != null) drawStroke(canvas, currentStroke, Long.MAX_VALUE);
        }
    }

    void drawStroke(Canvas canvas, Stroke stroke, long until) {
        if (stroke.points.size() == 1) {
            Point a = stroke.points.get(0);
            if (a.time <= until) {
                Paint dot = new Paint(pen);
                dot.setStyle(Paint.Style.FILL);
                canvas.drawCircle(a.x * getWidth(), a.y * getHeight(), width(a.pressure) / 2, dot);
            }
            return;
        }

        for (int i = 1; i < stroke.points.size(); i++) {
            Point a = stroke.points.get(i - 1);
            Point b = stroke.points.get(i);
            if (a.time > until) break;

            float endX = b.x;
            float endY = b.y;
            float endPressure = b.pressure;
            if (b.time > until) {
                float fraction = clamp((until - a.time) / (float)Math.max(1, b.time - a.time));
                endX = a.x + (b.x - a.x) * fraction;
                endY = a.y + (b.y - a.y) * fraction;
                endPressure = a.pressure + (b.pressure - a.pressure) * fraction;
            }

            pen.setStrokeWidth(width((a.pressure + endPressure) / 2));
            canvas.drawLine(a.x * getWidth(), a.y * getHeight(), endX * getWidth(), endY * getHeight(), pen);
            if (b.time > until) break;
        }
    }

    float width(float pressure) {
        return dp(3.2f) * (0.45f + 1.15f * clamp(pressure));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int tool = event.getToolType(0);
        boolean stylus = tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER;
        if (!stylus) return false;

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            requestUnbufferedDispatch(event);
            pause();
            playMode = false;
            resumePosition = 0;
            position = duration();
        }

        boolean eraseNow = eraser
                || tool == MotionEvent.TOOL_TYPE_ERASER
                || (event.getButtonState() & MotionEvent.BUTTON_STYLUS_PRIMARY) != 0;
        if (eraseNow) {
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                eraseAt(event.getX(), event.getY());
            }
            return true;
        }

        if (action == MotionEvent.ACTION_DOWN) {
            currentStroke = new Stroke();
            eventStart = event.getEventTime();
            long gap = lastEventTime < 0
                    ? (strokes.isEmpty() ? 0 : 220)
                    : Math.min(5000, Math.max(0, event.getEventTime() - lastEventTime));
            timelineStart = duration() + gap;
            addHistory(event);
            addPoint(event.getX(), event.getY(), event.getPressure(), event.getEventTime());
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE && currentStroke != null) {
            addHistory(event);
            addPoint(event.getX(), event.getY(), event.getPressure(), event.getEventTime());
            return true;
        }

        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && currentStroke != null) {
            addHistory(event);
            addPoint(event.getX(), event.getY(), event.getPressure(), event.getEventTime());
            if (!currentStroke.points.isEmpty()) strokes.add(currentStroke);
            currentStroke = null;
            lastEventTime = event.getEventTime();
            position = duration();
            resumePosition = 0;
            invalidate();
            notifyListener();
            changed();
            return true;
        }
        return true;
    }

    void addHistory(MotionEvent event) {
        for (int i = 0; i < event.getHistorySize(); i++) {
            addPoint(
                    event.getHistoricalX(0, i),
                    event.getHistoricalY(0, i),
                    event.getHistoricalPressure(0, i),
                    event.getHistoricalEventTime(i)
            );
        }
    }

    void addPoint(float x, float y, float pressure, long eventTime) {
        if (currentStroke == null || getWidth() == 0 || getHeight() == 0) return;
        currentStroke.points.add(new Point(
                clamp(x / getWidth()),
                clamp(y / getHeight()),
                clamp(pressure),
                timelineStart + Math.max(0, eventTime - eventStart)
        ));
        invalidate();
    }

    void eraseAt(float x, float y) {
        float radius = dp(28);
        float radiusSquared = radius * radius;
        for (int i = strokes.size() - 1; i >= 0; i--) {
            for (Point point : strokes.get(i).points) {
                float dx = point.x * getWidth() - x;
                float dy = point.y * getHeight() - y;
                if (dx * dx + dy * dy <= radiusSquared) {
                    strokes.remove(i);
                    lastEventTime = -1;
                    position = duration();
                    resumePosition = 0;
                    invalidate();
                    notifyListener();
                    changed();
                    return;
                }
            }
        }
    }

    boolean toggleEraser() {
        eraser = !eraser;
        return eraser;
    }

    void undo() {
        pause();
        playMode = false;
        if (!strokes.isEmpty()) strokes.remove(strokes.size() - 1);
        lastEventTime = -1;
        position = duration();
        resumePosition = 0;
        invalidate();
        notifyListener();
        changed();
    }

    void clearAll() {
        pause();
        playMode = false;
        strokes.clear();
        currentStroke = null;
        lastEventTime = -1;
        position = 0;
        resumePosition = 0;
        invalidate();
        notifyListener();
        changed();
    }

    void play() {
        if (duration() == 0) return;
        long startPosition = (resumePosition > 0 && resumePosition < duration()) ? resumePosition : 0;
        position = startPosition;
        playMode = true;
        playing = true;
        playStartClock = SystemClock.uptimeMillis() - Math.round(position / speed);
        removeCallbacks(frame);
        postOnAnimation(frame);
        notifyListener();
    }

    void pause() {
        if (!playing) return;
        playing = false;
        resumePosition = position;
        removeCallbacks(frame);
        notifyListener();
    }

    void seek(float fraction) {
        playMode = true;
        position = Math.round(clamp(fraction) * duration());
        resumePosition = position;
        invalidate();
        notifyListener();
    }

    float nextSpeed() {
        speed = speed == 0.5f ? 1f : speed == 1f ? 2f : speed == 2f ? 4f : 0.5f;
        if (playing) playStartClock = SystemClock.uptimeMillis() - Math.round(position / speed);
        return speed;
    }

    String serialize() {
        try {
            JSONArray all = new JSONArray();
            for (Stroke stroke : strokes) {
                JSONArray points = new JSONArray();
                for (Point point : stroke.points) {
                    JSONArray p = new JSONArray();
                    p.put(point.x);
                    p.put(point.y);
                    p.put(point.pressure);
                    p.put(point.time);
                    points.put(p);
                }
                all.put(points);
            }
            return all.toString();
        } catch (Exception ignored) {
            return "[]";
        }
    }

    void loadPage(String raw, long savedPlaybackPosition) {
        pause();
        ArrayList<Stroke> loaded = new ArrayList<>();
        try {
            JSONArray all = new JSONArray(raw);
            for (int i = 0; i < all.length(); i++) {
                Stroke stroke = new Stroke();
                JSONArray points = all.getJSONArray(i);
                for (int j = 0; j < points.length(); j++) {
                    JSONArray p = points.getJSONArray(j);
                    stroke.points.add(new Point(
                            (float)p.getDouble(0),
                            (float)p.getDouble(1),
                            (float)p.getDouble(2),
                            p.getLong(3)
                    ));
                }
                loaded.add(stroke);
            }
        } catch (Exception ignored) {
            loaded.clear();
        }

        strokes = loaded;
        currentStroke = null;
        lastEventTime = -1;
        playMode = false;
        resumePosition = Math.max(0, Math.min(savedPlaybackPosition, duration()));
        position = resumePosition;
        invalidate();
        notifyListener();
    }
}
