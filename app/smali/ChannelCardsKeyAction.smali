.class public final Lcom/jeremykenedy/firetv/ui/ChannelCardsKeyAction;
.super Ljava/lang/Object;
.implements Ln4/c;

.field private final delegate:Ln4/c;
.field private final open:Ln4/a;

.method public constructor <init>(Ln4/c;Ln4/a;)V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput-object p1, p0, Lcom/jeremykenedy/firetv/ui/ChannelCardsKeyAction;->delegate:Ln4/c;
    iput-object p2, p0, Lcom/jeremykenedy/firetv/ui/ChannelCardsKeyAction;->open:Ln4/a;
    return-void
.end method

.method public static wrap(Ln4/c;LQ3/l5;LN/p;)Ln4/c;
    .locals 3
    sget-object v0, Landroidx/compose/ui/platform/AndroidCompositionLocals_androidKt;->b:LN/S0;
    invoke-virtual {p2, v0}, LN/p;->k(LN/m0;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Landroid/content/Context;
    new-instance v1, Lcom/jeremykenedy/firetv/ui/ChannelCardsAction;
    invoke-direct {v1, v0, p1}, Lcom/jeremykenedy/firetv/ui/ChannelCardsAction;-><init>(Landroid/content/Context;LQ3/l5;)V
    new-instance v2, Lcom/jeremykenedy/firetv/ui/ChannelCardsKeyAction;
    invoke-direct {v2, p0, v1}, Lcom/jeremykenedy/firetv/ui/ChannelCardsKeyAction;-><init>(Ln4/c;Ln4/a;)V
    return-object v2
.end method

.method public final b(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 3
    move-object v0, p1
    check-cast v0, Lq0/b;
    iget-object v0, v0, Lq0/b;->a:Landroid/view/KeyEvent;
    invoke-virtual {v0}, Landroid/view/KeyEvent;->getKeyCode()I
    move-result v1
    const/16 v2, 0x52
    if-eq v1, v2, :manage_key
    const/16 v2, 0x16
    if-ne v1, v2, :delegate
    :manage_key
    invoke-virtual {v0}, Landroid/view/KeyEvent;->getAction()I
    move-result v1
    const/4 v2, 0x1
    if-ne v1, v2, :consumed
    invoke-virtual {v0}, Landroid/view/KeyEvent;->isCanceled()Z
    move-result v0
    if-nez v0, :consumed
    iget-object v0, p0, Lcom/jeremykenedy/firetv/ui/ChannelCardsKeyAction;->open:Ln4/a;
    invoke-interface {v0}, Ln4/a;->a()Ljava/lang/Object;
    :consumed
    sget-object v0, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
    return-object v0
    :delegate
    iget-object v0, p0, Lcom/jeremykenedy/firetv/ui/ChannelCardsKeyAction;->delegate:Ln4/c;
    invoke-interface {v0, p1}, Ln4/c;->b(Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v0
    return-object v0
.end method
