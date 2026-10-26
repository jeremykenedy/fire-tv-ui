.class public final Lcom/jeremykenedy/firetv/ui/FeaturedGroupBridge;
.super Ljava/lang/Object;
.implements Ljava/lang/Runnable;

.field private static final revision:LN/g0;

.method static constructor <clinit>()V
    .locals 2
    const/4 v0, 0x0
    invoke-static {v0}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
    move-result-object v0
    sget-object v1, LN/T;->l:LN/T;
    invoke-static {v0, v1}, LN/d;->M(Ljava/lang/Object;LN/I0;)LN/g0;
    move-result-object v0
    sput-object v0, Lcom/jeremykenedy/firetv/ui/FeaturedGroupBridge;->revision:LN/g0;
    return-void
.end method

.method public constructor <init>()V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public run()V
    .locals 2
    sget-object v0, Lcom/jeremykenedy/firetv/ui/FeaturedGroupBridge;->revision:LN/g0;
    invoke-virtual {v0}, LN/g0;->getValue()Ljava/lang/Object;
    move-result-object v1
    check-cast v1, Ljava/lang/Number;
    invoke-virtual {v1}, Ljava/lang/Number;->intValue()I
    move-result v1
    add-int/lit8 v1, v1, 0x1
    invoke-static {v1}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
    move-result-object v1
    invoke-virtual {v0, v1}, LN/g0;->setValue(Ljava/lang/Object;)V
    return-void
.end method

.method public static start(Landroid/content/Context;)V
    .locals 1
    new-instance v0, Lcom/jeremykenedy/firetv/ui/FeaturedGroupBridge;
    invoke-direct {v0}, Lcom/jeremykenedy/firetv/ui/FeaturedGroupBridge;-><init>()V
    invoke-static {p0, v0}, Lcom/jeremykenedy/firetv/ui/FeaturedCardClock;->start(Landroid/content/Context;Ljava/lang/Runnable;)V
    return-void
.end method

.method public static current(LQ3/S8;)LQ3/S8;
    .locals 1
    sget-object v0, Lcom/jeremykenedy/firetv/ui/FeaturedGroupBridge;->revision:LN/g0;
    invoke-virtual {v0}, LN/g0;->getValue()Ljava/lang/Object;
    invoke-static {p0}, Lcom/jeremykenedy/firetv/ui/FeaturedGroups;->current(Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, LQ3/S8;
    return-object v0
.end method

.method public static group(Landroid/content/Context;Ljava/util/List;)Ljava/util/ArrayList;
    .locals 9
    invoke-static {}, Lcom/jeremykenedy/firetv/ui/FeaturedGroups;->prepare()V
    new-instance v0, Ljava/util/ArrayList;
    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
    new-instance v1, Ljava/util/ArrayList;
    invoke-direct {v1}, Ljava/util/ArrayList;-><init>()V
    invoke-interface {p1}, Ljava/util/List;->iterator()Ljava/util/Iterator;
    move-result-object v2
    :next
    invoke-interface {v2}, Ljava/util/Iterator;->hasNext()Z
    move-result v3
    if-eqz v3, :ready
    invoke-interface {v2}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v3
    check-cast v3, LQ3/S8;
    iget-object v4, v3, LQ3/S8;->c:Ljava/lang/String;
    iget-wide v5, v3, LQ3/S8;->h:J
    invoke-static {p0, v4, v5, v6}, Lcom/jeremykenedy/firetv/ui/FeaturedGroups;->feedKey(Landroid/content/Context;Ljava/lang/String;J)Ljava/lang/String;
    move-result-object v7
    invoke-virtual {v0, v7}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    iget-wide v5, v3, LQ3/S8;->h:J
    const-wide/16 v7, 0x0
    cmp-long v8, v5, v7
    iget-object v7, v3, LQ3/S8;->e:Landroid/net/Uri;
    const/4 v3, 0x0
    if-gez v8, :preview
    const/4 v3, 0x1
    :preview
    invoke-static {v4, v7, v3}, Lcom/jeremykenedy/firetv/ui/FeaturedGroups;->itemKey(Ljava/lang/String;Landroid/net/Uri;Z)Ljava/lang/String;
    move-result-object v3
    invoke-virtual {v1, v3}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    goto :next
    :ready
    invoke-static {p1, v0, v1}, Lcom/jeremykenedy/firetv/ui/FeaturedGroups;->group(Ljava/util/List;Ljava/util/List;Ljava/util/List;)Ljava/util/ArrayList;
    move-result-object v0
    return-object v0
.end method
