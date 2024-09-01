
# Day-04-Kubernetes Architecture 介紹 - 當建立 Pod 時，發生了什麼(一)

# 前言
前兩天我們更認識了 Kubernetes 的核心組件，今天我們要來聊聊，當你要求 Kubernetes 建立 Pod 的時候，Kubernetes 中到底發生了什麼事情，才能把 Pod 建立出來，希望透過瞭解這個過程，再讀者開發或維護時更流暢安心。

# Kubernetes Cluster Architecture
![Archtitecture](https://kubernetes.io/images/docs/kubernetes-cluster-architecture.svg)
圖檔來源 [Kubernetes 官方文件](https://kubernetes.io/docs/concepts/architecture/)

# kubectl
先快速介紹當你使用 `kubectl` 時，kubectl 這個 command line tool 做了什麼。

使用 `kubectl` 指令時，會在 Client 端進行以下任務
1. 基本檢核：將明確不會成功的請求再 Client 返回錯誤訊息，減少 kube-apiserver 壓力。    
    kubectl 會將可用的資源版本規範，使用 OpenAPI 格式快取在本地 `~/.kube/cache` 目錄中，當使用 `kubectl` 操作時會使用它們進行檢查。

2. 客戶端身份憑證：於本地找尋身分憑證，用於後續提供給 kube-apiserver 進行身份驗證，找尋優先序如下
    - 命令列參數：若使用 kubectl 指令時明確指定了憑證（例如 --kubeconfig、--certificate-authority、--client-certificate、--client-key 等）時， kubectl 會優先使用這些參數中指定的憑證。

    - 環境變數：若設置了 KUBECONFIG 環境變數，kubectl 會使用該變數指定的配置檔案路徑來尋找憑證。

    - 預設的 kubeconfig file：如果沒有設置 KUBECONFIG 環境變數，kubectl 會使用位於 `~/.kube/config` 的預設配置檔案來查找憑證。
    
3. 將指令封裝成 Http 請求：真正向 `kube-apiserver` 發出請求
    
從這些任務能發現，如同 [Day-02-Kubernetes Architecture 介紹 - Control Plane] 的介紹，我們知道 **要操作 Kubernetes 資源**，都必須要透過 `kube-apiserver` 提供的 RESTful API，當我們使用 `kubectl` 操作時也是如此。

# 建立 Pod 的流程
## 1. Http 請求從 kubectl 送出 (in Client)
![https://miro.medium.com/v2/resize:fit:1400/format:webp/0*01aDK3UDdeWxxM5V.png](https://miro.medium.com/v2/resize:fit:1400/format:webp/0*01aDK3UDdeWxxM5V.png)
圖檔來至: [itnext.io/what-happens-when-you-create-a-pod-in-kubernetes](https://itnext.io/what-happens-when-you-create-a-pod-in-kubernetes-6b789b6db8a8)

如同先前說明的，kubectl 會透過 OpenAPI 格式找到適合的 API enpint 並進行格式校驗後，將 身份憑證 與 操作資源 的 payload 封裝成 Http Request 送往 `kube-apiserver` 

## 2. 認證 和 授權、Admission controllers  (kube-apiserver)
![https://miro.medium.com/v2/resize:fit:1400/format:webp/0*tYNAp-JiOoF7-frD.png](https://miro.medium.com/v2/resize:fit:1400/format:webp/0*tYNAp-JiOoF7-frD.png)
圖檔來至: [itnext.io/what-happens-when-you-create-a-pod-in-kubernetes](https://itnext.io/what-happens-when-you-create-a-pod-in-kubernetes-6b789b6db8a8)

當 `kube-apiserver` 收到 Http Request 請求，會從中取得身份憑證，並驗證是否為此 Cluster 的合法用戶(認證 Authentication)，並檢查此用戶是否有操作該資源的權限(授權 Authorization)。

當通過 認證(Authentication) 與 授權(Authorization) 兩個校驗後，還需要通過一關 `Admission controllers` 的檢查。
簡單來說 `Admission controllers` 是一串處理鏈，具備兩種能力
- 調整請求資源的值
- 檢查請求資源的值是否正確，當有檢核不通過時，會返回此請求，中斷此操作。

以下列出幾個常見的 `Admission controllers`
- ResourceQuota：   
    當 Pod 請求的計算資源超過 Namespace 中 [ResourceQuota] 的配置，則中斷此請求
- LimitRanger：
    - 當 Pod 未指定計算資源時，給予配置中的預設值
    - 當 Pod 指定的計算資源超過 [LimitRanger] 配置時，則中斷此請求

## 3. 將資料持久化到 etcd (kube-apiserver)
終於通過層層檢查，讓建立 Pod 的請求內容，送到了 Kubernetes 的資料庫：`etcd`。
然而 Pod 尚未真的被建立出來，而是處於 `Pending` 的狀態，代表 Scheduler 尚未幫這個 Pod 找到適合的家。
> 📘 關於 Pod 的狀態機，能參考[官方文件](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-phase)

## 4. 幫 Pod 找適合的家 (scheduler)
![https://miro.medium.com/v2/resize:fit:1400/format:webp/0*vQ-KQONXaQ_t4EQ2.png](https://miro.medium.com/v2/resize:fit:1400/format:webp/0*vQ-KQONXaQ_t4EQ2.png)
圖檔來至: [itnext.io/what-happens-when-you-create-a-pod-in-kubernetes](https://itnext.io/what-happens-when-you-create-a-pod-in-kubernetes-6b789b6db8a8)

當 scheduler 從 kube-apiserver 發現有狀態處於 Pending 的 Pod 時，它會進行兩個主要階段的操作：
過濾：scheduler 會依據 Pod 的需求(Affinity 和 anti-Affinity 等配置)和 Node 的資源狀況，過濾出一個符合條件的 Node 清單。
評分：對於篩選出的 Node，scheduler 會根據多種因素（如資源利用率）進行評分，然後選出得分最高的 Node。
最後，scheduler 會使用 kube-apiserver 的 API 將 Pod 與選中的 Node 綁定（Binding）。
> 📘 這裡的 綁定(Binding) 是指 scheduler 更新 Pod spec 中的 NodeName 欄位，scheduler 並不是實際建立 Pod 的組件。

到目前為止，這個 Pod 資訊仍只是存在 etcd 中的一筆資料而已，尚未有任何 container 被這個操作給啟動。

## 小結
今天介紹了 建立 Pod 旅程的前半段，這些事件大多都發生在 Control Plane 中
- 從 Client 端使用 `kubectl` 指令送出建立 Pod 的請求到 `kube-apiserver`
- `kube-apiserver` 中進行 認證、授權、Admission controllers 校驗，最終持久化到 etcd。
- `scheduler` 依據調度策略找到最適合該 Pod 的 Node。

明天會繼續介紹，建立 Pod 旅程的後半段，關於 worker node 的組件如何接手處理這筆 Pod 的資料。

# Refernce
- [itnext.io/what-happens-when-you-create-a-pod-in-kubernetes]
- [The birth story of the kubernetes pods]
- [kubectl 创建 Pod 背后到底发生了什么]

[itnext.io/what-happens-when-you-create-a-pod-in-kubernetes]:
https://itnext.io/what-happens-when-you-create-a-pod-in-kubernetes-6b789b6db8a8

[The birth story of the kubernetes pods]: https://sitereliability.in/deep-dive-the-birth-of-a-kubernetes-pod-understand-the-kubernetes-internals

[kubectl 创建 Pod 背后到底发生了什么]: https://icloudnative.io/posts/what-happens-when-k8s/

[ResourceQuota]:https://kubernetes.io/docs/concepts/policy/resource-quotas/

[LimitRanger]: https://kubernetes.io/docs/tasks/administer-cluster/manage-resources/memory-default-namespace/