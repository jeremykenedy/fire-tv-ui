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
    private static final float[] PASSOVER = {.095f,.344f,.137f,.348f};
    private static final float[] VESAK = {.121f,.235f,.050f,.440f,.406f,.782f,.375f,.923f,.904f,.770f};
    private static final float[] JUNETEENTH = {.533f,.625f,.546f,.658f,.603f,.646f};
    private static final float[] RAMADAN = {.122f,.261f,.184f,.746f,.835f,.677f,.895f,.599f};
    private static final float[] NEW_YEAR = {.136f,.337f,.914f,.151f,.842f,.302f,.941f,.357f};
    private static final float[] EPIPHANY = {.864f,.343f};
    private static final float[] EID_ADHA = {.058f,.735f,.387f,.611f,.542f,.597f,.703f,.677f,.905f,.300f};
    private static final float[] ROSH_HASHANAH = {.095f,.315f,.150f,.430f};
    private static final float[] YOM_KIPPUR = {.361f,.680f,.131f,.258f,.214f,.340f};
    private static final float[] SUKKOT = {.139f,.239f,.290f,.457f,.896f,.392f};
    private static final float[] PURIM = {.968f,.414f,.828f,.487f,.749f,.514f};
    private static final float[] HOLI = {.929f,.053f};
    private static final float[] NAVRATRI = {.502f,.670f,.182f,.430f,.382f,.611f,.632f,.611f,.769f,.611f};
    private static final float[] JANMASHTAMI = {.048f,.793f,.704f,.699f,.818f,.642f,.982f,.429f};
    private static final float[] GANESH_CHATURTHI = {.058f,.596f,.405f,.592f};
    private static final float[] VAISAKHI = {.846f,.595f,.813f,.592f,.899f,.596f};
    private static final float[] GURPURAB = {.556f,.972f,.592f,.951f,.633f,.925f,.657f,.906f,.685f,.884f};
    private static final float[] LOSAR = {.067f,.670f,.742f,.712f,.873f,.781f};
    private static final float[] OBON = {.869f,.827f,.690f,.713f,.474f,.654f,.559f,.638f};
    private static final float[] MAHAVIR_JAYANTI = {.067f,.871f,.114f,.922f,.343f,.824f,.331f,.901f};
    private static final float[] PARYUSHAN = {.812f,.675f};
    private static final float[] NAW_RUZ = {.481f,.692f,.585f,.691f,.745f,.702f};
    private static final float[] RIDVAN = {.147f,.248f,.195f,.535f,.369f,.680f,.553f,.750f};
    private static final float[] NOWRUZ = {.700f,.379f,.891f,.313f};
    private static final float[] MEHRGAN = {.666f,.514f,.702f,.421f,.246f,.736f,.868f,.666f};
    private static final float[] KWANZAA = {.355f,.405f,.403f,.405f,.451f,.405f,.502f,.382f,.554f,.405f,.602f,.405f,.649f,.405f};
    private static final float[] INDEPENDENCE = {.129f,.349f,.194f,.438f,.925f,.333f,.849f,.431f};
    private static final float[] SPRING = {.266f,.546f};
    private static final float[] SUMMER = {.794f,.582f,.393f,.754f,.629f,.831f};
    private static final float[] AUTUMN = {.747f,.477f};
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
        if ("passover".equals(scene)) return PASSOVER;
        if ("vesak".equals(scene)) return VESAK;
        if ("juneteenth".equals(scene)) return JUNETEENTH;
        if ("ramadan".equals(scene)) return RAMADAN;
        if ("new_year".equals(scene)) return NEW_YEAR;
        if ("epiphany".equals(scene)) return EPIPHANY;
        if ("eid_adha".equals(scene)) return EID_ADHA;
        if ("rosh_hashanah".equals(scene)) return ROSH_HASHANAH;
        if ("yom_kippur".equals(scene)) return YOM_KIPPUR;
        if ("sukkot".equals(scene)) return SUKKOT;
        if ("purim".equals(scene)) return PURIM;
        if ("holi".equals(scene)) return HOLI;
        if ("navratri".equals(scene)) return NAVRATRI;
        if ("janmashtami".equals(scene)) return JANMASHTAMI;
        if ("ganesh_chaturthi".equals(scene)) return GANESH_CHATURTHI;
        if ("vaisakhi".equals(scene)) return VAISAKHI;
        if ("gurpurab".equals(scene)) return GURPURAB;
        if ("losar".equals(scene)) return LOSAR;
        if ("obon".equals(scene)) return OBON;
        if ("mahavir_jayanti".equals(scene)) return MAHAVIR_JAYANTI;
        if ("paryushan".equals(scene)) return PARYUSHAN;
        if ("naw_ruz".equals(scene)) return NAW_RUZ;
        if ("ridvan".equals(scene)) return RIDVAN;
        if ("nowruz".equals(scene)) return NOWRUZ;
        if ("mehrgan".equals(scene)) return MEHRGAN;
        if ("kwanzaa".equals(scene)) return KWANZAA;
        if ("independence".equals(scene)) return INDEPENDENCE;
        if ("spring".equals(scene)) return SPRING;
        if ("summer".equals(scene)) return SUMMER;
        if ("autumn".equals(scene)) return AUTUMN;
        return null;
    }
}
