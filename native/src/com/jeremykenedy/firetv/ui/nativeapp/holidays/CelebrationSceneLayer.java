package com.jeremykenedy.firetv.ui.nativeapp.holidays;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/** Light positions belong to the original scene artwork and follow its center crop. */
final class CelebrationSceneLayer {
    private static final float[] HANUKKAH = {
        .719f,.414f,.744f,.414f,.769f,.414f,.794f,.414f,.823f,.339f,
        .852f,.414f,.877f,.414f,.902f,.414f,.927f,.414f
    };
    private static final float[] DIWALI = {
        .096f,.590f,.162f,.600f,.194f,.619f,.261f,.598f,.141f,.667f,
        .229f,.660f,.289f,.710f,.179f,.740f,.221f,.804f,.281f,.874f,
        .551f,.834f,.593f,.932f,.838f,.846f,.660f,.781f
    };
    private static final float[] EID = {.815f,.315f,.927f,.298f,.855f,.452f,.124f,.838f,.774f,.843f};
    private static final float[] LUNAR = {.119f,.151f,.047f,.242f,.927f,.121f,.966f,.711f,.679f,.577f,.845f,.574f};
    private static final float[] VALENTINE = {.912f,.278f,.957f,.395f};
    private static final float[] ST_PATRICK = {.804f,.546f,.875f,.545f};
    private static final float[] EASTER = {.268f,.481f};
    private static final float[] THANKSGIVING = {.901f,.226f,.751f,.556f,.791f,.562f};
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private double seconds;

    CelebrationSceneLayer() {
        glow.setShader(new RadialGradient(0,0,1,new int[]{0xffffd789,0x50ffb548,Color.TRANSPARENT},
                new float[]{0,.3f,1},Shader.TileMode.CLAMP));
    }

    boolean supports(String scene) { return positions(scene) != null; }
    void reset() { seconds = 0; }
    void advance(long elapsed, int speed) {
        seconds = (seconds + Math.max(0,Math.min(100,elapsed)) / 1000.0
                * (.25 + Math.max(0,Math.min(100,speed)) / 50.0)) % 3600;
    }

    void draw(Canvas canvas, RectF bounds, String scene) {
        float[] lights = positions(scene);
        if (lights == null) return;
        float radius = bounds.width() * (scene.equals("hanukkah") ? .023f : .045f);
        for (int i=0;i<lights.length;i+=2) {
            float flicker = (float)(.55 + .25*Math.sin(seconds*2.1+i) + .2*Math.sin(seconds*3.7+i*.8));
            glow.setAlpha(Math.round(28+flicker*54));
            int save=canvas.save();
            canvas.translate(bounds.left+bounds.width()*lights[i],bounds.top+bounds.height()*lights[i+1]);
            canvas.scale(radius*(.95f+flicker*.05f),radius);
            canvas.drawCircle(0,0,1,glow);
            canvas.restoreToCount(save);
        }
    }

    private static float[] positions(String scene) {
        if ("hanukkah".equals(scene)) return HANUKKAH;
        if ("diwali".equals(scene)) return DIWALI;
        if ("eid_fitr".equals(scene)) return EID;
        if ("lunar_new_year".equals(scene)) return LUNAR;
        if ("valentine".equals(scene)) return VALENTINE;
        if ("st_patrick".equals(scene)) return ST_PATRICK;
        if ("easter".equals(scene)) return EASTER;
        if ("thanksgiving".equals(scene)) return THANKSGIVING;
        return null;
    }
}
