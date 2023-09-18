# Day-18-Kubernetes_介紹-Namespace, Resource Quotas 與 Limit Range

# 前言
前幾天介紹了幾個 Pod 常用的功能，其中包含了 Resource 配置，今天會介紹 Namespace, ResourceQuota, LimitRange，偏 Kubernetes 維護團隊會使用的功能，主要用來作為 Resource 配置的管理，雖然主要是管理者在使用的功能，不過開發者若知道有這些功能，當遇到 Resource 使用問題時，也能比較容易意識到可能是這個配置設定上，需要找維護團隊溝通調整。

# 什麼是 Namespace 
Namespace 是 Kubernetes 中的一個抽象概念，提供一種機制，讓管理者能將 Cluster 內的資源(如 Pod) 分群管理 與 實施隔離措施，使 Cluster 管理者更容易分配系統資源與提升安全性。

簡單來說，我們能在 Kubernetes 中建立屬於你團隊的 Namespace，你建立的 Pod、Deployment、Service..等資源，都會歸屬該 Namespace。

這樣有什麼好處?
- 權限控制: 搭配 [RBAC]，將能操作該 Namespace 資源的權限只發配給團隊成員，避免外人誤操作時，影響服務運作。
- 資源管理: 搭配 [Resource quotas] 與 [Limit Range]，避免單一團隊耗盡 Kubernetes cluster 系統資源 (CPU/Memory..等等)
- 安全性: 搭配 [Network policy]，限制調用服務的來源
- 更容易管理: 透過 Namespace 將環境或團隊進行分類，能更易於監控與維護。

![ns](https://stacksimplify.com/course-images/azure-kubernetes-service-namespaces-2.png)
圖檔來自 [Kubernetes Namespaces - Imperative using kubectl][https://stacksimplify.com/azure-aks/azure-kubernetes-service-namespaces-imperative/
]

# 操作 Namespace
能透過 `kubectl` 來操作 Namespace
```
# 建立 namespace
kubectl create namespace demo

# 查詢 namespace
kubectl get namespace
```
返回結果
![create-namespace](https://cdn.staticaly.com/gh/YihongGao/picx-images-hosting@master/20230915/截圖-2023-09-18-下午6.37.06.2qnpmh2ay5a0.webp)

大部分 `kubectl` 操作都能加上 `-n` 或 `--namespace` 參數來指定 namespace
```
# 在 demo Namespace 中建立 Pod
kubectl run nginx --image=nginx --namespace=demo

# 查詢 demo Namespace 中的所有 Pod
kubectl get pod --namespace=demo

```
返回結果
```
NAME    READY   STATUS              RESTARTS   AGE
nginx   1/1     Running   0          6s
```

# Resource Quotas 與 Limit Range
先前介紹過 Pod 的**資源(CPU / Memory)管理**，能透過 `resources.requests` 與 `resources.limits` 來要求該 container 最低與最高使用的資源量。
但整個 Cluster 可使用的資源是有限的(依據硬體資源或預算)，會希望能控制每個團隊使用的資源量，避免單一服務或團隊過度的要求資源，造成資源緊張或衍生額外的成本。

Pod 要求大量資源的範例
```
apiVersion: v1
kind: Pod
metadata:
name: nginx
namespace: demo
spec:
containers:
- name: nginx
    image: nginx
    resources:
    requests:
        cpu: 4
        memory: "12Gi"
    limits:
        cpu: 12
        memory: "32Gi"
```

假設這個容器不需要這麼大量的資源，但確要求過多的資源，就會排擠到其他團隊可用的資源，甚至影響到 Pod 無法進行 scale out，而無法應付高流量。

## Resource Quotas
而 `Resource Quotas` 能幫助管理者，為每個 Namespace 分配指定額度的資源量作為上限，避免單一團隊過分要求資源，當資源不足需擴充時，也需要管理者調整 Resource Quotas 配額，讓管理者能有更多機會去了解資源被使用到哪裡。

```
# 建立 Resource Quotas
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ResourceQuota
metadata:
  name: compute-resources
  namespace: demo #指定 demo namespace 的 Resource Quotas
spec:
  hard:
    requests.cpu: "1"
    requests.memory: 1Gi
    limits.cpu: "2"
    limits.memory: 2Gi
EOF

# 查詢 Resource Quotas
kubectl get resourcequotas -n demo
```

返回結果
```
NAME                AGE   REQUEST                                     LIMIT
compute-resources   58s   requests.cpu: 0/1, requests.memory: 0/1Gi   limits.cpu: 0/2, limits.memory: 0/2Gi
```
這裡建立了一個 `Resource Quotas` 在 demo namespace，限制 demo namespace 中的**所有 Pod請求的資源，加總後不可超過其限制**
> 依此例子來說，demo namespace 中的所有 Pod 
> - `requests.cpu` **加總後**不可超過 1
> - `requests.memory` **加總後**不可超過 1Gi
> - `limits.cpu` **加總後**不可超過 2
> - `limits.memory` **加總後**不可超過 2Gi

我們建立一個超過限制的 Pod 試試看
```
cat <<EOF | kubectl apply -f - 
apiVersion: v1
kind: Pod
metadata:
  name: fat-nginx
  namespace: demo
spec:
  containers:
  - name: apps
    image: nginx:latest
    resources:
      requests:
        cpu: "4" 
        memory: "12Gi"
      limits:
        cpu: "12" 
        memory: "32Gi"
EOF
```
返回結果
```
Error from server (Forbidden): error when creating "STDIN": pods "fat-nginx" is forbidden: exceeded quota: compute-resources, requested: limits.cpu=12,limits.memory=32Gi,requests.cpu=4,requests.memory=12Gi, used: limits.cpu=0,limits.memory=0,requests.cpu=0,requests.memory=0, limited: limits.cpu=2,limits.memory=2Gi,requests.cpu=1,requests.memory=1Gi
```
能看到建立失敗的錯誤訊息中有額外三個資訊，`requested`, `used`, `limited`，分別代表此次請求的資源量，該 namespace 已使用的資源量，此 namespace 最大的資源量限制，提示你已經超過 `Resource Quotas` 的限制。

## Limit Range
我們能透過 `Resource Quotas` 來限制 Namespace 維度的資源使用量，但仍有可能資源 整個 Namespace 的資源 被單一或少數 Pod 一次耗盡。
這時候我們能透過 `LimitRange` 來限制 Pod 或 Container 維度的資源要求範圍。
也能定義預設的 `resources.requests` 與 `resources.limits`，避免無上限的使用系統資源。

```
# 建立 LimitRange
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: LimitRange
metadata:
  name: compute-resource
  namespace: demo
spec:
  limits:
  - default: # 定義預設 limits
      cpu: 500m
      memory: 500Mi
    defaultRequest: # 定義預設 requests
      cpu: 100m
      memory: 10Mi
    max: # max 和 min 定義該資源的範圍
      cpu: "2"
      memory: 4Gi
    min:
      cpu: 100m
      memory: 10Mi
    type: Container
EOF

# 查詢 LimitRange
kubectl get limitranges -n demo
```
返回結果
```
NAME               CREATED AT
compute-resource   2023-09-18T12:20:14Z
```

我們建立了一個 `LimitRange` 在 demo namespace 中，於該 namesoace 建立 Pod 時，會有以下檢查與影響
- 提供預設值
  - 若 container 沒有指定 `resource.requests.cpu`時，預設為 100m
  - 若 container 沒有指定 `resource.requests.memory`時，預設為 10Mi
  - 若 container 沒有指定 `resource.limits.cpu`時，預設為 500m
  - 若 container 沒有指定 `resource.limits.memory`時，預設為 500Mi
- 檢查資源範圍
  - 檢查 `resource.requests.cpu` 與 `resource.limits.cpu` 是否介於 100m ~ 2(等同2000m)
  - 檢查 `resource.requests.memory` 與 `resource.limits.memory` 是否介於 10Mi ~ 4Gi
  
我們來嘗試一下提供預設值的功能
```
kubectl run default-resource-pod --image nginx -n demo

# 查詢該 Pod `resources.requests` 的配置是否使用了預設值
kubectl get pod default-resource-pod -n demo -o custom-columns="POD:.metadata.name,CPU:spec.containers[*].resources.requests.cpu,MEMORY:spec.containers[*].resources.requests.memory,LIMITS_CPU:spec.containers[*].resources.limits.cpu,LIMITS_MEMORY:spec.containers[*].resources.limits.memory"
```

```
POD                    CPU    MEMORY   LIMITS_CPU   LIMITS_MEMORY
default-resource-pod   100m   10Mi     500m         500Mi
```
能看到即使我 create Pod 時，沒指定任何一個 `resources.requests`，當 Pod 建立時，會自動配置依據 `LimitRange` 來幫 Pod 配置預設值。


我們建立一個超過限制的 Pod 試試看
```
cat <<EOF | kubectl apply -f - 
apiVersion: v1
kind: Pod
metadata:
  name: fat-nginx
  namespace: demo
spec:
  containers:
  - name: apps
    image: nginx:latest
    resources:
      requests:
        cpu: "4" 
        memory: "12Gi"
      limits:
        cpu: "12" 
        memory: "32Gi"
EOF
```
返回結果
```
Error from server (Forbidden): error when creating "STDIN": pods "fat-nginx" is forbidden: [maximum memory usage per Container is 4Gi, but limit is 32Gi, maximum cpu usage per Container is 2, but limit is 12]
```
提示了 cpu request 與 limit 都超過了 `LimitRange` 的限制。
> 會逐個條件檢查 `LimitRange` 的範圍，當 cpu 檢查通過後，會在檢查 memory

# 總結
今天我們了解了 Namespace 能幫助我們實現資源隔離，提升安全性。並透過 
`Resource Quotas` 與 `Limit Range` 分配 與 保護運算資源，讓管理者更容易維護 Kubernetes。
希望開發者了解這些機制後，遇到問題時能更快的排除，甚至能提供建議給管理者一起保護系統運作。

[Resource quotas]: https://kubernetes.io/docs/concepts/policy/resource-quotas/
[RBAC]: https://kubernetes.io/docs/reference/access-authn-authz/rbac/
[Network policy]: https://kubernetes.io/docs/concepts/services-networking/network-policies/
[Limit Range]: https://kubernetes.io/docs/concepts/policy/limit-range/