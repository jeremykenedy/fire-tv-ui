.class public final Lcom/jeremykenedy/firetv/ui/AdvancedAction;
.super Ljava/lang/Object;
.implements Ln4/a;

.field private final context:Landroid/content/Context;

.method public constructor <init>(Landroid/content/Context;)V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput-object p1, p0, Lcom/jeremykenedy/firetv/ui/AdvancedAction;->context:Landroid/content/Context;
    return-void
.end method

.method public a()Ljava/lang/Object;
    .locals 3
    iget-object v0, p0, Lcom/jeremykenedy/firetv/ui/AdvancedAction;->context:Landroid/content/Context;
    const-class v1, Lcom/jeremykenedy/firetv/ui/AdvancedActivity;
    new-instance v2, Landroid/content/Intent;
    invoke-direct {v2, v0, v1}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V
    invoke-virtual {v0, v2}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V
    sget-object v0, Lb4/n;->a:Lb4/n;
    return-object v0
.end method
