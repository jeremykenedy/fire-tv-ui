.class public final Lcom/jeremykenedy/firetv/ui/CardKeyAction;
.super Ljava/lang/Object;
.implements Ln4/c;

.field private final delegate:Ln4/c;
.field private final context:Landroid/content/Context;
.field private final card:LQ3/S8;

.method public constructor <init>(Ln4/c;Landroid/content/Context;LQ3/S8;)V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput-object p1, p0, Lcom/jeremykenedy/firetv/ui/CardKeyAction;->delegate:Ln4/c;
    iput-object p2, p0, Lcom/jeremykenedy/firetv/ui/CardKeyAction;->context:Landroid/content/Context;
    iput-object p3, p0, Lcom/jeremykenedy/firetv/ui/CardKeyAction;->card:LQ3/S8;
    return-void
.end method

.method public final b(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 8
    move-object v0, p1
    check-cast v0, Lq0/b;
    iget-object v2, v0, Lq0/b;->a:Landroid/view/KeyEvent;
    iget-object v1, p0, Lcom/jeremykenedy/firetv/ui/CardKeyAction;->context:Landroid/content/Context;
    iget-object v7, p0, Lcom/jeremykenedy/firetv/ui/CardKeyAction;->card:LQ3/S8;
    iget-object v3, v7, LQ3/S8;->c:Ljava/lang/String;
    iget-object v4, v7, LQ3/S8;->e:Landroid/net/Uri;
    iget-wide v5, v7, LQ3/S8;->h:J
    invoke-static/range {v1 .. v6}, Lcom/jeremykenedy/firetv/ui/FeaturedCardMenu;->handle(Landroid/content/Context;Landroid/view/KeyEvent;Ljava/lang/String;Landroid/net/Uri;J)Z
    move-result v0
    if-eqz v0, :normal_card_key
    sget-object v0, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
    return-object v0
    :normal_card_key
    iget-object v0, p0, Lcom/jeremykenedy/firetv/ui/CardKeyAction;->delegate:Ln4/c;
    invoke-interface {v0, p1}, Ln4/c;->b(Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v0
    return-object v0
.end method
