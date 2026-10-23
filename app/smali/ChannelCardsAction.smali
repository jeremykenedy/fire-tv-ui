.class public final Lcom/jeremykenedy/firetv/ui/ChannelCardsAction;
.super Ljava/lang/Object;
.implements Ln4/a;

.field private final context:Landroid/content/Context;
.field private final channel:LQ3/l5;

.method public constructor <init>(Landroid/content/Context;LQ3/l5;)V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput-object p1, p0, Lcom/jeremykenedy/firetv/ui/ChannelCardsAction;->context:Landroid/content/Context;
    iput-object p2, p0, Lcom/jeremykenedy/firetv/ui/ChannelCardsAction;->channel:LQ3/l5;
    return-void
.end method

.method public a()Ljava/lang/Object;
    .locals 4
    iget-object v0, p0, Lcom/jeremykenedy/firetv/ui/ChannelCardsAction;->context:Landroid/content/Context;
    iget-object v3, p0, Lcom/jeremykenedy/firetv/ui/ChannelCardsAction;->channel:LQ3/l5;
    iget-object v1, v3, LQ3/l5;->a:Ljava/lang/String;
    iget-object v2, v3, LQ3/l5;->b:Ljava/lang/String;
    iget-object v3, v3, LQ3/l5;->c:Ljava/util/List;
    invoke-static {v0, v1, v2, v3}, Lcom/jeremykenedy/firetv/ui/ChannelCardsActivity;->open(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;Ljava/util/List;)V
    sget-object v0, Lb4/n;->a:Lb4/n;
    return-object v0
.end method
