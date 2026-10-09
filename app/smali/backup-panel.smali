.method public static final i(Landroid/content/Context;Landroid/content/SharedPreferences;Ln4/a;Ljava/util/Map;LN/p;I)V
    .locals 8
    const v0, 0x17b76af3
    invoke-virtual {p4, v0}, LN/p;->V(I)V
    new-instance v5, Lcom/jeremykenedy/firetv/ui/BackupAction;
    invoke-direct {v5, p0}, Lcom/jeremykenedy/firetv/ui/BackupAction;-><init>(Landroid/content/Context;)V
    const-string v0, "Backup/Restore_0"
    invoke-interface {p3, v0}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v3
    if-nez v3, :focus_ready
    new-instance v3, Le0/p;
    invoke-direct {v3}, Le0/p;-><init>()V
    invoke-interface {p3, v0, v3}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
    :focus_ready
    check-cast v3, Le0/p;
    const-string v0, "Manage TV backups"
    const/4 v1, 0x0
    const/4 v2, 0x0
    move-object v4, v5
    move-object v6, p4
    const/4 v7, 0x0
    invoke-static/range {v0 .. v7}, LQ3/M7;->I(Ljava/lang/String;IZLe0/p;Ln4/c;Ln4/a;LN/p;I)V
    const/4 v0, 0x0
    invoke-virtual {p4, v0}, LN/p;->p(Z)V
    return-void
.end method
