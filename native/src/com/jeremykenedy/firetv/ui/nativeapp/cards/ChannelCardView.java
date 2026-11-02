package com.jeremykenedy.firetv.ui.nativeapp.cards;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.focus.FocusTileView;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
import java.util.ArrayList;
import java.util.List;

/** One focus identity per feed; rotation replaces only its displayed payload. */
public final class ChannelCardView extends LinearLayout {
    public interface Listener {
        void onSelect(TvRecommendations.Feed feed, TvRecommendations.Program program);
        void onLongPress(TvRecommendations.Feed feed, TvRecommendations.Program program);
    }

    public interface ArtworkListener {
        void onArtworkChanged(ChannelCardView card, Bitmap artwork);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CardRotationDeadline rotationDeadline = new CardRotationDeadline();
    private final FocusTileView tile;
    private final TextView channel;
    private final TextView outsideTitle;
    private final Listener listener;
    private final List<TvRecommendations.Program> programs = new ArrayList<>();
    private TvRecommendations.Feed feed;
    private int index;
    private long intervalMillis;
    private boolean hostResumed;
    private boolean attached;
    private boolean hostFocused;
    private String selectedKey;
    private Uri artworkUri;
    private boolean artworkVisible;
    private int artworkEdge;
    private long artworkGeneration;
    private ArtworkLoader.Request artwork;
    private Bitmap artworkBitmap;
    private ArtworkListener artworkListener;
    private final Runnable rotate = new Runnable() {
        @Override public void run() {
            rotationDeadline.reset();
            if (active()) showNextProgram();
        }
    };

    public ChannelCardView(Context context, TvRecommendations.Feed feed, Listener listener) {
        super(context);
        if (feed == null) throw new IllegalArgumentException("A channel card needs a feed.");
        this.feed = feed;
        this.listener = listener;
        setOrientation(VERTICAL);
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        channel = new TextView(context);
        channel.setTextColor(0xffcec7df);
        channel.setTextSize(16);
        channel.setSingleLine(true);
        channel.setEllipsize(TextUtils.TruncateAt.END);
        channel.setPadding(dp(10), 0, dp(10), 0);
        channel.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(channel, new LayoutParams(-1, -2));
        tile = new FocusTileView(context);
        tile.setFocusable(false);
        tile.setClickable(false);
        tile.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        addView(tile, new LayoutParams(-1, 0, 1));
        outsideTitle = new TextView(context);
        outsideTitle.setTextColor(0xffcec7df);
        outsideTitle.setTextSize(17);
        outsideTitle.setSingleLine(true);
        outsideTitle.setEllipsize(TextUtils.TruncateAt.END);
        outsideTitle.setGravity(android.view.Gravity.CENTER);
        outsideTitle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(outsideTitle, new LayoutParams(-1, -2));
        setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) {
                if (ChannelCardView.this.listener != null)
                    ChannelCardView.this.listener.onSelect(ChannelCardView.this.feed, currentProgram());
            }
        });
        setOnLongClickListener(new OnLongClickListener() {
            @Override public boolean onLongClick(View view) {
                if (ChannelCardView.this.listener == null) return false;
                ChannelCardView.this.listener.onLongPress(ChannelCardView.this.feed, currentProgram());
                return true;
            }
        });
        updateFeed(feed);
    }

    public TvRecommendations.Feed feed() { return feed; }

    public void setArtworkListener(ArtworkListener listener) {
        artworkListener = listener;
        notifyArtwork();
    }

    private void notifyArtwork() {
        if (artworkListener != null)
            artworkListener.onArtworkChanged(this, active() && (isFocused() || hostFocused) ? artworkBitmap : null);
    }

    public TvRecommendations.Program currentProgram() {
        return programs.isEmpty() ? null : programs.get(index);
    }

    public String selectedProgramKey() { return selectedKey; }

    public void restoreProgramKey(String key) {
        selectedKey = key;
        for (int next = 0; next < programs.size(); next++) {
            if (programs.get(next).key.equals(key)) { index = next; display(); schedule(); return; }
        }
    }

    public void setHostFocused(boolean focused) {
        hostFocused = focused;
        tile.setPreviewFocused(focused || isFocused());
        notifyArtwork();
    }

    public void updateFeed(TvRecommendations.Feed value) {
        updateFeed(value, value == null ? null : value.programs);
    }

    public void updateFeed(TvRecommendations.Feed value, List<TvRecommendations.Program> visible) {
        if (value == null || !feed.item.id.equals(value.item.id))
            throw new IllegalArgumentException("Update a channel card with the same feed identity.");
        feed = value;
        channel.setText(value.title);
        setPrograms(visible);
    }

    /** The coordinator supplies the visible items after applying source-scoped hide preferences. */
    public void setPrograms(List<TvRecommendations.Program> visible) {
        String key = currentProgram() == null ? selectedKey : currentProgram().key;
        programs.clear();
        if (visible != null) for (TvRecommendations.Program program : visible) {
            if (program != null && feed.source.equals(program.source) && belongsToFeed(program))
                programs.add(program);
        }
        index = 0;
        for (int next = 0; next < programs.size(); next++)
            if (programs.get(next).key.equals(key)) { index = next; break; }
        display();
        schedule();
    }

    public void setRotationIntervalMillis(long value) {
        if (value < 0 || value > 86400000L)
            throw new IllegalArgumentException("Choose Manual or a timer up to one day.");
        intervalMillis = value;
        schedule();
    }

    public void showNextProgram() {
        if (programs.size() > 1) {
            index = (index + 1) % programs.size();
            display();
        }
        schedule();
    }

    public void showPreviousProgram() {
        if (programs.size() > 1) {
            index = (index + programs.size() - 1) % programs.size();
            display();
        }
        schedule();
    }

    public void onHostResume() {
        hostResumed = true;
        tile.refreshPreferences();
        display();
        schedule();
    }

    public void onHostPause() {
        hostResumed = false;
        schedule();
        cancelArtwork();
        artworkVisible = false;
        artworkBitmap = null;
        tile.setIcon(null);
        notifyArtwork();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        boolean compact = MeasureSpec.getSize(heightSpec) < dp(112);
        channel.setVisibility(compact ? GONE : VISIBLE);
        tile.setCompact(compact);
        super.onMeasure(widthSpec, heightSpec);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (tile != null && active()) display();
    }

    @Override protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        if (tile != null) tile.setPreviewFocused(focused || hostFocused);
        notifyArtwork();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        tile.setPreviewFocused(isFocused() || hostFocused);
        display();
        schedule();
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        notifyArtwork();
        schedule();
        cancelArtwork();
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (tile == null) return;
        if (visibility == VISIBLE) display();
        else cancelArtwork();
        notifyArtwork();
        schedule();
    }

    @Override protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        if (tile == null) return;
        if (isShown()) display();
        else cancelArtwork();
        notifyArtwork();
        schedule();
    }

    private boolean belongsToFeed(TvRecommendations.Program value) {
        for (TvRecommendations.Program candidate : feed.programs)
            if (candidate.key.equals(value.key)) return true;
        return false;
    }

    private boolean active() {
        return attached && hostResumed && isShown() && getWindowVisibility() == VISIBLE;
    }

    private void schedule() {
        handler.removeCallbacks(rotate);
        TvRecommendations.Program selected = currentProgram();
        long due = rotationDeadline.update(SystemClock.uptimeMillis(), active() && programs.size() > 1,
                intervalMillis, selected == null ? null : selected.key);
        if (due != 0) handler.postAtTime(rotate, due);
    }

    private void display() {
        if (!com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.ready(getContext())) return;
        final TvRecommendations.Program program = currentProgram();
        if (program != null) selectedKey = program.key;
        String title = program == null ? "No visible items" : program.title;
        tile.setTitle(title);
        boolean visible = !CardDisplayPreferences.hideTitles(getContext());
        boolean outside = CardDisplayPreferences.textOutside(getContext());
        tile.setTitleVisible(visible && !outside);
        Integer color = CardDisplayPreferences.textColor(getContext());
        tile.setTitleColor(color);
        outsideTitle.setTextColor(color == null ? 0xffcec7df : color);
        channel.setTextColor(color == null ? 0xffcec7df : color);
        outsideTitle.setText(title);
        outsideTitle.setVisibility(visible && outside ? VISIBLE : GONE);
        tile.setArtworkFit(CardPreferences.artworkFits(getContext()));
        setContentDescription(feed.title + ": " + title);
        Uri nextArtwork = program == null ? null : program.artwork;
        int edge = Math.max(getWidth(), getHeight());
        edge = Math.max(32, Math.min(768, edge > 0 ? edge : dp(320)));
        if (nextArtwork != null && nextArtwork.equals(artworkUri) && edge == artworkEdge
                && (artworkVisible || artwork != null)) return;
        cancelArtwork();
        artworkUri = nextArtwork;
        artworkEdge = edge;
        artworkVisible = false;
        artworkBitmap = null;
        tile.setIcon(null);
        notifyArtwork();
        if (program == null || program.artwork == null || !active()) return;
        if ("android.resource".equals(program.artwork.getScheme())
                && !feed.source.equals(program.artwork.getAuthority())) return;
        final long generation = artworkGeneration;
        artwork = ArtworkLoader.load(getContext(), program.artwork, edge, new ArtworkLoader.Callback() {
            @Override public void onLoaded(Bitmap bitmap) {
                if (generation == artworkGeneration) {
                    artwork = null;
                    if (active() && bitmap != null) {
                        artworkVisible = true;
                        artworkBitmap = bitmap;
                        tile.setIcon(new BitmapDrawable(getResources(), bitmap));
                        notifyArtwork();
                    }
                }
            }
        });
    }

    private void cancelArtwork() {
        artworkGeneration++;
        if (artwork != null) { artwork.cancel(); artwork = null; }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
