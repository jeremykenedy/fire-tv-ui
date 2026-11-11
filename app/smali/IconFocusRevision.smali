.class public final Lcom/jeremykenedy/firetv/ui/IconFocusRevision;
.super Ljava/lang/Object;

.field private static final revision:LN/g0;

.method static constructor <clinit>()V
    .locals 2
    const/4 v0, 0x0
    invoke-static {v0}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
    move-result-object v0
    sget-object v1, LN/T;->l:LN/T;
    invoke-static {v0, v1}, LN/d;->M(Ljava/lang/Object;LN/I0;)LN/g0;
    move-result-object v0
    sput-object v0, Lcom/jeremykenedy/firetv/ui/IconFocusRevision;->revision:LN/g0;
    return-void
.end method

.method public static observe()V
    .locals 1
    sget-object v0, Lcom/jeremykenedy/firetv/ui/IconFocusRevision;->revision:LN/g0;
    invoke-virtual {v0}, LN/g0;->getValue()Ljava/lang/Object;
    return-void
.end method

.method public static changed()V
    .locals 2
    sget-object v0, Lcom/jeremykenedy/firetv/ui/IconFocusRevision;->revision:LN/g0;
    invoke-virtual {v0}, LN/g0;->getValue()Ljava/lang/Object;
    move-result-object v1
    check-cast v1, Ljava/lang/Integer;
    invoke-virtual {v1}, Ljava/lang/Integer;->intValue()I
    move-result v1
    add-int/lit8 v1, v1, 0x1
    invoke-static {v1}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
    move-result-object v1
    invoke-virtual {v0, v1}, LN/g0;->setValue(Ljava/lang/Object;)V
    return-void
.end method
