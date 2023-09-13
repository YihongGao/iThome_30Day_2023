# Day-13-Kubernetes_介紹-Secret

# 前言
昨天介紹了 ConfigMap，因 ConfigMap 使用明文來儲存數據的特性，故不適合儲存資敏資訊，在 Kubernetes 中應該使用今天介紹的 Secret 來儲存。

# 什麼是 Secret
簡單來說，Secret 的建立方式與使用方式非常相似，一樣提供以下方式讓 Pod 使用
- 環境變數
- 掛載檔案
- 命令行參數(通常建議不使用此方式，容易被日誌系統收集，增加洩漏風險)
  
`kubectl` 提供使用以下方式建立 Secret
- 從文件創建（Create from File）：從本地文件或目錄中的配置數據創建 Secret。例如：
    ```
    kubectl create secret generic my-secret --from-file=secret-file.yaml
    ```
- 指定 Key-Value：指定配置數據的鍵值對，而無需從文件中讀取。例如：
    ```
   kubectl create secret generic my-secret --from-literal=username=myusername --from-literal=password=mypassword
    ```
- 從 yaml 建立：從一個包含 Secret。例如： 定義的 YAML 文件，然後使用 kubectl apply 命令將其應用到集群中。
    ```
    kubectl apply -f my-secret.yaml
    ```
  
讓我們透過 `kubectl` 建立一個 demo-secret.yaml，並介紹其中的屬性
```
# create secret
kubectl create secret generic demo-secret --dry-run=client -o yaml --from-literal=APP.TOKEN="dummy token from secret" > demo-secret.yaml 
```
> 參數中的 `generic` 代表通用類型，代表儲存的資料使用 key-value pair 儲存，也有 docker-registry, tls.. 等類型能提供訪問 dockerHub 或儲存 TLS 憑證, 更多類型請參考[官方文檔](https://kubernetes.io/docs/concepts/configuration/secret/#secret-types)

demo-secret.yaml 內容
```
apiVersion: v1
data:
  APP.TOKEN: ZHVtbXkgdG9rZW4gZnJvbSBzZWNyZXQ=
kind: Secret
metadata:
  creationTimestamp: null
  name: demo-secret
```

Secret 的 yaml 格式也非常簡單，能看到資料存放於 `data` 欄位，並已 key-value 的方式定義，差別在 Value 的值是使用 Base64 編碼後的值，而非明文，依此例存放了一個變數
|   key             |    value         | decode value | 
|--------------------|---------------|---------------|
| APP.TOKEN| ZHVtbXkgdG9rZW4= | dummy token |
> 注意！ 經過 Base64 編碼的資料，能輕易被還原成原始值，需搭配後續 **如何保護 Secret** 章節才能保護了機敏資料

我們透過該 yaml 建立 Secret 資源，方便後面實作進行測試
```
# apply yaml
kubectl apply -f demo-secret.yaml

# 查詢建立的 Secret
kubectl get secret demo-secret
```

## 實作
與 ConfigMap 的實作相同，我們使用一個簡單的 [範例應用程序](https://github.com/YihongGao/iThome_30Day_2023/tree/main/projects/demo)，這個應用程序提供一個 API
- localhost:8080/token: 返回值使用環境變數 `APP.TOKEN` 之值，若該變數不存在時，返回 `local token.`

我們調整一下昨天建立的 deployment，將 secret 當作環境變數
搭配上面建立的 configMap 來建立一個 deployment，並使用該 configMap 當作配置檔
```
# 建立 deployment
cat <<EOF | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: demo-deployment
spec:
  replicas: 1
  selector:
    matchLabels:
      app: demo-deployment
  template:
    metadata:
      labels:
        app: demo-deployment
    spec:
      containers:
        - name: app
          image: yihonggaotw/demo-image:v3
          envFrom:  # 將 ConfigMap 或 Secret 注入此容器
            - configMapRef:
                name: demo-config # 注入的 ConfigMap 名稱
            - secretRef:
                  name: demo-secret # 注入的 Secret 名稱
EOF
```

## 測試
```
# 將該 Pod 服務轉發至本地 8080 port 提供測試
kubectl port-forward deployments/demo-deployment 8080:8080
```
測試 localhost:8080/token，預期結果為 `dummy token from secret` 而不是預設得 `local token.`
![test-secret](https://cdn.staticaly.com/gh/YihongGao/picx-images-hosting@master/20230911/截圖-2023-09-12-上午2.50.54.neo6foakvnk.webp)


# 如何保護 Secret 中的資敏資訊
儲存於 Secret 的資料，是透過 Base64 編碼後儲存的，而 Base64 不是加密演算法，任何人取得該值，都能反編碼並取得原始值，導致機敏資訊洩漏。

所以必須搭配其他措施才能 保護 Secret 中的資敏資料
1. 最小權限原則：應只有 Kubernetes 管理者可存取 Secret，而非所有人都可以存取，透過 [RBAC](https://kubernetes.io/zh-cn/docs/reference/access-authn-authz/rbac/) 減少能存取 Secret 的 User 或 token，降低洩漏風險。
2. 避免洩漏 未加密的 Secret yaml 或 上傳至 git repo.
3. 只有特定 ServiceAccount 能存取 Sercet，請參考[官方文檔](https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/)
   
# 總結
Kubernetes 在設計時，區分了 ConfigMap 與 Secret 這兩個組件，分別應用於不同的使用案例，也讓管理者能透過 [RBAC](https://kubernetes.io/zh-cn/docs/reference/access-authn-authz/rbac/) 來區隔權限，保護 Secret 數據的安全。

- ConfigMap:
  - 適合用例： ConfigMap 主要用於存儲應用程序的配置數據，例如環境變數、配置文件、命令行參數等。這些配置通常是非敏感的，可以以明文形式存儲。
  - 數據格式： ConfigMap 中的數據存儲為鍵值對（Key-Value Pairs），並且數據是明文存儲的。這意味著 ConfigMap 中的數據可以被容器中的應用程序輕鬆訪問。

- Secret:
  - 適合用例：Secret 主要用於存儲敏感數據，例如密碼、API 密鑰、TLS 金鑰等。
  - 數據格式： Secret 中的數據也存儲為鍵值對，但數據是以 Base64 編碼形式存儲的，仍須控制 Secret 的存取權限，才能避免洩漏。

# 延伸問題 - 能透過 GitOps 管理 Secret 嗎?
補充知識: 
[GitOps](https://www.cncf.io/blog/2021/09/28/gitops-101-whats-it-all-about/) 是實現 IaC(Infrastructure as Code)的方式，運用在 Kubernetes 生態時，能簡單理解為有一個 git repo，裡面存放了你部署在 Kubernetes 上所有資源的 yaml file。

但 Secret 的 yaml 中，數據欄位是 base64 編碼的，如果上傳至 git repo，能存取這個 repo 的使用者，都能還原並取得 Secret 中的資敏資訊，這造成更多洩漏的風險。

明天我們來介紹如何透過 [sealed-secrets](https://github.com/bitnami-labs/sealed-secrets) 來解決這個問題