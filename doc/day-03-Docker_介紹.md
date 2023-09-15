# Day-03-Docker 介紹-如何使用 Container Image

# 前言
許多開發者都已經相當熟悉如何使用 Docker，所以我們就快速介紹 Docker 架構與 常用的 command 來運行服務 與 如何建立屬於你的 Container Image.

# Docker 安裝
安裝的部分能直接參考 [官方安裝指南](https://docs.docker.com/desktop/).

# Dokcer 架構介紹
![image.png](https://docs.docker.com/assets/images/architecture.svg)

## 核心元件

1. **Docker Daemon**：Docker Daemon 是在主機上運行的背景服務，負責管理容器的生命週期，如創建、啟動、停止和刪除容器。它與 Docker Client 進行通信。  
    > Docker Host 就是安裝了 Docker Daemon 的主機
1. **Docker Client**：Docker Client是用戶與 Docker 互動的界面。通過 Docker Client，用戶可以發出命令來管理Container 、Images、網絡等。它可以遠程連接到 Docker Daemon，也可以在本地運行。

2. **Docker Images（Container Images）**：Docker Images是應用程式的模板，包括運行應用程式所需的一切，如程式碼、依賴庫、配置等。Images是不可變的，用於創建 Container 實例。

3. **Container**：Container 是基於 Docker Images運行的實例。它們提供了隔離的運行環境，確保應用程式在不同環境中具有一致性的運行行為。

4. **Docker Registry**：Docker Registry 是用於存儲和分享 Docker Images 的中央儲存庫。Docker Hub 是最知名的 Docker Registry 之一，它包含了眾多公共Images供使用。

# 使用案例 - 使用 Docker 運行服務
假設我想在本地環境運行 [Nginx](https://hub.docker.com/_/nginx) 服務，能依循以下步驟
> 此範例 Docker Daemon 與 Docker Client 都安裝在本地環境，所以本地環境既是 Client 也是 Docker Host。

1. 至 [Docker Registry](https://hub.docker.com/) 搜尋想要的服務，此處已 [Nginx](https://hub.docker.com/_/nginx) 為例，能查到有許多不同版本的 Container Image。
![Nginx DockerHub](https://cdn.staticaly.com/gh/YihongGao/picx-images-hosting@master/20230902/截圖-2023-09-02-上午2.51.31.6vdw01t8rqw0.webp)
1. 我們選擇最新(latest)的版本，並該版本的 Container Image pull 到 Docker Host (本地)   
   執行：
    ```
    docker pull nginx:latest
    ``` 
    輸出結果：
    ![docker-pull](https://cdn.staticaly.com/gh/YihongGao/picx-images-hosting@master/20230902/截圖-2023-09-02-上午2.59.57.aqvqqukw1k0.webp)
2. 接下來檢查是否 Container Image 是否 pull 成功    
   執行：
    ```
    docker image ls
    ```
    輸出結果：
    ![docker-image-ls](https://cdn.staticaly.com/gh/YihongGao/picx-images-hosting@master/20230902/截圖-2023-09-02-上午3.06.46.12m8a0ll3z2o.webp)
    `docker image ls` 能查出 Docker host 上所有的 Container Image，因為第一步我們執行了 `docker pull nginx:latest`，所以能看到有一個 REPOSITORY 為 nginx，TAG 為 latest 的 Container Image 在清單內，代表 pull 成功了。
3. 使用 nginx:latest 這個 Container Image 創建一個 Container instance，且希望能使用 localhost:8080 連到 nginx 服務(預設為 80 port)    
    執行：
    ```
    docker run -d -p 8080:80 nginx:latest
    ```
    > 指令中的參數意義    
    `-d` ：Container 於背景運行
     `-p 8080:80` ：將 Docker Host 的 8080 port 與 container 中的 80 port 綁定，所以使用 localhost:8080 可訪問到該 container 中的 80 port 服務

    輸出結果：
    ![docker-run](https://cdn.staticaly.com/gh/YihongGao/picx-images-hosting@master/20230902/截圖-2023-09-02-上午3.32.17.738kuhqfzi40.webp)
    因為使用了 `-d` 參數，所以該 command 會返回一個唯一的 Container Id
4. 來檢查一下， Container 的狀態 與 runtime Log
    ```
    docker ps
    ```
    輸出結果：
    ![docker-ps](https://cdn.staticaly.com/gh/YihongGao/picx-images-hosting@master/20230902/截圖-2023-09-02-上午3.36.20-1.1a0kpb1ruerk.webp)
    能看到有一個 Container Instance 被創建了，且STATUS為 Up。
    我們來檢查進一步看運行日誌
    ```
    docker logs ${Container Id}
    ```
    > Container Id 能從 `docker ps` 或 `docker run -d` 返回的值取得。

    輸出結果：
    ![docker-logs](https://cdn.staticaly.com/gh/YihongGao/picx-images-hosting@master/20230902/截圖-2023-09-02-上午3.41.04.wlt0nzx3lts.webp)

    我們從 `docker ps` 確認了 STATUS 為 UP，且將本地 8080 port 與 container 80 port 綁定，之後再透過 `docker logs` 檢查運行日誌啟動正常，無 ERROR log，基本上這個 Container 應該正常運作了。
5. 透過瀏覽器使用 `localhosl:8080` 來連到 nginx 服務
    ![nginx-index](https://cdn.staticaly.com/gh/YihongGao/picx-images-hosting@master/20230902/截圖-2023-09-02-上午3.26.12.4ljjthj3ng60.webp)
6. 當我們不需要使用該 Container 時，能將其停止或刪除
    ```
    docker stop ${Container Id}
    ```
    ![docker-stop](https://cdn.staticaly.com/gh/YihongGao/picx-images-hosting@master/20230902/截圖-2023-09-02-上午3.57.50.4e3fl882k8o0.webp)
    能看到執行 `docker stop` 之後，透過 `docker ps -a` 查詢該 Container 狀態為 Exited，代表已經停止運行。
    這時候能重新啟動 或 將其完全刪除。
    ```
    # 重新啟動該的 Container，先前運行的紀錄仍保留著。
    docker start ${Container Id}
    # 完全刪除，刪除後只能重新使用 docker run 創建，原本 Container 內運行的紀錄會消失
    docker rm ${Container Id}
    ```

# 總結
今天我們已經知道怎麼使用 Docker Client 並利用他人建立的 Container Image 部署 Container instance 在本地環境，也能從 localhost 訪問該 Container 中的服務，與如何停止/重啟/刪除該 Container，以後要在本地架設 Redis、RabbitMQ 等中間件，也能參考此方式建立。 

# 補充資料

## docker container 生命週期
![docker-life-cycle](https://res.cloudinary.com/practicaldev/image/fetch/s--0uJGEEuc--/c_limit%2Cf_auto%2Cfl_progressive%2Cq_auto%2Cw_880/https://dev-to-uploads.s3.amazonaws.com/uploads/articles/gevspybo00m3a7l4hfrz.png)
## 常用的 docker command
| 命令 | 描述 |
|------|------|
| docker --version | 檢查 Docker 版本。 |
| docker images | 列出本地的 Docker 映像。 |
| docker pull <image_name> | 下載 Docker 映像。 |
| docker rmi <image_name> | 刪除本地的 Docker 映像。 |
| docker ps | 列出正在運行的容器。 |
| docker ps -a | 列出所有容器，包括已停止的容器。 |
| docker run <image_name> | 運行一個新容器。 |
| docker start <container_name> | 啟動一個已停止的容器。 |
| docker stop <container_name> | 停止一個正在運行的容器。 |
| docker rm <container_name> | 刪除一個容器。 |
| docker exec -it <container_name> /bin/bash | 進入一個正在運行的容器的互動式 Shell。 |
| docker logs <container_name> | 查看容器的日誌。 |
| docker stats <container_name> | 查看容器的資源使用情況。 |
| docker system prune | 清理未使用的映像、容器和資源。 |
| docker network ls | 列出 Docker 網絡。 |
| docker network inspect <network_name> | 查看網絡的詳細信息。 |
