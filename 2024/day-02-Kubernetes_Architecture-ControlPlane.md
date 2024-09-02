
# Day-02-Kubernetes Architecture 介紹 - Control Plane
於 [2023/Day-06-Kubernetes 介紹] 簡單地說明了 Kubernetes Architecture，這次將 Kubernetes 的核心組件介紹得更詳細一點，包括這些組件的運作原理 與 組件之間如何協同運作的。


# Kubernetes 架構 (Kubernetes Cluster Architecture)
![Archtitecture](https://kubernetes.io/images/docs/kubernetes-cluster-architecture.svg)
圖檔來源 [Kubernetes 官方文件](https://kubernetes.io/docs/concepts/architecture/)

Kubernetes 作為一個容器編排工具，通常多個伺服器組成，伺服器中又分為兩大類別，各司其職並互相合作，以提供服務有高可用性、易於擴展、自我修復...等功能。
- Control Plane：負責管理和協調整個 Cluster 的運作。
- Worker Node：執行應用程式容器的實際工作負載。

## Control Plane 
![Control Plane](https://github.com/YihongGao/picx-images-hosting/raw/master/20240818/截圖-2024-08-18-下午1.34.04.70a9h17alm.webp)
Control Plane 是整個 Cluster 的大腦，由數個組件組成，透過這些組件來確保整個 Cluster 的正常運作。

### etcd
一個具備分散式和高可用特性的 Key-Value 資料庫，專門用來儲存 Cluster 中所有的配置與狀態數據。包括 Pod、Service、ConfigMap、Secrets 以及其他資源的狀態和配置。

若此資料庫損毀或不可用，整個 Cluster 將無法正常運作，因此定期備份這個資料庫是一項極為重要的任務。

### kube-apiserver
kube-apiserver 是 Kubernetes 中最重要的組件之一：
- **作為 Kubernetes 資源請求的單一入口** : 提供一系列 RESTful API，並且作為唯一能存取 etcd 的組件，kube-apiserver 是其他組件之間進行溝通與數據交換的核心樞紐。

- **身份與資料校驗**： 實現認證（Authentication）、授權（Authorization）與資料檢核等功能，保護 Cluster 的資料安全與正確性。

### kube-controller-manager
具體來說，它也是一個應用程式，該應用程式內運行著數個稱為 controller 的邏輯單位，每個 controller 負責**保持配置與 Cluster 狀態一致**，每個 controller 都對一個資源(ex: ReplicaSet、Service) 負責，透過 透過與 kube-apiserver 取得系統最新的狀態，並依照配置的期望狀態作出反應

### scheduler
scheduler 是 Kubernetes 中負責將 Pod 分配到適當的 Worker Node 上運行的核心組件，透過調度策略將 Pod 分配到適當的 Worker Node 上。

調度策略包含兩個主要步驟
- 過濾階段：將與期待狀態不符的 Node 排除，例如
  - 該 Node 的剩餘資源低於 Pod 的資源需求
  - 該 Node 不滿足 Pod 配置的 `nodeSelector`、`Node affinity`...等條件
- 評分階段：依據 Pod 的期望狀態 或 Node 的當前狀態進行評分，並嘗試將 Pod 分配到分數較高的 Node，例如
  - Pod 使用了 [Pod Topology Spread Constraints]，會優先挑選沒有運行相同副本的 Node 來運行新 Pod
  - 優先挑選資源使用率較低的 Node 來平衡資源壓力

### cloud-controller-manager
簡單來說，是負責 Kubernetes 與 Cloud Provider (GCP、AWS、Azure) 整合的組件，例如 [LoadBalancer type Service](https://kubernetes.io/docs/concepts/services-networking/service/#loadbalancer) 或是 [Gateway API](https://kubernetes.io/docs/concepts/services-networking/gateway/) 會在 Cloud Provider 的 plamform 建立出 LoadBalancer 來曝露服務。

# 小結
![https://github.com/YihongGao/picx-images-hosting/raw/master/20240901/截圖-2024-09-01-下午2.53.45.361ii5oz59.webp](https://github.com/YihongGao/picx-images-hosting/raw/master/20240901/截圖-2024-09-01-下午2.53.45.361ii5oz59.webp)
簡單來說，Control Plane 是一個已 kube-apiserver 作為中心的輻射狀結構，所有組件都透過與 kube-apiserver API 溝通來運作，也只有 kube-apiserver 能操作 etcd 資料庫，確保 etcd 的安全性與資料一致性。

而 kube-controller-manager 中運行的每個 Controller 發現它負責的資源與 Cluster 當前狀態不相符時，會操作 kube-apiserver API 嘗試將 Cluster 調整與配置相符。

比如 ReplicaSetController 發現某 ReplicaSet 的預期狀態是需要 3 個 Pod，而 Cluster 當前只運行 2 個 Pod 時，ReplicaSetController 將創建新的 Pod 來滿足期望狀態。

而 scheduler 發現新增的 Pod，該 Pod 尚未被指定到 Worker Node 時，scheduler 負責將 Pod 與適當的 Worker Node 綁定，由 kube-apiserver 後續與 Worker Node 交互，來完成把 Pod 部署到 Worker Node 的需求。

明天我們會繼續了解 Worker Node 的各個組件，與他們如何與 Control Plane 合作。


# Refernce
- [kubernetes 官方文件/components](https://kubernetes.io/zh-cn/docs/concepts/overview/components/)
- [kubernetes指南](https://kubernetes.feisky.xyz/concepts/architecture)
- [Kubernetes 核心介紹 Api Server](https://alanzhan.dev/post/2022-04-24-kubernetes-api-server/)

[2023/Day-06-Kubernetes 介紹]:https://ithelp.ithome.com.tw/articles/10320161

[Pod Topology Spread Constraints]: https://kubernetes.io/docs/concepts/scheduling-eviction/topology-spread-constraints/