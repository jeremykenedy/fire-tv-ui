.class public final Lcom/jeremykenedy/firetv/ui/ChannelCardsFocus;
.super Ljava/lang/Object;
.implements Ljava/lang/Runnable;

.field private static target:Ljava/lang/ref/WeakReference;

.method private constructor <init>()V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public static remember(Le0/p;)V
    .locals 1
    new-instance v0, Ljava/lang/ref/WeakReference;
    invoke-direct {v0, p0}, Ljava/lang/ref/WeakReference;-><init>(Ljava/lang/Object;)V
    sput-object v0, Lcom/jeremykenedy/firetv/ui/ChannelCardsFocus;->target:Ljava/lang/ref/WeakReference;
    return-void
.end method

.method public static restore()V
    .locals 4
    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;
    move-result-object v0
    new-instance v1, Landroid/os/Handler;
    invoke-direct {v1, v0}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V
    new-instance v0, Lcom/jeremykenedy/firetv/ui/ChannelCardsFocus;
    invoke-direct {v0}, Lcom/jeremykenedy/firetv/ui/ChannelCardsFocus;-><init>()V
    # Let Compose remove the modal focus target before requesting its entry row.
    const-wide/16 v2, 0x50
    invoke-virtual {v1, v0, v2, v3}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z
    return-void
.end method

.method public run()V
    .locals 1
    sget-object v0, Lcom/jeremykenedy/firetv/ui/ChannelCardsFocus;->target:Ljava/lang/ref/WeakReference;
    if-eqz v0, :done
    invoke-virtual {v0}, Ljava/lang/ref/WeakReference;->get()Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Le0/p;
    if-eqz v0, :done
    :try_start
    invoke-static {v0}, LQ3/M7;->T(Le0/p;)V
    :try_end
    .catch Ljava/lang/IllegalStateException; {:try_start .. :try_end} :unavailable
    :done
    return-void
    :unavailable
    move-exception v0
    return-void
.end method
