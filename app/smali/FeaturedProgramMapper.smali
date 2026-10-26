.class public final Lcom/jeremykenedy/firetv/ui/FeaturedProgramMapper;
.super Ljava/lang/Object;

.method public static map(Ljava/util/List;Ljava/util/List;)Ljava/util/ArrayList;
    .locals 8
    new-instance v0, Ljava/util/ArrayList;
    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
    invoke-interface {p1}, Ljava/util/List;->iterator()Ljava/util/Iterator;
    move-result-object v1
    :watch_next
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z
    move-result v2
    if-eqz v2, :channels
    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v2
    check-cast v2, LQ3/k8;
    const/4 v3, 0x0
    invoke-static {v2, v3}, Lcom/jeremykenedy/firetv/ui/FeaturedProgramMapper;->card(LQ3/k8;LQ3/y;)LQ3/S8;
    move-result-object v2
    invoke-virtual {v0, v2}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    goto :watch_next
    :channels
    invoke-interface {p0}, Ljava/util/List;->iterator()Ljava/util/Iterator;
    move-result-object v1
    :next_channel
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z
    move-result v2
    if-eqz v2, :done
    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v2
    check-cast v2, LQ3/y;
    iget-object v3, v2, LQ3/y;->d:Ljava/util/List;
    invoke-interface {v3}, Ljava/util/List;->iterator()Ljava/util/Iterator;
    move-result-object v3
    :next_program
    invoke-interface {v3}, Ljava/util/Iterator;->hasNext()Z
    move-result v4
    if-eqz v4, :next_channel
    invoke-interface {v3}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v4
    check-cast v4, LQ3/k8;
    invoke-static {v4, v2}, Lcom/jeremykenedy/firetv/ui/FeaturedProgramMapper;->card(LQ3/k8;LQ3/y;)LQ3/S8;
    move-result-object v4
    invoke-virtual {v0, v4}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    goto :next_program
    :done
    return-object v0
.end method

.method private static card(LQ3/k8;LQ3/y;)LQ3/S8;
    .locals 14
    new-instance v0, LQ3/S8;
    iget-object v1, p0, LQ3/k8;->a:Ljava/lang/String;
    const-string v2, "Watch Next"
    iget-object v3, p0, LQ3/k8;->e:Ljava/lang/String;
    iget-object v4, p0, LQ3/k8;->c:Landroid/net/Uri;
    iget-object v5, p0, LQ3/k8;->d:Landroid/net/Uri;
    const/4 v6, 0x1
    iget v7, p0, LQ3/k8;->f:F
    const-wide/16 v8, -0x1
    iget v10, p0, LQ3/k8;->g:I
    iget v11, p0, LQ3/k8;->h:I
    const-string v12, "Watch Next"
    if-eqz p1, :ready
    const-string v13, "||"
    invoke-virtual {v13, v1}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;
    move-result-object v1
    iget-object v2, p1, LQ3/y;->b:Ljava/lang/String;
    iget-wide v8, p1, LQ3/y;->a:J
    move-object v12, v2
    :ready
    invoke-direct/range {v0 .. v12}, LQ3/S8;-><init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Landroid/net/Uri;Landroid/net/Uri;ZFJIILjava/lang/String;)V
    return-object v0
.end method

.method public static labels(Landroid/content/Context;Ljava/util/List;)Ljava/util/ArrayList;
    .locals 4
    new-instance v0, Ljava/util/ArrayList;
    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
    invoke-interface {p1}, Ljava/util/List;->iterator()Ljava/util/Iterator;
    move-result-object v1
    :next_label
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z
    move-result v2
    if-eqz v2, :labels_ready
    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v2
    check-cast v2, LQ3/S8;
    invoke-static {p0, v2}, Lcom/jeremykenedy/firetv/ui/FeaturedProgramMapper;->label(Landroid/content/Context;LQ3/S8;)LQ3/S8;
    move-result-object v2
    invoke-virtual {v0, v2}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    goto :next_label
    :labels_ready
    return-object v0
.end method

.method private static label(Landroid/content/Context;LQ3/S8;)LQ3/S8;
    .locals 13
    iget-wide v8, p1, LQ3/S8;->h:J
    const-wide/16 v0, 0x0
    cmp-long v0, v8, v0
    if-ltz v0, :watch_label
    return-object p1
    :watch_label
    iget-object v1, p1, LQ3/S8;->a:Ljava/lang/String;
    iget-object v3, p1, LQ3/S8;->c:Ljava/lang/String;
    invoke-static {p0, v3}, Lcom/jeremykenedy/firetv/ui/FeedOrder;->appName(Landroid/content/Context;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v12
    invoke-static {v12}, Lcom/jeremykenedy/firetv/ui/FeaturedCardMenu;->label(Ljava/lang/String;)Ljava/lang/String;
    move-result-object v2
    iget-object v4, p1, LQ3/S8;->d:Landroid/net/Uri;
    iget-object v5, p1, LQ3/S8;->e:Landroid/net/Uri;
    iget-boolean v6, p1, LQ3/S8;->f:Z
    iget v7, p1, LQ3/S8;->g:F
    iget v10, p1, LQ3/S8;->i:I
    iget v11, p1, LQ3/S8;->j:I
    new-instance v0, LQ3/S8;
    invoke-direct/range {v0 .. v12}, LQ3/S8;-><init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Landroid/net/Uri;Landroid/net/Uri;ZFJIILjava/lang/String;)V
    return-object v0
.end method
