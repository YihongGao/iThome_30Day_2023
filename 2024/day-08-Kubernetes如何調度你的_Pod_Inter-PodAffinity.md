
# Day-08-Kubernetes 如何調度你的 Pod - Inter-Pod Affinity

# 前言
昨天介紹了三個依據 Node 配置(Name 或 label) 來安排 Pod 要被調度到哪個 Node 的方式
1. NodeName
2. NodeSelector
3. Node Affinity

今天會來介紹另外一個管理 Pod 如何調度的方式 `Inter-Pod Affinity / Anti-affinity` 

# Inter-Pod Affinity / Anti-affinity
`Inter-Pod Affinity / Anti-affinity` 的使用方式與 Node Affinity / Anti-affinity 類似，但它們比對的是 Pod 的 Label，而不是 Node 的 Label。

常見使用案例包括：
- 將相依性強的 Pod 部署在同一個 Node 或同一個 zone，以減少網路延遲或傳輸費用。
- 將 Pod 的副本分散到不同的 Node 上，以降低 Node 異常時發生服務中斷的風險。 

在使用 `Inter-Pod Affinity / Anti-affinity` 之前，需要了解其分群方式。與 Node Affinity 按照每個 Node 進行分群不同，Inter-Pod Affinity / Anti-affinity 是透過 topologyKey 屬性來判斷和比對 Node 的 Label。

具有相同 Label 值的 Node 被歸為一個群組，這個群組稱為 topology。

常見的 **topology** 配置方式有
1. 每個 Node 自己為一個 topology : `topologyKey=kubernetes.io/hostname`
![https://www.hwchiu.com/assets/images/BJ5XNkE33-caacc5f3872a29542bbd572b1b8b1ea2.png](https://www.hwchiu.com/assets/images/BJ5XNkE33-caacc5f3872a29542bbd572b1b8b1ea2.png)
圖檔來源：- [HWCHIU 學習筆記 / 解密 Assigning Pod To Nodes(下)]
    > 📘 每個 Node 的 `kubernetes.io/hostname` value 通常都是唯一的

2. 每個 zone 為一個 topology : `topologyKey=topology.kubernetes.io/zone`
![https://www.hwchiu.com/assets/images/BkD4V1Vhn-90ae866222166bf21ebfc41a92443a9f.png](https://www.hwchiu.com/assets/images/BkD4V1Vhn-90ae866222166bf21ebfc41a92443a9f.png)
圖檔來源：- [HWCHIU 學習筆記 / 解密 Assigning Pod To Nodes(下)]
    > 📘 上圖中的Label `kind.zone`，是為了在本地環境模擬 zone Label 自定義的，能想像與 `topology.kubernetes.io/zone` 等價。     
    > 📘 有更多 Label 可使用，例如 region 為單位，可參考 [官方文件](https://kubernetes.io/zh-cn/docs/reference/labels-annotations-taints/)

    了解 topology 的規則後，我們直接看一下使用範例

## 使用範例
### 把兩個服務放到同個 node 降低網路延遲
假設我們有一組服務(nginx + redis)，並希望運行在同個 node，減少網路延遲，我們繼續使用昨天本地建構的 kubernetes cluster 進行操作

先部署 redis，並透過 Inter-Pod Anti-affinity 讓他盡量分散到每個 Node

```yaml
## redis.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis
spec:
  selector:
    matchLabels:
      app: redis
  replicas: 3
  template:
    metadata:
      labels:
        app: redis
    spec:
      containers:
      - name: redis
        image: redis:latest
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
          - labelSelector:
              matchExpressions:
              - key: app
                operator: In
                values:
                - redis
            topologyKey: "kubernetes.io/hostname"
```

```shell
kubectl apply -f redis.yaml
```
能看到使用方式與昨天介紹的 Node affinity 非常相似，一樣有
- `requiredDuringSchedulingIgnoredDuringExecution`
- `preferredDuringSchedulingIgnoredDuringExecution`
作為 強制條件 或者是 優先傾向。
只是 `matchExpressions` 比對的對象改成該 Node 上運行的 Pod label，並依照 `topologyKey` 進行分群。

我們能看到 redis pod 被均勻分佈在不同 node 上，因為 topologyKey 指定 `kubernetes.io/hostname` 並且搭配 `podAntiAffinity` 表示 Pod 不希望被調度到有運行 `app=redis` label pod 的 Node。
```shell
kubectl get pod -o wide

NAME                     READY   STATUS    RESTARTS   AGE   IP            NODE                  NOMINATED NODE   READINESS GATES
redis-5f5d8dd5d4-kqbxr   1/1     Running             0          31m   10.244.3.12   ithome-2024-worker2   <none>           <none>
redis-5f5d8dd5d4-lfzc8   1/1     Running             0          31m   10.244.4.5    ithome-2024-worker3   <none>           <none>
redis-5f5d8dd5d4-nqxgw   1/1     Running             0          31m   10.244.1.4    ithome-2024-worker4   <none>           <none>
```

接著我們透過 podAffinity 將 nginx 的部署到同個 pod
```yaml
## nginx.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx
spec:
  selector:
    matchLabels:
      app: nginx
  replicas: 4
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
      - name: nginx
        image: nginx:latest
      affinity:
        podAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
          - labelSelector:
              matchExpressions:
              - key: app
                operator: In
                values:
                - redis
            topologyKey: "kubernetes.io/hostname"
```

```shell
kubectl apply -f nginx.yaml
```
透過 `podAffinity.requiredDuringSchedulingIgnoredDuringExecution` 並指定 redis Pod 的 label，表示 nginx 服務一定要部署到有 redis Pod(`app=redis`) 的 Node

```shell
kubectl get pod -o wide

NAME                     READY   STATUS    RESTARTS   AGE   IP            NODE                  NOMINATED NODE   READINESS GATES
nginx-799d9c7f87-dh49v   1/1     Running   0          73s   10.244.3.19   ithome-2024-worker2   <none>           <none>
nginx-799d9c7f87-dzm8m   1/1     Running   0          73s   10.244.1.14   ithome-2024-worker4   <none>           <none>
nginx-799d9c7f87-gfp8w   1/1     Running   0          73s   10.244.1.15   ithome-2024-worker4   <none>           <none>
nginx-799d9c7f87-ntth2   1/1     Running   0          73s   10.244.4.15   ithome-2024-worker3   <none>           <none>
redis-5f5d8dd5d4-kqbxr   1/1     Running   0          33m   10.244.3.12   ithome-2024-worker2   <none>           <none>
redis-5f5d8dd5d4-lfzc8   1/1     Running   0          33m   10.244.4.5    ithome-2024-worker3   <none>           <none>
redis-5f5d8dd5d4-nqxgw   1/1     Running   0          33m   10.244.1.4    ithome-2024-worker4   <none>           <none>
```
能看到 4 個 nginx Pod 都部署到運行有 redis 的 Node，沒有被調度到未運行 redis 的 Node(`ithome-2024-worker`)。

從上述兩個 yaml，我們學到
- 透過 `podAntiAffinity.requiredDuringSchedulingIgnoredDuringExecution` 將 Pod 盡量分佈到不同 Node。
- 透過 `podAffinity.requiredDuringSchedulingIgnoredDuringExecution` 讓兩個服務運行在同個 node 或同個 zone，降低網路延遲或網路成本。

# 小結
透過這兩天認識的 Node Affinity 與 Inter-Pod Affinity 能滿足許多調度 Pod 的需求，

回顧一下他們常見的使用案例
- Node Affinity 
    - 綁定有特定硬體設備的 Node
    - 依照用途規劃 Node 職責，避免不同產品互相影響
    - 合規需求，有些法規要求資料只能保存在特定地理位置
- Inter-Pod Anti-Affinity
    - 將耦合度高的服務部署在一起，減少網路延遲
    - 將資源競爭高的服務分散到不同 Node，減少資源瓶頸(network、CPU)

但若是希望為了高可用性，要讓 Pod 分散時，使用上還是會遇到一些問題
- 使用 Node Affinity 時，Pod 可能會被集中在一個或少數的 Node，當該 Node 發生異常，可能會造成服務中斷
- 使用 Inter-Pod Anti-Affinity，雖然能將 Pod 分散在不同 Node 上降低風險，但若進行 Deployment Rolling update 時，新版本的 Pod 沒有節點可以部署，會導致 Rolling update 無法進行。

所以明天會來介紹一個專門將 Pod 副本分散到不同 Node，降低 Node 崩潰的影響範圍，提高服務可用性的功能：`Pod TopologySpreadConstraints`


# Refernce
- [kubernetes 官方文件](https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/)
- [HWCHIU 學習筆記 / 解密 Assigning Pod To Nodes(下)]
- [小信豬的部落格 / [Kubernetes] Assigning Pods to Nodes](https://godleon.github.io/blog/Kubernetes/k8s-Assigning-Pod-to-Nodes/)

[kind]: https://kind.sigs.k8s.io/

[HWCHIU 學習筆記 / 解密 Assigning Pod To Nodes(下)]: https://www.hwchiu.com/docs/2023/k8s-assigning-pod-2