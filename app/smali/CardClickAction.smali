.class public final Lcom/jeremykenedy/firetv/ui/CardClickAction;
.super Ljava/lang/Object;
.implements Ln4/a;
.implements Ljava/lang/Runnable;

.field private final context:Landroid/content/Context;
.field private final card:LQ3/S8;
.field private final open:Ln4/a;

.method public constructor <init>(Landroid/content/Context;LQ3/S8;Ln4/a;)V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput-object p1, p0, Lcom/jeremykenedy/firetv/ui/CardClickAction;->context:Landroid/content/Context;
    iput-object p2, p0, Lcom/jeremykenedy/firetv/ui/CardClickAction;->card:LQ3/S8;
    iput-object p3, p0, Lcom/jeremykenedy/firetv/ui/CardClickAction;->open:Ln4/a;
    return-void
.end method

.method public a()Ljava/lang/Object;
    .locals 8
    iget-object v0, p0, Lcom/jeremykenedy/firetv/ui/CardClickAction;->context:Landroid/content/Context;
    iget-object v7, p0, Lcom/jeremykenedy/firetv/ui/CardClickAction;->card:LQ3/S8;
    iget-object v1, v7, LQ3/S8;->c:Ljava/lang/String;
    iget-object v2, v7, LQ3/S8;->e:Landroid/net/Uri;
    iget-wide v3, v7, LQ3/S8;->h:J
    iget-object v5, v7, LQ3/S8;->hiddenCardKey:Ljava/lang/String;
    iget-object v6, v7, LQ3/S8;->hiddenCardTitle:Ljava/lang/String;
    move-object v7, p0
    invoke-static/range {v0 .. v7}, Lcom/jeremykenedy/firetv/ui/FeaturedCardMenu;->show(Landroid/content/Context;Ljava/lang/String;Landroid/net/Uri;JLjava/lang/String;Ljava/lang/String;Ljava/lang/Runnable;)V
    sget-object v0, Lb4/n;->a:Lb4/n;
    return-object v0
.end method

.method public run()V
    .locals 1
    iget-object v0, p0, Lcom/jeremykenedy/firetv/ui/CardClickAction;->open:Ln4/a;
    invoke-interface {v0}, Ln4/a;->a()Ljava/lang/Object;
    return-void
.end method
