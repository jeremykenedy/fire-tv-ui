.class public final Lcom/jeremykenedy/firetv/ui/BackupAction;
.super Ljava/lang/Object;
.implements Ln4/a;
.implements Ln4/c;

.field private final context:Landroid/content/Context;

.method public constructor <init>(Landroid/content/Context;)V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput-object p1, p0, Lcom/jeremykenedy/firetv/ui/BackupAction;->context:Landroid/content/Context;
    return-void
.end method

.method public a()Ljava/lang/Object;
    .locals 1
    iget-object v0, p0, Lcom/jeremykenedy/firetv/ui/BackupAction;->context:Landroid/content/Context;
    invoke-static {v0}, Lcom/jeremykenedy/firetv/ui/BackupActivity;->open(Landroid/content/Context;)V
    sget-object v0, Lb4/n;->a:Lb4/n;
    return-object v0
.end method

.method public b(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 1
    sget-object v0, Lb4/n;->a:Lb4/n;
    return-object v0
.end method
