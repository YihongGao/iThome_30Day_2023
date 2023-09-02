# Day-04-Docker 介紹-建立自己的 Containar Image

# 前言
昨天我們介紹了怎麼使用 Docker Client 並使用他人建立的 Image 再本地部署服務，而今天我們會將一個 Java 的 API 服務打包成 Container Image，並推送到 Container Registry。

# 前置準備
1. 將 Java 原始碼 cloen 到本地端
    ```
    git clone https://github.com/YihongGao/iThome_30Day_2023.git
    ```

2. 切換到 demo 專案下
    ```
    cd projects/demo
    ```
3. 將 Java 原始碼編譯並打包成 JAR file
> JAR file 是一種 Java 部署和分發的常用文件格式，通常就是一個服務或函式庫，再這裡 JAR file 的內容就是要打包近 Container Image 的 API 服務。
- 選項一：使用 maven 指令打包
    ```
    .mvnw clean package
    ```
    能在 target 目錄中看到編譯好的 JAR file(demo.jar)，這就是我們稍後要包進 Container 中的 jar。
    ```
    ls target/demo.jar
    ```
- 選項二：直接 copy 我預先編譯好的 jar 到 target 目錄 (本地環境沒有安裝 java 的人，能直接使用此方式)
    ```
    cp ./artifact/demo.jar ./target/demo.jar
    ```
   
# 建構 Container Image
當要打包的服務(JAR)準備好之後，我們需要一個叫 Dockerfile 的檔案，來定義建構 Container Image 的過程。

這邊直接看我預先寫好的 Dockerfile 內容，檔案就在 projects/demo 目錄下

```
# 使用 OpenJDK 17 作為基本映像
FROM openjdk:17

# 在容器中創建一個目錄以存放應用程序 JAR 文件
WORKDIR /app

# 複製本地的 JAR 文件到容器中
COPY target/demo.jar /app/application.jar

# 暴露 8080 端口
EXPOSE 8080

# 定義容器啟動命令，運行 JAR 文件
CMD ["java", "-jar", "application.jar"]
```

我們依序看 Dockerfile 中每一行做了些什麼事情
- `FROM openjdk:17`    
    代表我選擇使用 `openjdk:17` 這個 Image 來當作我的 Base Image，因為 JAR file 的運行環境需要有 JRE 這函式庫，而 `openjdk:17` 符合我的服務運行的需求。    
    簡單來說 Base Image 你可以依據你的服務依賴的函式庫來選擇 Base Image，如果你的服務是使用 node.js，你可能會選擇 `node:lts` 當作 Base Image。
- `WORKDIR /app`    
    使用 `/app` 目錄作為當前的工作目錄，確保後續的指令都在 `/app` 下進行，提升可讀性與維護性，`/app` 能替換成你想要的目錄名稱。
- `COPY target/demo.jar /app/application.jar`    
    將我們先前打包好的 JAR file 複製到 Container 內，所以執行到這步驟時，Container 的內容就已經包含了 Base Image + JAR file。

    因為我們使用了 `WORKDIR` 指定當前目錄為 `/app`，且透過 `COPY` 將本地 JAR file 複製到 `/app` 並使用新檔案名稱 `application.jar`，所以容器中至少有以下目錄結構
    ```
    /
    └── app <-- [當前目錄] 
        └── application.jar
    ```
- `EXPOSE 8080`    
    定義此容器可以將 8080 PORT 映射到主機上，後續 docker run 時才能用-p 參數進行轉發
- `CMD ["java", "-jar", "application.jar"]`   
    定義容器啟動時執行的指令，以這個案例來說等同
    ```
    java -jar application.jar
    ```

我們來試著透過 Docker Client 建立 Container Image
1. 確定你的終端機 `projects/demo` 的目錄下
2. 執行 `docker build`
    ```
    docker build -t demo-image:v1 .
    ```
    > 指令中 `-t demo-image:v1` 代表我希望該 Container Image 的名稱為 demo-image，且 tag 為 v1。當未來這服務有更新時，能使用同一個名稱，調整 tag 為 v2，方便區分不同版本的 Container Image。

    運行結果:
    ![docker-build](https://cdn.staticaly.com/gh/YihongGao/picx-images-hosting@master/20230903/截圖-2023-09-03-上午3.02.51.32r4azzzea20.webp)

# 測試剛建構的 Container Image
上傳 Container Registry 之前，先在本地運行做個簡單的測試。
```
# 使用 demo-image:v1 來建立 Container instance
docker run -p 8080:8080 demo-image:v1

# 呼叫 API
curl localhost:8080
```
運行結果
![run_and_curl](https://cdn.staticaly.com/gh/YihongGao/picx-images-hosting@master/20230903/截圖-2023-09-03-上午3.14.04.52mk1hg58wc0.webp)
收到 API 服務返回 "Hello, welcome to use the container." 的訊息，代表建構成功。

# 上傳到 Container Registry
這邊為了讀者自行實作方便，範例為上傳至免費的 DockerHub

1. 註冊並登入 DockerHub 後，點擊 Repositories / Create repository
   ![Create repository](https://cdn.staticaly.com/gh/YihongGao/picx-images-hosting@master/20230903/截圖-2023-09-03-上午3.21.05.5h8n51bk9w8.webp)
2. 替你的 repository 取名，並點 Create
   > 此範例我用 `demo-image` 當 repository 名稱
3. 使用 Docker Client 登入 docker 帳戶
    ```
    docker login
    ```
4. 將本地的 Container Image 打上 DockerHub 的位址
    ```
    docker tag demo-image:v1 ${你的 docker 帳號}/${你的repository名稱}:v1
    ```
5. 上傳至 DockerHub
    ```
    docker push ${你的 docker 帳號}/${你的repository名稱}:v1
    ```
    運行結果：
    ![docker-push](https://cdn.staticaly.com/gh/YihongGao/picx-images-hosting@master/20230903/截圖-2023-09-03-上午3.37.27.4qb68l128960.webp)
    上傳完之後，就能在 DockerHub 上看到該 Container Image 了
    ![dockerhub-ls-v1](https://cdn.staticaly.com/gh/YihongGao/picx-images-hosting@master/20230903/截圖-2023-09-03-上午3.37.06.20dv71nmvzc0.webp)


# 總結
今天我們使用了 Dockerfile 來定義如何建置 Container Image，並透過 Docker client 建立了自己的 Container Image，而該 Container 的內容包含了以下內容
- Java 17 的函式庫(因為 BaseImage為 openjdk:17)
- 我們的 API 應用服務

這就達到了 **一致的環境**、**依賴性管理** 的好處，透過交付 Container 來部署服務，不需太擔心運行的環境差異。

> 1. **一致的環境** ：容器確保應用程式在不同環境中運行一致，開發者可以在本地開發環境中進行測試，確保應用程式在生產環境中也能正常運行。
> 2. **依賴性管理** ：容器打包了應用程式的所有依賴項，開發者無需擔心環境差異導致的依賴性問題，從而減少了開發和部署中的挑戰。

最後也透過 `docker tag` 與 `docker push`，將 Image 推送到 Container Registry，讓遠端伺服器或其他人能透過 `docker pull` 取得該 image。
> 注意：若你的 Container 不想給外人取得，DockerHub Repository 務必選擇 private

    





 

