.class public final Lcom/jeremykenedy/firetv/ui/CardFocusAction;
.super Ljava/lang/Object;
.implements Ln4/a;

.field private final targets:Ljava/util/Map;
.field private final target:Ljava/lang/String;

.method public constructor <init>(Ljava/util/Map;Ljava/lang/String;)V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput-object p1, p0, Lcom/jeremykenedy/firetv/ui/CardFocusAction;->targets:Ljava/util/Map;
    iput-object p2, p0, Lcom/jeremykenedy/firetv/ui/CardFocusAction;->target:Ljava/lang/String;
    return-void
.end method

.method public a()Ljava/lang/Object;
    .locals 2
    iget-object v0, p0, Lcom/jeremykenedy/firetv/ui/CardFocusAction;->targets:Ljava/util/Map;
    iget-object v1, p0, Lcom/jeremykenedy/firetv/ui/CardFocusAction;->target:Ljava/lang/String;
    invoke-interface {v0, v1}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Le0/p;
    if-eqz v0, :done
    invoke-static {v0}, LQ3/M7;->T(Le0/p;)V
    :done
    sget-object v0, Lb4/n;->a:Lb4/n;
    return-object v0
.end method
