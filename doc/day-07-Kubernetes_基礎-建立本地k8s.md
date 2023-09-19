# Day-07-Kubernetes_介紹-建立本地 Kubernetes 環境

# 前言
接下來幾天會開始介紹 Kubernetes 常用的組件(Pod, Deployment, Service..)，在認識這些組件的時候，希望讀者也能透過實際操作來加深印象，所以先介紹怎麼透過 `kind` 在本地快速的搭建 Kubernetes 環境，並使用 `kubectl` 操作你的 Kubernetes 環境。

Note: 
透過 `kind` 建立的環境非常適合開發與測試，但並不適合使用再生產環境，若希望建立生產環境的 Kubernetes Cluster 請參考[官方手冊]()

# 前置作業
今天我們先於本地安裝 `kubectl` 與 `kind`，筆者的操作環境為 MacOS (Apple Silicon)。
- kubectl    
  Kubernetes 的 command-line 工具，可以透過此工具來部署應用服務或操作、管理你的 Kubernetes。  
    ```
    # 透過 Homebrew 安裝
    brew install kubectl

    # 檢查是否安裝成功
    kubectl version --client=true
    ```
    Note: Windows / Linux 或其他安裝方式能參考 [kubectl 安裝官方手冊](https://kubernetes.io/zh-cn/docs/tasks/tools/)

- kind ("Kubernetes IN Docker")
    使用 Docker container 為基礎，能在本地環境快速的建立  Kubernetes 群集，是一個輕量級的搭建方案，適合用於開發與測試。
  ```
  # 透過 Homebrew 安裝
  brew install kind

  # 檢查是否安裝成功
  kind version
  ```
   Note: Windows / Linux 或其他安裝方式能參考 [kind 安裝官方手冊](https://kind.sigs.k8s.io/docs/user/quick-start/#installation)

# 建立你的 Kubernetes 環境
1. 準備 kind-config.yaml    
    因為 kind 預設只會建立一個單一節點，要建立多個節點時，需要提供配置檔案來定義，所以我們在本地建立一個 kind-config.yaml 的檔案

    kind-config.yaml 內容：
    ```
    apiVersion: kind.x-k8s.io/v1alpha4
    kind: Cluster
    nodes:
    - role: control-plane  # 指定要一個 Master Node
    extraPortMappings:
    - containerPort: 30000 # 本地端能訪問 Cluster 30000 Port
        hostPort: 30000
        listenAddress: "0.0.0.0" # Optional, defaults to "0.0.0.0"
        protocol: tcp # Optional, defaults to tcp
    - containerPort: 30001 # 本地端能訪問 Cluster 30001 Port
        hostPort: 30001
        listenAddress: "0.0.0.0" # Optional, defaults to "0.0.0.0"
        protocol: tcp # Optional, defaults to tcp
    - role: worker  # 指定要一個 Worker Node
    ```
    檔案內容除了指定要一個 Master Node + Worker Node 之外，也要求轉發 30000 & 30001 port 到 Host 方便後面測試用。
2. 建立 Kubernetes Cluster
    ```
    # 建立一個 Kubernetes cluster 且 cluster name 為 my-cluster
    kind create cluster --name my-cluster --config kind-config.yaml

    # 查詢建立的 clusters
    kind get clusters
    ```
    執行結果
    ![kind create cluster](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230903/截圖-2023-09-05-上午2.34.29.9lq21h6p0u8.webp)
   預設為會使用 Docker container 建立一個 kubernetes node，該 node 同時為 master node(control-plane) 與 worker node。
3. 配置 `kubectl` 要操作哪個 kubernetes 
    >`kubectl` 是 Client 端組件，用來操作本地或遠端的 Kuberntes，所以需要指定現在你要操作的 Kuberntes。       
    而透過 `kind create cluster` 建置時，會將該 Kubernetes 配置一個 Context 到本地端的配置檔(kubeconfig)中，該 Context 的名稱方式為 `kind-${you cluster name}。

    ```
    # 查詢 Context 名稱，我的 cluster name 為 my-cluster，故 context 名稱應為 kind-my-cluster
    kubectl config get-contexts

    # 設定 kubectl context 為 kind-my-cluster
    kubectl cluster-info --context kind-my-cluster
    ```
    執行結果
    ![get-contexts](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230903/截圖-2023-09-05-上午2.52.49-3.1zuriu1dkoyo.webp)
    ![set-context](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230903/截圖-2023-09-05-上午2.57.38.5taa9tdepu80.webp)
    
4. Cluster 已經建立完成，讓我們來部署個服務，測試一下 Cluster 是否可用
    ```
    # 部署一個叫 podinfo 的 golang 服務
    kubectl apply -k github.com/stefanprodan/podinfo//kustomize

    # 讓該服務能透過 localhost:30000 訪問
    kubectl patch svc podinfo -p '{"spec": {"type": "NodePort", "ports": [{"port": 9898, "targetPort": 9898, "nodePort": 30000}]}}'
    ```
    使用瀏覽器訪問 `localhost:30000`
    ![podman](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230903/截圖-2023-09-05-上午3.37.13.25123q1r5li8.webp)

    出現以上畫面代表你已經成功部署了一個服務在本地端的 Kubernetes cluster。

# 總結
今天我們透過 `kind` 建立了一個適合開發與測試的 Kubernetes Cluster，並能透過 `kubectl` 操作該 Cluster，最後也部署了一個服務到 Kubernetes 上運行，接下來會開始介紹 Kubernetes 中常用的組件。

# 補充
若要清掉上面部署的服務來保持 Cluster 乾淨的話，能使用下面語法
```
k delete -k github.com/stefanprodan/podinfo//kustomize
```
