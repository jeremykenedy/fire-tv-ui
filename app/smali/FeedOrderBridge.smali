.class public final Lcom/jeremykenedy/firetv/ui/FeedOrderBridge;
.super Ljava/lang/Object;

.method public static order(Landroid/content/Context;Ljava/util/List;)Ljava/util/ArrayList;
    .locals 7
    new-instance v0, Ljava/util/ArrayList;
    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
    invoke-interface {p1}, Ljava/util/List;->iterator()Ljava/util/Iterator;
    move-result-object v1
    :next_feed
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z
    move-result v2
    if-eqz v2, :feeds_ready
    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v2
    check-cast v2, LQ3/S8;
    iget-object v3, v2, LQ3/S8;->c:Ljava/lang/String;
    iget-wide v4, v2, LQ3/S8;->h:J
    invoke-static {p0, v3, v4, v5}, Lcom/jeremykenedy/firetv/ui/FeedOrder;->key(Landroid/content/Context;Ljava/lang/String;J)Ljava/lang/String;
    move-result-object v6
    invoke-interface {v0, v6}, Ljava/util/List;->add(Ljava/lang/Object;)Z
    goto :next_feed
    :feeds_ready
    invoke-static {p0, p1, v0}, Lcom/jeremykenedy/firetv/ui/FeedOrder;->order(Landroid/content/Context;Ljava/util/List;Ljava/util/List;)Ljava/util/ArrayList;
    move-result-object v0
    return-object v0
.end method
