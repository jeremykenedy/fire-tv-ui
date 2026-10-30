.class public final Lcom/jeremykenedy/firetv/ui/MenuOverlayLayer;
.super Ljava/lang/Object;
.implements Ln4/c;

.method public constructor <init>()V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public b(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 2
    check-cast p1, Landroid/content/Context;
    new-instance v0, Lcom/jeremykenedy/firetv/ui/MenuBackgroundView;
    const/4 v1, 0x1
    invoke-direct {v0, p1, v1, v1}, Lcom/jeremykenedy/firetv/ui/MenuBackgroundView;-><init>(Landroid/content/Context;ZZ)V
    return-object v0
.end method

.method public static draw(Landroid/content/Context;LN/p;)Z
    .locals 5
    invoke-static {p0}, Lcom/jeremykenedy/firetv/ui/MenuBackgroundView;->overlayEnabled(Landroid/content/Context;)Z
    move-result v0
    if-eqz v0, :off
    new-instance v0, Lcom/jeremykenedy/firetv/ui/MenuOverlayLayer;
    invoke-direct {v0}, Lcom/jeremykenedy/firetv/ui/MenuOverlayLayer;-><init>()V
    sget-object v1, LZ/l;->a:LZ/l;
    invoke-static {v1}, Landroidx/compose/foundation/layout/d;->d(LZ/o;)LZ/o;
    move-result-object v1
    const/4 v2, 0x0
    move-object v3, p1
    const/4 v4, 0x0
    invoke-static {v0, v1, v2, v3, v4}, Landroidx/compose/ui/viewinterop/a;->b(Ln4/c;LZ/o;LW0/a;LN/p;I)V
    const/4 v0, 0x1
    return v0
    :off
    const/4 v0, 0x0
    return v0
.end method
