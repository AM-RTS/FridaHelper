.class public Lcom/example/app/LoginManager;
.super Ljava/lang/Object;
.source "LoginManager.java"


# instance fields
.field private token:Ljava/lang/String;


# direct methods
.method public constructor <init>()V
    .registers 1

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method


# virtual methods
.method public authenticate(Ljava/lang/String;Ljava/lang/String;)Z
    .registers 4
    .param p1, "username"
    .param p2, "password"

    const/4 v0, 0x1
    return v0
.end method

.method public logout()V
    .registers 1

    return-void
.end method

.method public static native getStatus()Z
.end method

.method private synthetic access$000()Ljava/lang/String;
    .registers 2

    iget-object v0, p0, Lcom/example/app/LoginManager;->token:Ljava/lang/String;
    return-object v0
.end method
