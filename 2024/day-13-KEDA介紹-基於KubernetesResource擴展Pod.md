
# Day-13 KEDA 介紹 - 基於 Kubernetes Resource 擴展 Pod

# 前言
前兩天 Demo 了透過 Prometheus、Message Queue 配置自動擴展策略，但有時候我們希望使用更簡單的策略，例如 **依據上游 Pod 的數量來進行擴展**，理論上 上游的 Pod 進行 scale out 時，下游 Pod 可能也會收到更多流量，故若是上游服務的自動擴展已經夠完整時，作為下游的服務有時候能參考上游 Pod 數量來進行擴展。

# 環境準備
需要再 Kubernetes 中進行以下準備，來模擬上下游服務
- 部署 nginx 模擬上游服務，並有 2 個 Pod 副本
- 部署 redis 模擬下游服務，並有 1 個 Pod 副本

## 部署 nginx 模擬上游服務，並有 2 個 Pod 副本
```yaml
# nginx.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx
spec:
  selector:
    matchLabels:
      app: nginx
  replicas: 2
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
      - name: nginx
        image: nginx:latest
```
部署 nginx
```shell
kubectl apply -f nginx.yml
```

## 部署 redis 模擬下游服務，並有 1 個 Pod 副本
```yaml
# redis.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis
spec:
  selector:
    matchLabels:
      app: redis
  replicas: 1
  template:
    metadata:
      labels:
        app: redis
    spec:
      containers:
      - name: redis
        image: redis:latest
```
部署 nginx
```shell
kubectl apply -f redis.yaml
```

這時能看到 Kubernetes 中有 3 個 Pod 在運行
- 2 個 nginx Pod
- 1 個 redis Pod
```shell
kubectl get pod
NAME                     READY   STATUS    RESTARTS   AGE
nginx-7584b6f84c-5bqlm   1/1     Running   0          8m7s
nginx-7584b6f84c-bmhhv   1/1     Running   0          13m
redis-644585c74b-8v5rp   1/1     Running   0          13m
```
# 配置 KEDA
來設定 `ScaledObject` 將 nginx Pod 數量作為擴展依據

## 配置 ScaledObject
```yaml
# keda-scaled-object.yml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: keda-kubernetes-workload-demo
  namespace: ithome
spec:
  minReplicaCount: 1
  maxReplicaCount: 4
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: redis
  triggers:
  - type: kubernetes-workload
    metadata:
      podSelector: 'app=nginx'
      value: '2'
```
能看到這配置檔單純許多，主要欄位為
- `triggers` 
  - `type` : 指定為 `kubernetes-workload`，此時 KEDA 透過 kubernetes cluster 的 `kube-apiserver` 監聽 Pod 的數量
  - `podSelector`: 使用 labelSelector 選擇監聽哪些 Pod。 (可透過 `,` 指定多組條件)
  - `value`: redis 與 nginx 之間的關係
    - 計算公式： `relation = (pods which match selector) / (scaled workload pods)`，當 `relation` 超過 `value` 配置值時，會擴展 redis Pod。   

      故當 nginx Pod scale out 到 3 個時，relation 為 `3`，會擴展 redis 的 Pod。
      - relation value: `3 = 3(nginx replica number) / 1(redis replica number)`

部署 ScaledObject
```shell
kubectl apply -f keda-scaled-object.yml
```

## 模擬上游擴展 Pod
透過 `kubectl scale` 來模擬上游服務 (nginx) 承受到更多流量，觸發了 Pod 擴展
```shell
kubectl scale deployment nginx --replicas 3
```
不久後，就能看到 redis 也從 1 個 Pod 擴展到 2 個
```shell
kubectl get pod

NAME                     READY   STATUS              RESTARTS   AGE
nginx-7584b6f84c-5bqlm   1/1     Running             0          57m
nginx-7584b6f84c-bmhhv   1/1     Running             0          63m
nginx-7584b6f84c-lqzrx   1/1     Running             0          13s
redis-644585c74b-8v5rp   1/1     Running             0          63m
redis-644585c74b-wc2d4   0/1     ContainerCreating   0          2s
```

若 nginx 擴展到 5 個時，redis 也會擴展到 3個 Pod

```shell
kubectl scale deployment nginx --replicas 5

kubectl get pod

NAME                     READY   STATUS              RESTARTS   AGE
nginx-7584b6f84c-2gsrx   1/1     Running             0          17s
nginx-7584b6f84c-4nnx9   1/1     Running             0          17s
nginx-7584b6f84c-5bqlm   1/1     Running             0          59m
nginx-7584b6f84c-bmhhv   1/1     Running             0          65m
nginx-7584b6f84c-lqzrx   1/1     Running             0          118s
redis-644585c74b-8v5rp   1/1     Running             0          65m
redis-644585c74b-ct294   0/1     ContainerCreating   0          2s
redis-644585c74b-wc2d4   1/1     Running             0          107s
```

再來我們觀察是否會依據 nginx 縮容，一並減少 redis Pod

```shell
kubectl scale deployment nginx --replicas 2
```

這時因為大於 1 個 Pod 時的擴展機制其實是由 [HPA] 處理的，HPA 為了避免頻繁擴縮容，故會參考 5 分鐘以內的評估資料，故需要等 5 分鐘後才會減少 redis Pod
```shell
kubectl get pod

NAME                     READY   STATUS    RESTARTS   AGE
nginx-7584b6f84c-5bqlm   1/1     Running   0          73m
nginx-7584b6f84c-bmhhv   1/1     Running   0          79m
redis-644585c74b-8v5rp   1/1     Running   0          79m
```
> 📘 想了解更多 [HPA] 的行為可參考[官方文件](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/#configurable-scaling-behavior)
> 📘 `ScaledObject` 能透過 advanced block 調整 [HPA] 的行為，能參考 [KEDA 官方文件](https://keda.sh/docs/2.15/reference/scaledobject-spec/#overview)

# 小結
這三天我們透過 [KEDA] 來實現更多元的方式來配置自動擴展策略，不再受 [HPA] 預設只能依據 CPU / Memory，讓我們能依照服務特性來提高服務可用性。

明天會繼續介紹 Kubernetes 原生用來管理網路流量的 Resource: `NetworkPolicy`

# Refernce
- [KEDA]

[KEDA]: https://keda.sh/
[HPA]: https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/
[HPA (Horizontal Pod Autoscaling)]: https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/

[2023/day-29-Kubernetes 介紹-Pod 水平自動伸縮 (Horizontal Pod Autoscaler)]: https://ithelp.ithome.com.tw/articles/10336846

[kind]: https://kind.sigs.k8s.io/

[helm]: https://helm.sh/

[Kube-Prometheus]: https://prometheus-operator.dev/docs/getting-started/installation/