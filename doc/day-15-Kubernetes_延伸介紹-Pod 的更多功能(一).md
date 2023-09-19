# Day-15-Kubernetes_延伸介紹-Pod 的更多功能(一)

# 前言
我們已經學了一套 Kubernetes 上最常用的組合技
- Pod: 負責 運行 container 
- Deployment: 負責 管理多副本的 Pod 與 滾動更新 
- Service: 負責 服務發現 與 負載均衡
- Ingress: 管理對外開放存取的端點與轉發規則
- ConfigMap/Secret: 負責存放配置或機敏資訊
  
透過這些 Kubernetes 組件，我們已經能部署一套**多副本**且有**基本自我修復能力**的應用程序，且能進行**負載均衡**分散流量與能**對外開放存取**並進行路由管理。

這幾天來深入一點了解 Pod 的生命週期 與 更多功能與特性，來讓我們的服務更強健。
- Pod 的生命週期 和 重啟策略(RestartPolicy)
- 資源(CPU/Memory)管理
- QoS (Quality of Service)
- Liveness, Readiness and Startup Probes
- 優雅關閉 (graceful shutdown)


# Pod 的生命週期 和 重啟策略(RestartPolicy)
Kubernetes 中的 Pod 是容器化應用程序的基本部署單位，具有一個非常重要且獨特的特性：它是一個容器的臨時載體，可以隨時被替換，這種特性使得 Pod 成為支持高可用性和故障恢復的強大工具。

透過瞭解 Pod 的生命週期，能幫助我們去配置適合的 重啟策略(RestartPolicy)

## Pod 的生命週期 
隨著 Pod 被創建，會經歷以下階段
| 階段         | 描述                                                                                     |
|--------------|------------------------------------------------------------------------------------------|
| Pending | Pod 已被 Kubernetes 系統接受，但有一個或者多個容器尚未創建亦未運行。此階段包括等待 Pod 被調度的時間和透過網路下載鏡像的時間。 |
| Running | Pod 已經綁定到某個節點，Pod 中所有的容器都已被創建。至少有一個容器仍在運行，或者正處於啟動或重啟狀態。 |
| Succeeded | Pod 中的所有容器都已成功終止，並且不會再重啟。 |
| Failed | Pod 中的所有容器都已終止，並且至少有一個容器是因為失敗終止。也就是說，容器以非 0 狀態退出或者被系統終止。 |
| Unknown | 因為某些原因無法取得 Pod 的狀態。這種情況通常是因為與 Pod 所在主機通信失敗。 |

## 重啟策略(RestartPolicy)
假設我們運行的是 Web 服務，會預期服務需要保持 `Running` 狀態，隨時都能處理用戶端的請求，但若受到海量請求導致或其他原因導致 Web 服務崩潰，此時 Pod 狀態會進入 `Failed` 狀態。此時我們通常會希望服務能自動重啟，減少服務崩潰造成的影響時間。

利用 重啟策略(RestartPolicy) 我們能指定 Pod 在什麼情況下要重啟
- `Always`（始終）： 這是默認的重啟策略。當容器終止時，Kubernetes 將始終嘗試重新啟動容器，無論是因為錯誤、退出碼還是其他原因。這對於應用程序要求高可用性的情況很有用。
> 透過 Deployment 管理 Pod 時，只能使用 `Always`
- `OnFailure`（僅在失敗時）： 在這種策略下，當容器因錯誤或非零退出碼而終止時，Kubernetes 才會嘗試重新啟動容器。這可用於應對應用程序中的臨時錯誤或容器崩潰的情況。

- `Never`（永不）： 在這種策略下，Kubernetes 不會嘗試重新啟動容器，無論容器如何終止。這通常用於一次性工作或容器不應自動重啟的情況。

## 測試 重啟策略(RestartPolicy)
我們來測試一下不同 重啟策略(RestartPolicy) 遇到容器終止時的行為

部署各種 RestartPolicy 的 Pod
```
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: restart-always-exit-0-pod
spec:
  restartPolicy: Always
  containers:
  - name: always-container
    image: busybox
    command: ["sh", "-c", "echo 'Container started'; sleep 5; echo 'exit 0(success)'; exit 0"]

---

apiVersion: v1
kind: Pod
metadata:
  name: restart-always-exit-1-pod
spec:
  restartPolicy: Always
  containers:
  - name: on-failure-container
    image: busybox
    command: ["sh", "-c", "echo 'Container started'; sleep 5; echo 'Forcing an error'; exit 1"]

---

apiVersion: v1
kind: Pod
metadata:
  name: restart-on-failure-exit-0-pod
spec:
  restartPolicy: OnFailure
  containers:
  - name: on-failure-container
    image: busybox
    command: ["sh", "-c", "echo 'Container started'; sleep 5; echo 'exit 0(success)'; exit 0"]

---

apiVersion: v1
kind: Pod
metadata:
  name: restart-on-failure-exit-1-pod
spec:
  restartPolicy: OnFailure
  containers:
  - name: on-failure-container
    image: busybox
    command: ["sh", "-c", "echo 'Container started'; sleep 5; echo 'Forcing an error'; exit 1"]

---

apiVersion: v1
kind: Pod
metadata:
  name: restart-never-exit-0-pod
spec:
  restartPolicy: Never
  containers:
  - name: on-failure-container
    image: busybox
    command: ["sh", "-c", "echo 'Container started'; sleep 5; echo 'exit 0(success)'; exit 0"]

---

apiVersion: v1
kind: Pod
metadata:
  name: restart-never-exit-1-pod
spec:
  restartPolicy: Never
  containers:
  - name: on-failure-container
    image: busybox
    command: ["sh", "-c", "echo 'Container started'; sleep 5; echo 'Forcing an error'; exit 1"]
EOF
```

執行後會產生 6 個 pod
| 名稱                          | 重啟策略(RestartPolicy) | 容器行為                 | 預期行為  |
| ----------------------------- | ----------------------- | ------------------------ | ---------- |
| restart-always-exit-0-pod     | Always                  | 返回成功代碼 (exit 0)     | 重啟       |
| restart-always-exit-1-pod     | Always                  | 返回失敗代碼 (exit 1)     | 重啟       |
| restart-on-failure-exit-0-pod | OnFailure               | 返回成功代碼 (exit 0)     | 不重啟     |
| restart-on-failure-exit-1-pod | OnFailure               | 返回失敗代碼 (exit 1)     | 重啟       |
| restart-never-exit-0-pod      | Never                   | 返回成功代碼 (exit 0)     | 不重啟     |
| restart-never-exit-1-pod      | Never                   | 返回失敗代碼 (exit 1)     | 不重啟     |

我們透過上表與 `kubectl get pod 檢視預期行為是否相同
```
NAME                               READY   STATUS             RESTARTS       AGE
restart-always-exit-0-pod          0/1     CrashLoopBackOff   6 (102s ago)   8m27s
restart-always-exit-1-pod          0/1     CrashLoopBackOff   6 (2m7s ago)   8m27s
restart-never-exit-0-pod           0/1     Completed          0              8m27s
restart-never-exit-1-pod           0/1     Error              0              8m27s
restart-on-failure-exit-0-pod      0/1     Completed          0              8m27s
restart-on-failure-exit-1-pod      0/1     CrashLoopBackOff   6 (107s ago)   8m27s
```

每次 Pod 重啟時，該 Pod 的 `RESTARTS` 都會遞增 1，我們能從這看出 重啟策略(RestartPolicy) 
> 每次容器退出時時，都會延遲幾秒後再執行重啟，延遲時間是依據指數退避的方式計算(10秒，20秒，40秒…)，最長為 5 分鐘。若 Pod 運行超過 10 分鐘未出現問題，則會重置該延遲時間。

以下是我簡單的選擇方式
- Web / API 服務: `Always`
    > 通常 Web 服務這種希望自動重啟的都建議使用 Deployment，Deployment 預設為 `Always`(也只能使用 `Always`)，能再 Web 服務崩潰時，盡快重啟服務。
- 單次/定時作業: `OnFailure` or `Never`
    > 使用 [Jobs](https://kubernetes.io/docs/concepts/workloads/controllers/job/) 或 [CronJob](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/) 來管理 Pod，並依據作業是否可重複執行來配置  `OnFailure` or `Never`

# 資源(CPU / Memory)管理
在 Kubernetes 中，資源請求（Resource Requests）和資源限制（Resource Limits）是用於定義 Pod 對 CPU 和內存資源的需求和限制的重要概念。它們有助於確保 Pod 在 Kubernetes 集群中有效地使用資源，防止過度消耗資源或過度限制 Pod 的運行。

若我們不對 Pod 設定資源限制（CPU / Memory），當某個 Pod 中的應用程序出現記憶體洩漏的問題時，可能會導致該 Worker Node 的 Memory 被耗盡。這不僅破壞了容器之間應有的隔離性，還可能對其他服務的正常運作造成影響。

我們能透過 `resources` 屬性來配置
```
resources: # 此 Pod 的資源請求/限制配置
  requests: # 資源請求
    cpu: "100m"     # 請求至少 0.1 core CPU
    memory: "100Mi" # 請求至少約 104 MB memory
  limits:   # 資源限制
    cpu: "1"        # 最多使用 1 core CPU
    memory: "200Mi" # 最多使用約 209 MB memory
```
### CPU 單位
Kubernetes 中 CPU 單位為 milli-core，用正整數與正數小數表示，最小單位為 1m。   
- 使用後綴(m)時：每個單位代表 1 milli-core 
  - 1m 表示 0.001 core CPU。  
  - 1000m 表示 1 core CPU。   
- 不使用後綴(m)時：每個單位代表 1 core，
   - 0.5 表示 0.5 core CPU。  
   - 1 表示 1 core CPU。   

### Memory 單位
Memory 資源的基本單位是位元組（byte）。您可以使用以下其中一個後綴，將內存表示為純整數或定點整數：E、P、T、G、M、K、Ei、Pi、Ti、Gi、Mi、Ki。例如，下面是一些近似相同的值：
```
128974848、129e6、129M、123Mi
```

- 資源(CPU/Memory)管理
- QoS (Quality of Service)
- 優雅關閉 (graceful shutdown)

## 測試 資源(CPU / Memory)管理
### 前置作業
因需要觀察 pod CPU / Memory 使用量，需要安裝 `metrics-server` 才能使用 `kubectl top`

安裝 `metrics-server`
```
# 建立臨時目錄
mkdir metrics-server

# 產生 metrics-server yaml
echo 'bases:
  - https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

patchesJson6902:
  - target:
      version: v1
      kind: Deployment
      name: metrics-server
      namespace: kube-system
    patch: |-
      - op: add
        path: /spec/template/spec/containers/0/args/-
        value: --kubelet-insecure-tls' > metrics-server/kustomization.yaml

# deploy metrics-server
kubectl apply -k metrics-server

# 檢查安裝是否成功
kubectl get apiservices | grep metrics

# 出現結果包含 `v1beta1.metrics.k8s.io` 代表安裝成功
```

### 測試

#### **符合指定 memory 請求與限制**
container 會使用 符合配置範圍中的(100Mi < 使用量 < 200Mi) 的 Memory
```
kubectl create ns mem-example

cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: memory-demo
  namespace: mem-example
spec:
  containers:
  - name: memory-demo-ctr
    image: polinux/stress
    resources:
      requests:
        memory: "100Mi"
      limits:
        memory: "200Mi"
    command: ["stress"]
    args: ["--vm", "1", "--vm-bytes", "150M", "--vm-hang", "1"]
EOF
```

檢查 Pod 狀態 與 Memory 使用量
```
kubectl get pod memory-demo --namespace=mem-example
kubectl top pod memory-demo --namespace=mem-example
```
Memory 使用量符合配置(100Mi < 使用量 < 200Mi)
```
NAME          CPU(cores)   MEMORY(bytes)   
memory-demo   84m          162Mi     
```

#### **使用超過 memory 限制**
container 會使用 250MB Memory，超過 limit 配置的100Mi
```
kubectl create ns mem-example

cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: memory-demo-2
  namespace: mem-example
spec:
  containers:
  - name: memory-demo-2-ctr
    image: polinux/stress
    resources:
      requests:
        memory: "50Mi"
      limits:
        memory: "100Mi"
    command: ["stress"]
    args: ["--vm", "1", "--vm-bytes", "250M", "--vm-hang", "1"]
EOF
```

檢查 Pod 狀態
```
kubectl get pod memory-demo --namespace=mem-example
```
會看到 Pod STATUS 為 `OOMKilled`，因為使用的 Memory 超過 limit 限制
```
NAME            READY   STATUS      RESTARTS   AGE
memory-demo-2   0/1     OOMKilled   0          4s
```

#### **使用超過 cpu 限制**
```
kubectl create ns cpu-example

cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: cpu-demo
  namespace: cpu-example
spec:
  containers:
  - name: cpu-demo-ctr
    image: alexeiled/stress-ng
    resources:
      limits:
        cpu: "1"
      requests:
        cpu: "0.5"
    args:
    - --cpu
    - "2"
EOF
```

檢查 Pod 狀態 與 CPU 使用量
```
kubectl get pod cpu-demo --namespace=cpu-example
kubectl top pod cpu-demo --namespace=cpu-example
```
能看到雖然容器嘗試使用 2 core CPU，但仍只獲得 1 core(limit 配置)
```
NAME       CPU(cores)   MEMORY(bytes)   
cpu-demo   1000m        9Mi             
```

## 問題: 為什麼容器嘗試使用超過 limit 的 Memory 時，Pod 會被強制終止，而嘗試使用超過 limit 的 CPU 不會被強制關閉?
主要原因在於記憶體和 CPU 在系統資源管理上的不同特性
- CPU : 當一個進程或容器使用了它被分配的CPU時間片，CPU可以被回收並分配給其他等待的任務。這有助於確保公平的CPU資源分配，防止某個進程壟斷CPU資源。
- Memory : 不同於CPU，內存的分配通常不會被回收並分配給其他任務。當一個進程或容器分配了一定數量的內存，這些內存資源通常只有在該進程或容器終止後才能被釋放。

所以當 Memory 使用超過 limit 時，只能透過強制終止才能限制 Memory 使用量。

# 總結
今天介紹了 **Pod 的生命週期 和 重啟策略(RestartPolicy)**
與 **資源(CPU/Memory)管理**，都是能對服務穩定性的功能項，希望能幫助讀者配置出一個穩固的服務。

明天會繼續介紹
- QoS (Quality of Service)
- Liveness, Readiness and Startup Probes
- 優雅關閉 (graceful shutdown)

# 參考
- [Assign Memory Resources to Containers and Pods](https://kubernetes.io/zh-cn/docs/tasks/configure-pod-container/assign-memory-resource/)
- [Assign CPU Resources to Containers and Pods](https://kubernetes.io/docs/tasks/configure-pod-container/assign-cpu-resource/)