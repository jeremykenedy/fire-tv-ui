.class public final Lcom/jeremykenedy/firetv/ui/ChannelManageRow;
.super Ljava/lang/Object;

.method public static draw(LQ3/l5;LN/p;Ln4/a;)V
    .locals 13
    sget-object v0, Landroidx/compose/ui/platform/AndroidCompositionLocals_androidKt;->b:LN/S0;
    invoke-virtual {p1, v0}, LN/p;->k(LN/m0;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Landroid/content/Context;
    new-instance v5, Lcom/jeremykenedy/firetv/ui/ChannelCardsAction;
    invoke-direct {v5, v0, p0}, Lcom/jeremykenedy/firetv/ui/ChannelCardsAction;-><init>(Landroid/content/Context;LQ3/l5;)V
    const v0, 0x63b70411
    invoke-virtual {p1, v0}, LN/p;->V(I)V
    invoke-virtual {p1}, LN/p;->K()Ljava/lang/Object;
    move-result-object v4
    sget-object v0, LN/l;->a:LN/T;
    if-ne v4, v0, :manage_focus_ready
    new-instance v4, Le0/p;
    invoke-direct {v4}, Le0/p;-><init>()V
    invoke-virtual {p1, v4}, LN/p;->f0(Ljava/lang/Object;)V
    :manage_focus_ready
    check-cast v4, Le0/p;
    invoke-virtual {p1}, LN/p;->s()V
    const-string v0, "Manage cards (Right or Menu)"
    const/4 v1, 0x0
    const/4 v2, 0x0
    const/4 v3, 0x0
    move-object v6, p2
    const/4 v7, 0x0
    const/4 v8, 0x0
    const/4 v9, 0x0
    move-object v10, p1
    const/4 v11, 0x0
    const/16 v12, 0x41c
    invoke-static/range {v0 .. v12}, LA3/a;->A(Ljava/lang/String;ZZZLe0/p;Ln4/a;Ln4/a;Ln4/a;Ln4/a;Ln4/c;LN/p;II)V
    return-void
.end method
