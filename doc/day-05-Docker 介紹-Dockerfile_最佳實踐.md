# Day-05-Docker 介紹-Dockerfile 最佳實踐

# 前言
通常 Software engineer 是最清楚自己的應用軟體依賴哪些函式庫的成員，所以大多 Dockerfile 也是 Software engineer 負責撰寫的，所以今天會分享幾個 Docker 官方建議撰寫 Dockerfile 的最佳實踐原則。

# 只安裝必要的函式庫
避免安裝不必要的函式庫，當你安裝越多，你的 Container 出現弱點的機會就會越高，後續維護起來會更困難。

已昨天的 Dockerfile 為例子，能將 base Image 改為 `openjdk:17-alpine` 或 `arm64v8/eclipse-temurin:17.0.8.1_1-jre-ubi9-minimal`
```
FROM openjdk:17-alpine
```
因為 openjdk:17-alpine 使用 Alpine Linux 作為基礎，它是一個輕量級的 Linux 發行版，通常比標準的 Linux 發行版中的函式庫更精簡，且容量更小。

# 避免使用 Root 運行
大多 base Image 都會使用 root user 運行，因 root 有著最大的權限，能方便使用者安裝函式庫或進行配置。
但若是 Docker Engine 或 Linux kernel 出現漏洞時，可能會導致 Host 主機被攻擊。

建議自己建立一個 non-root user，並使用該 user 運行服務。
```
FROM arm64v8/eclipse-temurin:17.0.8.1_1-jre-ubi9-minimal

WORKDIR /app

# 創建一個非 root 用戶
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# 切換到用戶 appuser
USER appuser

COPY target/demo.jar /app/application.jar

# 定義容器啟動命令，運行 JAR 文件
CMD ["java", "-jar", "application.jar"]

# 暴露 8080 端口
EXPOSE 8080
```

# 先執行不常改變的指令，後執行常變動的指令 與 `COPY`, `ADD` 指令
Dockerfile 中指令的順序會大大影響建構的效率，因為大部分的指令都會獨立生成一個 Image layer
![image-layer](https://docs.docker.com/build/guide/images/layers.png)

在執行 `docker build` 時，docker 會嘗試於本地尋找有沒有可重複利用的 layer，並使用該 layer 開始進行建構，節省重複建構的時間。

已下圖為例，若執行 `COPY` 的檔案內容有任何變化，則會從 `WORKDIR /src` 開始建構 Image，每次建構時，都可能浪費時間在執行 `RUN go mod download` 的網路 IO上，拖慢了建構時間。
![bad-dockerfile-order](https://docs.docker.com/build/guide/images/cache-bust.png)

若將 Dockerfile 調整成下圖，只要 go.mod 的檔案沒有任何改變，都能從 `RUN go mod download` layer 開始往後建構 Image，降低建構等待網路 IO 的時間。
![good-dockerfile-order](https://docs.docker.com/build/guide/images/reordered-layers.png)

# 能合併的指令盡量合併再一起
上面提到了每一個 Dockerfile 指令都會生成一個 layer，若 Dockerfile 指令越多，則 layer 層數越高，會增加 Container Image 的大小，所以盡量再可讀性尚可的情況下，將指令合併起來


不好的範例
```
FROM ubuntu:20.04

RUN apt-get update
RUN apt-get install -y package1 package2 package3
RUN apt-get clean
RUN rm -rf /var/lib/apt/lists/*

COPY . /app

WORKDIR /app

CMD ["./start.sh"]

```

建議的範例，透過 `\` 將 apt-get 相關操作合併再一起，減少 Image layer
```
FROM ubuntu:20.04

RUN apt-get update && \
    apt-get install -y package1 package2 package3 && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

COPY . /app

WORKDIR /app

CMD ["./start.sh"]
```

# 使用 .dockerignore
若你的 project 中，有敏感資訊(如金鑰) 或 不希望包入 container 的檔案，建議使用 .dockerignore 來顯性的排除他，避免使用 `COPY` 指令時，不小心被放入 container。

使用方式類似 .gitignore
```
# 排除不必要的配置文件
config.ini
settings.conf

# 排除版本控制文件和目錄
.git/
.gitignore

# 排除敏感信息
secrets/
passwords/
```

# 總結
今天介紹了一些撰寫 Dockerfile 的最佳實踐，其中包含了安全性、性能與可維護性等議題，希望讀者能建構更安全、高效與容易維護的 Image。