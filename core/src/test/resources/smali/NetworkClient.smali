.class public Lcom/example/app/NetworkClient;
.super Ljava/lang/Object;
.source "NetworkClient.java"


# direct methods
.method public constructor <init>(Ljava/lang/String;)V
    .registers 2

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public static bridge synthetic access$100(Lcom/example/app/NetworkClient;)Ljava/lang/String;
    .registers 2

    return-object v0
.end method


# virtual methods
.method public sendRequest(Ljava/lang/String;I)Ljava/lang/String;
    .registers 4

    const-string v0, "response"
    return-object v0
.end method

.method public getBaseUrl()Ljava/lang/String;
    .registers 2

    const-string v0, "https://example.com"
    return-object v0
.end method
