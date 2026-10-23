.class public final Lcom/jeremykenedy/firetv/ui/FeaturedCardSetupRow;
.super Ljava/lang/Object;

.method public static draw(Ljava/util/Map;LN/p;)V
    .locals 13
    const-string v0, "Cards_Sources"
    invoke-interface {p0, v0}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v4
    if-nez v4, :setup_focus_ready
    invoke-static {v0, p0}, LA/p;->c(Ljava/lang/String;Ljava/util/Map;)Le0/p;
    move-result-object v4
    :setup_focus_ready
    check-cast v4, Le0/p;
    sget-object v0, Landroidx/compose/ui/platform/AndroidCompositionLocals_androidKt;->b:LN/S0;
    invoke-virtual {p1, v0}, LN/p;->k(LN/m0;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Landroid/content/Context;
    new-instance v5, Lcom/jeremykenedy/firetv/ui/CardSourcesAction;
    invoke-direct {v5, v0}, Lcom/jeremykenedy/firetv/ui/CardSourcesAction;-><init>(Landroid/content/Context;)V
    new-instance v8, Lcom/jeremykenedy/firetv/ui/CardFocusAction;
    const-string v0, "Cards_PosterMode"
    invoke-direct {v8, p0, v0}, Lcom/jeremykenedy/firetv/ui/CardFocusAction;-><init>(Ljava/util/Map;Ljava/lang/String;)V
    const-string v0, "Featured card setup"
    const/4 v1, 0x0
    const/4 v2, 0x0
    const/4 v3, 0x0
    const/4 v6, 0x0
    const/4 v7, 0x0
    const/4 v9, 0x0
    move-object v10, p1
    const/4 v11, 0x0
    const/16 v12, 0x41c
    invoke-static/range {v0 .. v12}, LA3/a;->A(Ljava/lang/String;ZZZLe0/p;Ln4/a;Ln4/a;Ln4/a;Ln4/a;Ln4/c;LN/p;II)V
    return-void
.end method
