package com.jeremykenedy.firetv.ui.nativeapp.appearance;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/** Source-owned, deterministic effects. The overlay never paints a backing color. */
public final class AppearanceView extends View {
    private final boolean overlay;
    private AppearanceOptions options = AppearanceOptions.defaults();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint ground = new Paint(Paint.DITHER_FLAG);
    private final Path shape = new Path();
    private boolean resumed, attached, scheduled, reducedMotion;
    private final AppearanceClock clock = new AppearanceClock();
    private final Runnable frame = new Runnable() {
        @Override public void run() {
            scheduled = false;
            if (!running()) return;
            clock.frame(SystemClock.uptimeMillis(), overlay ? options.overlaySpeed : options.baseSpeed);
            invalidate(); schedule();
        }
    };
    public AppearanceView(Context context, boolean overlay) { super(context); this.overlay = overlay; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    public void setOptions(AppearanceOptions value) { options = value; gradient(); invalidate(); update(); }
    public void setReducedMotion(boolean value) { reducedMotion = value; invalidate(); update(); }
    public void setResumed(boolean value) { resumed = value; update(); }
    public boolean isFrameScheduled() { return scheduled; }
    public long animationMillis() { return clock.elapsedMillis(); }
    private boolean running() { return !reducedMotion && attached && resumed && hasWindowFocus() && isShown() && getWindowVisibility() == VISIBLE
            && (overlay ? options.overlayAnimated() : options.baseAnimated()); }
    private void schedule() { if (!scheduled && running()) { scheduled = true; postDelayed(frame, 33); } }
    private void update() { if (!running()) { removeCallbacks(frame); scheduled = false; clock.pause(); } else schedule(); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); attached = true; update(); }
    @Override protected void onDetachedFromWindow() { attached = false; update(); super.onDetachedFromWindow(); }
    @Override protected void onVisibilityChanged(View view, int visibility) { super.onVisibilityChanged(view, visibility); if (frame != null) update(); }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); if (frame != null) update(); }
    @Override public void onWindowFocusChanged(boolean focus) { super.onWindowFocusChanged(focus); update(); }
    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) { gradient(); }
    private void gradient() {
        int top = 0xff111c35, bottom = 0xff29334c;
        if (options.base.equals("still_graphite")) { top=0xff171b20; bottom=0xff424044; }
        else if (options.base.equals("still_forest")) { top=0xff102d2b; bottom=0xff395149; }
        else if (options.base.equals("still_plum") || options.base.equals("silk")) { top=0xff241a36; bottom=0xff59405b; }
        else if (options.base.equals("embers")) { top=0xff231924; bottom=0xff613e30; }
        else if (options.base.equals("aurora") || options.base.equals("waves")) { top=0xff102834; bottom=0xff28534f; }
        int legacy = options.base.equals("still_legacy_gradient") ? LegacyGradients.index(options.legacyGradient) : -1;
        if (legacy >= 0) { top = LegacyGradients.top(legacy); bottom = LegacyGradients.bottom(legacy); }
        else {
            PaletteRecipes.Theme recipe=PaletteRecipes.find(options.basePalette);
            if(recipe!=null) { top=recipe.dark; bottom=recipe.middle; }
        }
        ground.setShader(new LinearGradient(0, 0, legacy >= 0 ? 0 : Math.max(1,getWidth()), Math.max(1,getHeight()), top, bottom, Shader.TileMode.CLAMP));
    }
    @Override protected void onDraw(Canvas canvas) {
        String style = overlay ? options.overlay : options.base;
        if (style.equals("off") || (!overlay && !options.scene.isEmpty())
                || (!overlay && style.equals("still_legacy_gradient") && LegacyGradients.index(options.legacyGradient) < 0)) return;
        int strength = overlay ? options.overlayOpacity : options.brightness;
        int density = overlay ? options.overlayIntensity : options.baseIntensity;
        double seconds = clock.elapsedMillis() / 1000.0;
        double phase = clock.phaseSeconds();
        if (!overlay) {
            canvas.drawRect(0,0,getWidth(),getHeight(),ground);
            if (!style.equals("still_legacy_gradient")) { paint.setColor(Color.BLACK); paint.setAlpha(Math.round((100-strength)*1.5f)); canvas.drawRect(0,0,getWidth(),getHeight(),paint); }
        }
        if (style.startsWith("still_") || density == 0 || (overlay && strength == 0)) return;
        float scale = Math.max(0.5f, getHeight()/1080f);
        if ((style.equals("aurora") || style.equals("waves") || style.equals("silk") || style.equals("prism") || style.equals("rays") || style.equals("orbits"))) {
            ribbons(canvas, style, phase, density, scale); return;
        }
        int count = style.equals("snowflakes") ? 18+density*3/4 : 24+density*2;
        float width=getWidth(), height=getHeight();
        boolean rising = style.equals("hearts_floating") || style.equals("embers") || style.equals("lanterns");
        PaletteRecipes.Theme recipe=PaletteRecipes.find(overlay ? options.overlayPalette : options.basePalette);
        for (int i=0;i<count;i++) {
            double depth=0.35+fraction(i*19.137)*0.65;
            double position=fraction(i*0.61803398875 + phase*(0.014+depth*0.021));
            float y=(float)((rising ? 1-position : position)*(height+80*scale)-40*scale);
            float x=(float)(fraction(i*0.754877666)*width+Math.sin(phase*0.4+i)*18*scale*depth);
            float size=(float)((2+depth*5)*scale);
            int color=0xfff3f7ff;
            if (style.equals("leaves")) color=i%3==0 ? 0xffe9b75a : i%3==1 ? 0xffb75b32 : 0xffd9893e;
            if (style.startsWith("hearts")) color=i%2==0 ? 0xffef7198 : 0xfff9b7cd;
            if (style.equals("petals")) color=0xffffc8df;
            if (style.equals("embers") || style.equals("lanterns")) color=0xffffc675;
            if(recipe!=null) color=i%2==0 ? recipe.light : recipe.accent;
            paint.setColor(color); paint.setAlpha((int)((overlay ? strength/100f : 0.75f)*(130+depth*125)));
            canvas.save(); canvas.translate(x,y);
            if (style.equals("snowflakes")) {
                canvas.rotate((float)(i*31+phase*9)); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Math.max(1,scale*1.3f));
                int alpha=paint.getAlpha(), flakeColor=paint.getColor();
                for(int spoke=0;spoke<6;spoke++) {
                    paint.setColor(0xff30445a); paint.setAlpha(alpha/2); paint.setStrokeWidth(Math.max(2,scale*3.5f));
                    canvas.drawLine(0,0,0,size*1.8f,paint);
                    paint.setColor(flakeColor); paint.setAlpha(alpha); paint.setStrokeWidth(Math.max(1,scale*1.3f));
                    canvas.drawLine(0,0,0,size*1.8f,paint); canvas.drawLine(0,size, -size*.35f,size*.6f,paint); canvas.drawLine(0,size,size*.35f,size*.6f,paint); canvas.rotate(60);
                }
                paint.setStyle(Paint.Style.FILL);
            } else if (style.startsWith("hearts")) {
                canvas.rotate((float)(Math.sin(seconds*.4+i)*16)); shape.reset();
                shape.moveTo(0,size); shape.cubicTo(-size*2,-size*.2f,-size,-size*1.9f,0,-size*.8f);
                shape.cubicTo(size,-size*1.9f,size*2,-size*.2f,0,size); canvas.drawPath(shape,paint);
            } else if (style.equals("leaves") || style.equals("petals")) {
                canvas.rotate((float)(i*47+phase*24)); shape.reset(); shape.moveTo(-size*1.5f,0);
                shape.quadTo(0,-size*1.4f,size*1.5f,0); shape.quadTo(0,size*1.4f,-size*1.5f,0); canvas.drawPath(shape,paint);
                paint.setColor(0xff70452e); paint.setAlpha((int)(strength*1.5f)); paint.setStrokeWidth(scale); canvas.drawLine(-size,0,size,0,paint);
            } else if (style.equals("rain")) { paint.setStrokeWidth(scale); canvas.drawLine(0,0,-size,size*5,paint); }
            else if (style.equals("stars")) { canvas.drawRect(-size*.2f,-size,size*.2f,size,paint); canvas.drawRect(-size,-size*.2f,size,size*.2f,paint); }
            else {
                if (style.equals("bokeh")) size*=5;
                if (style.equals("snow")) {
                    int alpha=paint.getAlpha(), snowColor=paint.getColor();
                    paint.setColor(0xff30445a); paint.setAlpha(alpha/2); canvas.drawCircle(0,0,size+scale*1.5f,paint);
                    paint.setColor(snowColor); paint.setAlpha(alpha);
                }
                canvas.drawCircle(0,0,size,paint);
            }
            canvas.restore();
            if (style.equals("constellation") && i>0 && i%4!=0) {
                paint.setAlpha(35); paint.setStrokeWidth(scale); float lastX=(float)(fraction((i-1)*0.754877666)*width);
                if (Math.abs(lastX-x)<width*.25f) canvas.drawLine(x,y,lastX,y+25*scale,paint);
            }
        }
    }
    private void ribbons(Canvas canvas, String style, double time, int density, float scale) {
        int count=3+density/20;
        PaletteRecipes.Theme recipe=PaletteRecipes.find(overlay ? options.overlayPalette : options.basePalette);
        for(int i=0;i<count;i++) {
            paint.setColor(recipe==null ? (i%2==0 ? 0xff72b5b4 : 0xff8994d0) : (i%2==0 ? recipe.light : recipe.accent));
            paint.setAlpha(Math.round((18+i*3)*(overlay ? options.overlayOpacity/100f : 1f)));
            float width=getWidth(), height=getHeight();
            float y=(float)((i+1.0)/(count+1)*height+Math.sin(time*.15+i)*height*.07);
            if(style.equals("orbits")) {
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth((2+i)*scale);
                canvas.save(); canvas.rotate((float)(time*2+i*22),width*.5f,height*.5f);
                float radius=height*(.24f+i*.055f);
                canvas.drawOval(width*.5f-radius*1.9f,height*.5f-radius*.5f,width*.5f+radius*1.9f,height*.5f+radius*.5f,paint);
                canvas.restore();
            } else if(style.equals("prism") || style.equals("rays")) {
                paint.setStyle(Paint.Style.FILL); shape.reset();
                if(style.equals("rays")) {
                    canvas.save(); canvas.rotate((float)(time*1.5+i*24),width*.16f,height*.22f);
                    shape.moveTo(width*.16f,height*.22f); shape.lineTo(width*1.3f,height*(.30f+i*.025f));
                    shape.lineTo(width*1.3f,height*(.39f+i*.025f)); shape.close(); canvas.drawPath(shape,paint); canvas.restore();
                } else {
                    float x=(float)((i+.5f)/count*width+Math.sin(time*.1+i)*width*.07);
                    shape.moveTo(x-width*.25f,height*1.1f); shape.lineTo(x,height*-.1f);
                    shape.lineTo(x+width*.35f,height*1.1f); shape.close(); canvas.drawPath(shape,paint);
                }
            } else {
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth((style.equals("aurora")?100:55)*scale);
                shape.reset();
                if(style.equals("silk")) {
                    shape.moveTo(-100,y+height*.4f); shape.cubicTo(width*.3f,y+height*.4f,width*.55f,y-height*.4f,width+100,y-height*.4f);
                } else if(style.equals("aurora")) {
                    shape.moveTo(-100,y); shape.cubicTo(width*.3f,y-height*.65f,width*.55f,y+height*.35f,width+100,y-height*.3f);
                } else {
                    shape.moveTo(-100,y); shape.cubicTo(width*.25f,y-height*.2f,width*.65f,y+height*.2f,width+100,y);
                }
                canvas.drawPath(shape,paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }
    private static double fraction(double value) { return value-Math.floor(value); }
}
