.class public final Lcom/jeremykenedy/firetv/ui/SavedSetupsRow;
.super Ljava/lang/Object;

.method public static draw(Le0/p;Ln4/a;Ln4/a;Ln4/a;LN/p;Ljava/util/Map;)V
    .locals 13
    move-object/from16 v10, p4
    sget-object v0, Landroidx/compose/ui/platform/AndroidCompositionLocals_androidKt;->b:LN/S0;
    invoke-virtual {v10, v0}, LN/p;->k(LN/m0;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Landroid/content/Context;
    new-instance v5, Lcom/jeremykenedy/firetv/ui/MenuBackgroundAction;
    invoke-direct {v5, v0}, Lcom/jeremykenedy/firetv/ui/MenuBackgroundAction;-><init>(Landroid/content/Context;)V
    move-object/from16 v9, p5
    new-instance v8, Lcom/jeremykenedy/firetv/ui/CardFocusAction;
    const-string v0, "Theme Mode_SavedSetups"
    invoke-direct {v8, v9, v0}, Lcom/jeremykenedy/firetv/ui/CardFocusAction;-><init>(Ljava/util/Map;Ljava/lang/String;)V
    const-string v0, "Backgrounds and overlays"
    const/4 v1, 0x0
    const/4 v2, 0x0
    const/4 v3, 0x0
    move-object v4, p0
    move-object v6, p1
    move-object v7, p2
    const/4 v9, 0x0
    const/4 v11, 0x0
    const/16 v12, 0x41c
    invoke-static/range {v0 .. v12}, LA3/a;->A(Ljava/lang/String;ZZZLe0/p;Ln4/a;Ln4/a;Ln4/a;Ln4/a;Ln4/c;LN/p;II)V

    move-object/from16 v9, p5
    const-string v0, "Theme Mode_SavedSetups"
    invoke-interface {v9, v0}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v4
    if-nez v4, :saved_focus_ready
    invoke-static {v0, v9}, LA/p;->c(Ljava/lang/String;Ljava/util/Map;)Le0/p;
    move-result-object v4
    :saved_focus_ready
    check-cast v4, Le0/p;
    move-object/from16 v10, p4
    sget-object v0, Landroidx/compose/ui/platform/AndroidCompositionLocals_androidKt;->b:LN/S0;
    invoke-virtual {v10, v0}, LN/p;->k(LN/m0;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Landroid/content/Context;
    new-instance v5, Lcom/jeremykenedy/firetv/ui/SavedSetupsAction;
    invoke-direct {v5, v0}, Lcom/jeremykenedy/firetv/ui/SavedSetupsAction;-><init>(Landroid/content/Context;)V
    new-instance v7, Lcom/jeremykenedy/firetv/ui/CardFocusAction;
    const-string v0, "Theme Mode_Screensaver"
    invoke-direct {v7, v9, v0}, Lcom/jeremykenedy/firetv/ui/CardFocusAction;-><init>(Ljava/util/Map;Ljava/lang/String;)V
    const-string v0, "Saved setups"
    const/4 v1, 0x0
    const/4 v2, 0x0
    const/4 v3, 0x0
    move-object v6, p1
    move-object/from16 v8, p3
    const/4 v9, 0x0
    const/4 v11, 0x0
    const/16 v12, 0x41c
    invoke-static/range {v0 .. v12}, LA3/a;->A(Ljava/lang/String;ZZZLe0/p;Ln4/a;Ln4/a;Ln4/a;Ln4/a;Ln4/c;LN/p;II)V
    return-void
.end method
