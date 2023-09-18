# Day-09-Kubernetes_介紹-Deployment

# 前言
昨天我們使用 Pod 來建構服務，今天我們來介紹更強大的服務部署組件 Deployment。

# 什麼是 Deployment
Deployment 提供了高級的控制和管理功能，並能夠自動處理應用程序的升級、回滾、自我修復等操作，降低我們管理 Pod 的複雜度 與 提供更高的可用性。

Deployment 具有以下特性：
1. 滾動式更新 (Rolling update)
在不影響既有服務的狀況下，部署啟動新版本的服務，並在新版本準備好時，在逐建關閉舊版本的服務。
![rolling-update](https://images.ctfassets.net/23aumh6u8s0i/LxDl5amQZC2znii0JLZuw/87f42c35b5ba35ac1550f572d23ba943/01_rolling-deployment.jpg)
圖檔來源 [deployment-strategies-in-kubernete](https://auth0.com/blog/deployment-strategies-in-kubernetes/)

2. 自我修復
如果某個 Pod 意外終止，Deployment 會自動創建一個新的 Pod 來替代它，確保應用程序的持續運行。這種自我修復功能提高了應用程序的可靠性，無需手動干預。

3. 滾動回退
如果在升級過程中發現問題，Deployment 允許你快速回滾到以前的版本，確保應用程序能夠回到正常運行狀態。這是一個緊急恢復應用程序的關鍵功能。

# Deployment 與 Pod 的關係
![deployment-with-pod](https://godleon.github.io/blog/images/kubernetes/k8s-deployment.png)

Deployment 作為一個高階的元件，透過 Replica Set 元件來管理 Pod 的版本與數量，若想要對 Pod 進行調整，只要更新 Deployment 中的描述，而 Deployment 本身更關注部署策略 以及操作 Replica Set。

> 注意: 不要直接修改 Deployment 生成的 Replica Set / Pod 資源。應該更新 Deployment 中對 Pod 的描述，以避免潛在的更改失效或導致非預期問題。

# 建立一個 deployment
首先我們透過 `kubectl` 快速生成一個 deployment yaml 的模板，方便理解 yaml 內容

```
# 透過 `--dry-run=client` 與 `-o yaml` 生成 deployment yaml 並導出到當前目錄 my-deployment.yaml
kubectl create deployment my-deployment --image=nginx:1.24 --replicas=3 --dry-run=client -o yaml > my-deployment.yaml
```


會得到一個 my-deployment.yaml 的檔案，並另外手動添加關於 RollingUpdate 與 Pod expose ports 的屬性
```
apiVersion: apps/v1
kind: Deployment
metadata:
  creationTimestamp: null
  labels:
    app: my-deployment
  name: my-deployment
spec:
  replicas: 3
  selector:
    matchLabels:
      app: my-deployment
  strategy: {}
  template:
    metadata:
      creationTimestamp: null
      labels:
        app: my-deployment
    spec:
      containers:
      - image: nginx:1.24
        name: nginx
        resources: {}
        ports:
          - containerPort: 80
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 1
status: {}
```

逐步介紹幾個重要的屬性
- apiVersion: 表示這份 yaml 使用的 API 版本號，目前 Deployment 的 API 版本號為 `apps/v1`，若描述其他元件時，可能會使用不同版本號
- kind: 表示這份 yaml 描述的元件名稱
- metadata: 
  - name: Deployment 的名稱
  - labels: key/value 結構的標籤，建議每個服務都有一個獨特的 Labels
- spec: Deployment 的詳細配置
  - replicas: 要運行 3 個 Pod (Pod 的內容是依據 template 屬性)
  - template: Pod template
    - metadata:     
      - labels: key/value 結構的標籤，**建議與 Deployment Labels 內容一致**，方便管理
    - spec: Pod 的詳細配置    
      (省略, 此區塊 Pod 的 yaml 格式相同)
- strategy: 定義升級策略
  - type: 策略類型 (RollingUpdate / Recreate)
  - rollingUpdate：當策略為 "RollingUpdate" 時，可以配置的滾動更新相關屬性，如 maxSurge 和 maxUnavailable。\
    - maxSurge：指定在升級過程中允許的額外副本數量，這些副本將超過所需的數量。
    - maxUnavailable：指定在升級過程中允許不可用的最大副本數量。

我們使用這個 my-deployment.yaml 這個檔案來建置 Deployment resouce 看看
```
# 要求 Kubernetes 依據 my-pod.yaml 的內容，建立 Pod 
kubectl apply -f my-deployment.yaml 

# 查詢 deployment, replicasets 與 pod
kubectl get deployment,replicasets,pod
```

能看到產生了 1 個 deployment、 1 個 replicaset 與 3 個 pod(因為 yaml 中 spec.replicas 指定為 3)，且 Pod 的名稱為 ${replicaset name}-${隨機字串}
```
NAME                            READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/my-deployment   3/3     3            3           24m

NAME                                      DESIRED   CURRENT   READY   AGE
replicaset.apps/my-deployment-5f7cc5585   3         3         3       50s

NAME                                READY   STATUS    RESTARTS   AGE
pod/my-deployment-5f7cc5585-9mv76   1/1     Running   0          50s
pod/my-deployment-5f7cc5585-c2b56   1/1     Running   0          50s
pod/my-deployment-5f7cc5585-vlqq9   1/1     Running   0          49s
```

讓我們來嘗試看看 Deployment 滾動式更新 (Rolling update) 的功能，假設我想更新 my-deployment 的 pod 使用的 image 由 nginx:1.24 改為 nginx:1.25。
```
# 更新 my-deployment 中 Pod container name 為 nginx 使用的 Image 為nginx:1.25
kubectl set image deployments/my-deployment nginx=nginx:1.25

# 查詢 deployment, replicasets 與 pod 的變化
kubectl get deployment,replicasets,pod
```

能發現 replicasets 多出了一組，仍有 3 個 Pod，但 Pod NAME 與 Pod AGE 改變了，用了新的 replicasets 產生了新的 Pod
```
NAME                            READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/my-deployment   3/3     3            3           89m

NAME                                      DESIRED   CURRENT   READY   AGE
replicaset.apps/my-deployment-5bc65fc44   3         3         3       2m8s
replicaset.apps/my-deployment-5f7cc5585   0         0         0       66m

NAME                                READY   STATUS    RESTARTS   AGE
pod/my-deployment-5bc65fc44-67hcr   1/1     Running   0          2m8s
pod/my-deployment-5bc65fc44-9n8lb   1/1     Running   0          2m8s
pod/my-deployment-5bc65fc44-rk8fr   1/1     Running   0          2m7s
pod/my-pod                          1/1     Running   0          3h34m
```
Note: 因為 nginx 啟動速度很快，不太容易看到逐個更新的過程，能使用啟動較慢的 Image, 並在過程中使用 `kubectl get deployment,replicasets,pod` 觀察。
```
# 改用 啟動速度較慢的 Image
kubectl set image deployments/my-deployment nginx=yihonggaotw/demo-image:v1
```

# Rollback 
透過 `kubectl rollout undo` 回滾到先前的版本
```
# 回滾到上一個版本
kubectl rollout undo deployment/my-deployment   
```

其他回滾方式能參考[官方文檔](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#rolling-back-a-deployment)

# 自我修復
若 Deployment 產生的 Pod 被刪除，會自動重新創建新的 Pod，直到滿足 Deployment 中指定的 replicas 數量
```
# delete 其中一個 pod
kubectl delete pod ${pod-name}

# 查詢 pod 預期會有新的 pod 啟動，已滿足 replicas 要求的數量
```

# 總結
今天我們使用了 Deployment 來建立服務，且進行了 Rolling update 與 rollback，並驗證了 Deployment 的自我修復功能，官方建議使用 Deployment 來取代直接建立 Pod，能獲得較好的可用性。

另外有一個值得思考的地方，今天我們使用了同一個 Image，部署了多個 Pod，來強化該服務的可用性，但調用方怎麼知道要連線到哪一個 Pod 呢？Pod name 或 Pod IP 都是隨機配給或產生的，故無法指定 pod Id 來調用。
這個解決方式就是 Kubernetes 中的 Service 組件，明天會介紹怎麼使用 Service 組件來提供一個讓外部或其他Pod能穩定調用服務的方式。