# Day-11-Kubernetes_介紹-ConfigMap

# 前言
今天來介紹一下 ConfigMap，我們在介紹 Container 時，有介紹到容器具有**一致的環境**、**依賴項管理左移** 的特性，讓 Container 具有良好的可移植性。
> 1. **一致的環境** ：容器確保應用程式在不同環境中運行一致，開發者可以在本地開發環境中進行測試，確保應用程式在生產環境中也能正常運行。
> 2. **依賴項管理左移** ：開發者在打包容器時，將應用程式的所有依賴項一併放入 Container，而不必跨團隊溝通依賴項配置問題，從而減少了開發和部署中的挑戰。

但應用程序執行時，除了依賴函式庫，通常還依賴了配置項，且這個配置項，通常與運行環境綁定的，這時候該怎麼處理。
> 如運行在不同環境時，資料庫的連線位址是不同的。舉例
> - 測試環境：jdbc:mysql://192.168.1.100:3306/apps
> - 正式環境：jdbc:mysql://203.0.113.50:3306/apps

依照 [12factor](https://12factor.net/config) 的建議，應該**將配置從程式碼中分離**出來，以便在不同環境中（開發、測試、生產等）運行，且能搭配不同的配置，才算是有良好的可移植性。

今天介紹的 ConfigMap，就是 Kubernetes 提供給我們集中管理這類配置的資源。

# 什麼是 ConfigMap
ConfigMap 提供 key-value pairs 的方式來儲存配置，讓 Pod 可以依照需要將 ConfigMap 透過環境變數、檔案，命令參數等方式提供給應用程序，讓容器與配置解耦。

能透過 `kubectl` 使用以下方式建立 ConfigMap

- 從文件創建（Create from File）：使用 kubectl create configmap 命令，您可以從本地文件或目錄中的配置數據創建 ConfigMap。例如：
    ```
    kubectl create configmap my-config --from-file=config-files/
    ```
- 指定 Key-Value：使用 kubectl create configmap 命令，您可以直接指定配置數據的鍵值對，而無需從文件中讀取。例如：
    ```
    kubectl create configmap my-config --from-literal=key1=value1 --from-literal=key2=value2
    ```
- 從 yaml 建立：您可以創建一個包含 ConfigMap 定義的 YAML 文件，然後使用 kubectl apply 命令將其應用到集群中。
    ```
    kubectl apply -f my-config.yaml
    ```
  
實務上，我們為了同一份代碼，能在本地運行方便開發，又能與配置解耦，通常會透過**環境變數**的方式並搭配預設值，讓應用程序使用配置時，遵循以下規則選擇配置
    1. 當該配置對應的環境變數存在時，應用程序使用環境變數之值
    2. 若不存在，使用應用程序的預設值

已 Spring boot 為例，application.yml 中配置方式如下
```
# 使用 SpEL 表達式來設置參數，並指定預設值
app:
  welcome:
    message: ${APP.WELCOME.MESSAGE: Hello, World}
```
意思是 app.welcome.message 這個配置的值會先從**先從環境變數**的`APP.WELCOME.MESSAGE` 取得，若該環境變數不存在，則使用 Hello, World 當作配置值。

讓我們透過 `kubectl` 建立一個 ymal，並介紹其中的屬性
```
kubectl create configmap demo-config --dry-run=client -o yaml --from-literal=APP.WELCOME.MESSAGE="Hello, ConfigMap" --from-literal=APP.ENV=kind > demo-config.yaml 
```
會產生一個 demo-config.yaml 
```
apiVersion: v1
data:
  APP.ENV: kind
  APP.WELCOME.MESSAGE: Hello, ConfigMap
kind: ConfigMap
metadata:
  creationTimestamp: null
  name: demo-config
```
ConfigMap 的 yaml 格式非常簡單，能看到資料存放於 `data` 欄位，並已 key-value 的方式定義，依此例存放了兩個變數
|   key             |    value         |
|--------------------|---------------|
| APP.WELCOME.MESSAGE| Hello, ConfigMap |
| APP.ENV            | kind           |


## 實作
使用一個簡單的 Spring boot project，並提供兩個 API
- localhost:8080: 返回值使用環境變數 `APP.WELCOME.MESSAGE` 之值，若該變數不存在時，返回 `Hello, welcome to use the container.`
- localhost:8080: 返回值使用環境變數 `APP.ENV` 之值，若該變數不存在時，返回 `local`

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
          image: yihonggaotw/demo-image:v2
          envFrom: # 將 ConfigMap 注入此容器
            - configMapRef:
                name: demo-config # 注入的 ConfigMap 名稱
EOF
```

## 測試
```
# 查該 Deployment 生成的 Pod name
kubectl get pod --selector app=demo-deployment

# 將該 Pod 服務轉發至本地 8080 port 提供測試
kubectl port-forward pods/${你的 pod name} 8080:8080
```
- 測試 localhost:8080，預期結果為 `Hello, ConfigMap` 而不是預設得 `Hello, welcome to use the container.`
![hello-configMap](https://cdn.staticaly.com/gh/YihongGao/picx-images-hosting@master/20230911/截圖-2023-09-12-上午12.11.51.11trm2d6ti4.webp)

- 測試 localhost:8080/env，預期結果為 `kind` 而不是預設得 `local`
![hello-configMap](https://cdn.staticaly.com/gh/YihongGao/picx-images-hosting@master/20230911/截圖-2023-09-12-上午12.11.03.2m6tksf94z40.webp)

# 使用 ConfigMap 該注意的事項
- ConfigMap 中儲存的值是明文的，且不提供加密功能，故**不適合**存放機敏資訊(API token, Password..等資訊)
    > 機敏資訊 適合用 Secret 來儲存，會在明天介紹這組件
- ConfigMap 不適合儲存大量數據，數據容量上限是 1 MiB
- 更新 ConfigMap 中數據，已經在運行(Running)的 Pod 預設**不會使用新數據**，需要重啟該 Pod 才可以取得新數據
    > 為了保持 Pod 運行的環境穩定，預設 Kubernetes 方案不提供 熱更新。若有熱更新需求，可參考第三方提供的 [Reloader](https://github.com/stakater/Reloader)

# 總結
今天我們了解如何透過 ConfigMap 將 Container 與 配置 進行解耦，讓 Container 具有高可移植性，且將 配置 集中於 Kubernetes 管理。

與了解 ConfigMap 不適合儲存機敏數據，這類的資料，將由明天介紹的 Secret 來儲存。