
# Day-04-Kubernetes 如何建立出 Pod

# 前言
前兩天我們更認識了 Kubernetes 的核心組件，今天我們要來聊聊，當你透過 `kubectl` 指令要求 Kubernetes 建立 Pod 的時候，Kubernetes 中到底發生了什麼事情，來將 Pod 建立出來，希望透過瞭解這個過程，再讀者開發或維護時更有信心。


# Kubernetes Cluster Architecture
![Archtitecture](https://kubernetes.io/images/docs/kubernetes-cluster-architecture.svg)
圖檔來源 [Kubernetes 官方文件](https://kubernetes.io/docs/concepts/architecture/)

# kubectl
再開始介紹建立的 Pod 流程之前，需要先快速介紹當你使用 `kubectl` 時，他內部在做什麼。

使用 `kubectl` 指令時，會在 Client 端進行以下任務
1. 基本檢核：將明確不會成功的請求再 Client 返回錯誤訊息，減少 kube-apiserver 壓力。    
    kubectl 會將可用的資源版本規範，使用 OpenAPI 格式快取在本地 `~/.kube/cache` 目錄中，當使用 `kubectl` 操作時會使用它們進行檢查。

2. 客戶端身份憑證：於本地找尋身分憑證，用於後續提供給 kube-apiserver 進行身份驗證，找尋優先序如下
    - 命令列參數：若使用 kubectl 指令時明確指定了憑證（例如 --kubeconfig、--certificate-authority、--client-certificate、--client-key 等）時， kubectl 會優先使用這些參數中指定的憑證。

    - 環境變數：若設置了 KUBECONFIG 環境變數，kubectl 會使用該變數指定的配置檔案路徑來尋找憑證。

    - 預設的 kubeconfig file：如果沒有設置 KUBECONFIG 環境變數，kubectl 會使用位於 `~/.kube/config` 的預設配置檔案來查找憑證。
    
3. 將指令封裝成 Http 請求：真正向 `kube-apiserver` 發出請求
    
從這些任務能發現，如同 "Day-02-Kubernetes Architecture - Control Plane" 的介紹，我們知道 **要操作 Kubernetes 資源**，都必須要透過 `kube-apiserver` 提供的 RESTful API，當我們使用 `kubectl` 操作時也是如此。


# Refernce
https://itnext.io/what-happens-when-you-create-a-pod-in-kubernetes-6b789b6db8a8

https://sitereliability.in/deep-dive-the-birth-of-a-kubernetes-pod-understand-the-kubernetes-internals

https://icloudnative.io/posts/what-happens-when-k8s/