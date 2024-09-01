
# Day-07-如何調度你的 Pod - Node Selector / Node Affinity

# 前言
今天我們要來介紹如何引導 Kubernetes 將 Pod 調度到我們希望的 Node。

在 Kubernetes 中，所有 Pod 都會運行在 Worker Node 上，而每個 Node 可能具有不同的配置或用途。以下是幾個常見的調度需求：

- 將某些服務部署到擁有 GPU 資源的節點上。
- 將基礎設施（如 Kafka、Redis 等）與業務應用程序隔離到不同的 Node，減少兩者之間的互相干擾，提升穩定性。
- 將同一服務的 Pod 副本分散部署在不同的 Node 或地理位置（region/zone），降低整個服務同時不可用的風險。

我們今天會介紹幾個操作方式，並透過 [kind] 在本地演練一次，讓 Kubernetes 依照我們的需求調度 Pod。

# 環境準備
於本地建構一個 4 個 worker node 的 k8s 環境

```shell
echo 'apiVersion: kind.x-k8s.io/v1alpha4
kind: Cluster
nodes:
- role: control-plane
  extraPortMappings:
  - containerPort: 30000
    hostPort: 30000
    listenAddress: "0.0.0.0" # Optional, defaults to "0.0.0.0"
    protocol: tcp # Optional, defaults to tcp
  - containerPort: 30001
    hostPort: 30001
    listenAddress: "0.0.0.0" # Optional, defaults to "0.0.0.0"
    protocol: tcp # Optional, defaults to tcp
- role: worker
  labels:
    zone: local-a
- role: worker
  labels:
    zone: local-a
    GPU: "true"
- role: worker
  labels:
    zone: local-b
- role: worker
  labels:
    zone: local-b' > kind-config.yaml

kind create cluster --name ithome-2024 --config kind-config.yaml

# create namespace
kubectl create ns ithome

# switch context to ithone namespace
kubens ithome
```

建立四個 Worker node，並透過 `zone=local-a` 與 `zone=local-b` 的 Label 模擬分佈在不同 zone，而其中一個 node 有個 `GPU=true` 的 Label 模擬擁有 GPU 運算資源。

# NodeName
直接透過指定 Pod.spec 中的 Node，來強制指定 Pod 要調度到什麼 Node 上。

```shell
echo 'apiVersion: v1
kind: Pod
metadata:
  name: node-name
spec:
  containers:
  - name: nginx
    image: nginx
  nodeName: ithome-2024-worker3' > node-name.yaml

# deploy
kubectl apply -f node-name.yaml

# 看 pod 是否被分配到 ithome-2024-worker3 node
kubectl get pod node-name -o wide

NAME    READY   STATUS    RESTARTS   AGE   IP           NODE                  NOMINATED NODE   READINESS GATES
node-name   1/1     Running   0          48s   10.244.4.2   ithome-2024-worker3   <none>           <none>
```
能看到 Pod 被正確分配到 ithome-2024-worker3 上了，原理是我們代替 `scheduler` 的任務，直接把 Pod 指定到 Node，所以這個 Pod 會由 `kubelet` 直接接手進行部署，不需要 `scheduler` 進行調度。

雖然這個做法簡單有效，但存在以下風險，所以通常我們不會選用此方式
- 若該 node 不存在時，此 Pod 無法部署成功
- 即使該 node 存在，若 node 剩餘資源無法滿足 Pod 的資源請求時，也會 Pod 部署失敗
- Cloud 環境的 Node name 通常是隨機的，若有 Node 的擴容/縮容 時，Node name 若改變會導致 Pod 部署失敗。

# NodeSelector
nodeSelector 是一個更安全且簡單的方式，透過 `pod.spec.nodeSelector` 指定 Node 的 Label，由 `scheduler` 去找出有符合 label 的 Node List 後，依照資源冗余等演算法從中找到最適合的 Node

```shell
echo 'apiVersion: v1
kind: Pod
metadata:
  name: node-selector
spec:
  containers:
  - name: nginx
    image: nginx
  nodeSelector:
    zone: local-a'  > node-selector.yaml

# deploy
kubectl apply -f node-selector.yaml

# 符合 zone: local-a 的 Node 清單
kubectl get nodes -l zone=local-a

NAME                  STATUS   ROLES    AGE   VERSION
ithome-2024-worker    Ready    <none>   92m   v1.30.0
ithome-2024-worker2   Ready    <none>   92m   v1.30.0

# 看 pod 是否被分配到 zone=local-a 的 Node
kubectl get pod node-selector -o wide

NAME            READY   STATUS    RESTARTS   AGE   IP           NODE                  NOMINATED NODE   READINESS GATES
node-selector   1/1     Running   0          41s   10.244.3.2   ithome-2024-worker2   <none>           <none>
```

能看到 Pod 被分配到其中一個符合 `zone=local-a`。
只要 `nodeSelector` 篩選出的 Node List，有任何一個 Node 有足夠資源能滿足 Pod 的資源要求，通常都能部署成功，更具有彈性。

但仍有許多改善空間，比如當擁有 `zone=local-a` 的 Node 都無法滿足該 Pod 的請求時，希望仍能部署到其他 Node，避免服務中斷。

# Node affinity/anti-affinity
`Node Affinity` 能簡單視為 nodeSelector 的加強版，提供了以下特性
- 能靈活的 label 選擇方式，原本 nodeSelector 只能使用 equals 與 AND
- 優先級特性，優先挑選滿足條件的 Node，但若不滿足時，也能調度到其他 Node

目前提供兩種 affinity 類型
- `requiredDuringSchedulingIgnoredDuringExecution`: 與 nodeSelector 類似，代表 Node 一定要符合條件才會被選上。
- `preferredDuringSchedulingIgnoredDuringExecution`: 優先選擇符合條件的 Node，若是符合條件的 Node 都不可用時，仍能選擇其他 Node

> 📘 兩個類型後面的 `IgnoredDuringExecution` 代表當 Pod 被分配到 Node 之後，當 Node label 被調整為不符合條件的值時，不會影響正在運行的 Pod。

## 使用範例
### 部署到有 Label `zone=local-a` 的 Node
```shell
echo 'apiVersion: v1
kind: Pod
metadata:
  name: node-affinity-required
spec:
  containers:
  - name: nginx
    image: nginx
  affinity:
    nodeAffinity:
      requiredDuringSchedulingIgnoredDuringExecution:
        nodeSelectorTerms:
        - matchExpressions:
          - key: zone
            operator: In
            values:
            - local-a' > node-affinity-required.yaml

kubectl apply -f node-affinity-required.yaml

# 看 pod 是否被分配到 zone=local-a 的 Node
kubectl get pod node-affinity-required -o wide

NAME                     READY   STATUS    RESTARTS   AGE   IP           NODE                 NOMINATED NODE   READINESS GATES
node-affinity-required   1/1     Running   0          15s   10.244.2.4   ithome-2024-worker   <none>           <none>
```
能看到 Pod 被部署在有 `zone=local-a` label 的 Node 上了。

來介紹幾個重要的屬性
- `requiredDuringSchedulingIgnoredDuringExecution`: 表示此 Pod 只部署在符合條件的 Node

- `nodeSelectorTerms`: 篩選條件的組合，包含一到多個篩選條件，該組合中全部條件都滿足時，才代表該 Node 符合部署的條件，而 `requiredDuringSchedulingIgnoredDuringExecution` 能同時有多個 `nodeSelectorTerms`，當 Node 滿足任一 `nodeSelectorTerms` 時，該 Pod 即允許部署到該 Node

- `matchExpressions`: 定義篩選條件的區塊，依此例來說，就是找到有 `zone` label 且 value 為 `local-a` 的 Node，有更多條件式用法可參考[官方文件](https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/#operators)

這個範例基本上跟 `nodeSelector` 等價，但條件式提供了更多方式能選擇作出更多變化。

## 優先部署到包含 GPU lable 的 Node
```shell
echo 'apiVersion: apps/v1
kind: Deployment
metadata:
  name: node-affinity-preferred
spec:
  replicas: 1
  selector:
    matchLabels:
      app: node-affinity-preferred
  template:
    metadata:
      labels:
        app: node-affinity-preferred
    spec:
      affinity:
        nodeAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
            - weight: 1
              preference:
                matchExpressions:
                - key: GPU
                  operator: Exists
      containers:
      - name: node-affinity-preferred
        image: busybox:stable
        args:
        - /bin/sh
        - -c
        - sleep 3600
        resources: 
          requests:
            cpu: 1' > node-affinity-preferred.yaml

kubectl apply -f node-affinity-preferred.yaml

# 有 GPU label 的 Node 清單
kubectl get nodes -l GPU

NAME                  STATUS   ROLES    AGE   VERSION
ithome-2024-worker2   Ready    <none>   92m   v1.30.0

# 看 pod 是否被分配到 zone=local-a 的 Node
kubectl get pod -o wide | grep node-affinity-preferred

ode-affinity-preferred-6bddbf654-78xfp   1/1     Running   0          103s   10.244.3.3   ithome-2024-worker2   <none>           <none>
```

來介紹幾個重要的屬性
- `preferredDuringSchedulingIgnoredDuringExecution`: 表示此 Pod 會優先部署到符合條件的 Node，若是符合條件的 Node 都不可用時，仍允許部署到其他 Node

- `weight`: 依照 weight 作為權重，優先將 Pod 部署到 weight 較大的條件

- `matchExpressions`: 定義篩選條件的區塊，依此例來說，就是找到有 `GPU` label 的 Node，有更多條件式用法可參考[官方文件](https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/#operators)

讀者能擴展這個 deployment 的 Pod 數量，看是否當 ithome-2024-worker2 資源滿載時，Pod 仍會被建立在其他 node
> kubectl scale deployment node-affinity-preferred --replicas 3

## 組合技：必須部署在 `zone=local-a` 的 Node 上，並且優先部署到有 GPU label 的 節點
同時使用 `requiredDuringSchedulingIgnoredDuringExecution` 與 `preferredDuringSchedulingIgnoredDuringExecution` 來完成這需求

```shell
echo 'apiVersion: apps/v1
kind: Deployment
metadata:
  name: node-affinity-required-and-preferred
spec:
  replicas: 6
  selector:
    matchLabels:
      app: node-affinity-required-and-preferred
  template:
    metadata:
      labels:
        app: node-affinity-required-and-preferred
    spec:
      affinity:
        nodeAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            nodeSelectorTerms:
            - matchExpressions:
              - key: zone
                operator: In
                values:
                - local-a
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 1
            preference:
              matchExpressions:
              - key: GPU
                operator: Exists
      containers:
      - name: node-affinity-required-and-preferred
        image: busybox:stable
        args:
        - /bin/sh
        - -c
        - sleep 3600
        resources: 
          requests:
            cpu: 1' > node-affinity-required-and-preferred.yaml

kubectl apply -f node-affinity-required-and-preferred.yaml

# 看 pod 是否分配到有 zone=local-a 的 Node，且優先部署在有 GPU label 的 Node
kubectl get pod -o wide

NAME                                                    READY   STATUS    RESTARTS   AGE   IP            NODE                  NOMINATED NODE   READINESS GATES
node-affinity-required-and-preferred-7b786c97f7-h498k   1/1     Running   0          6s    10.244.3.10   ithome-2024-worker2   <none>           <none>
node-affinity-required-and-preferred-7b786c97f7-vmsc5   1/1     Running   0          6s    10.244.2.7    ithome-2024-worker    <none>           <none>
node-affinity-required-and-preferred-7b786c97f7-vvj8q   1/1     Running   0          47s   10.244.3.7    ithome-2024-worker2   <none>           <none>
node-affinity-required-and-preferred-7b786c97f7-zhnx9   1/1     Running   0          27s   10.244.3.8    ithome-2024-worker2   <none>           <none>
node-affinity-required-and-preferred-7b786c97f7-zqv5k   1/1     Running   0          6s    10.244.3.11   ithome-2024-worker2   <none>           <none>
node-affinity-required-and-preferred-7b786c97f7-zz92g   1/1     Running   0          27s   10.244.3.9    ithome-2024-worker2   <none>           <none>
```

能看到大部分的 Pod 都被部署到有 GPU label 的 ithome-2024-worker2，當 ithome-2024-worker2 資源不足時，仍能部署到滿足 zone=local-a 條件的 ithome-2024-worker。

# 小結
今天我們介紹了三種將 Pod 調度到指定 Node 的方法：
1. NodeName
2. NodeSelector
3. Node Affinity

總結來說，Node Affinity 彈性較高，提供優先序等功能，可以讓 Pod 優先部署在符合需求的 Node 上；而 NodeSelector 則是滿足基本需求的簡單方法。

因此，建議優先使用 Node Affinity 或 NodeSelector，以降低服務中斷的風險，盡量避免使用 NodeName 的方式。

明天我們將繼續介紹 `Inter-Pod Affinity`，以滿足更多生產環境的需求。


# Refernce
- [kubernetes 官方文件](https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/)
- [HWCHIU 學習筆記 / k8s-assigning-pod](https://www.hwchiu.com/docs/2023/k8s-assigning-pod)
- [小信豬的部落格 / [Kubernetes] Assigning Pods to Nodes](https://godleon.github.io/blog/Kubernetes/k8s-Assigning-Pod-to-Nodes/)

[kind]: https://kind.sigs.k8s.io/