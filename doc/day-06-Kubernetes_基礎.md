# Day-06-Kubernetes 介紹
![kubernetes icon](https://kubernetes.io/images/kubernetes-horizontal-color.png)
# 前言
前面已經學習了如何使用 Docker 建構你自己的 Container Image 與 運行 Container，今天我們將開始介紹 Kubernetes 已經他帶給我們什麼好處。

# 什麼是 Kubernetes？
Kubernetes，簡稱 K8s，是一個強大的開源容器管理平台，用於自動化和管理容器化應用程式的部署、擴展、以及運營。它最初由 Google 開發，，現在由雲原生計算基金會（CNCF）維護。Kubernetes 的名稱源自希臘語，意為“舵手”或“駕駛員”，這個名稱非常貼切，因為 Kubernetes 用於導航和管理容器應用程式的生命周期。

# 為什麼要使用 Kubernetes？
想像一下我們再生產環境有多台伺服器時，每台伺服器上都部署了數十個容器服務時，我們希望每個服務都能正常運行且具有高可用性，比如於服務崩潰時，能自我修復，甚至服務因為請求流量大增時，能利用進行水平擴展，分散服務的處理壓力。

而 Kubernetes 提供了這些特性，以下舉出幾個常使用到的部分
- 容器編排：依據資源要求將容器運行於符合要求的伺服器，或 避免所有容器都運行在同一個伺服器，減少伺服器不可用的風險
    > 例如容器要求使用  2 GB 的 Memory，Kubernetes 會嘗試找到至少有 2 GB 可用的 Memory 的伺服器來運行該容器。
- 自我修復：當容器崩潰時，將自動重啟該容器，直到容器準備好提供服務，才對外開放流量。甚至在伺服器失效時，能將運行於失效伺服器的容器移轉至其他可用伺服器並嘗試啟動。
    > 運行於 Docker 的 container，若 container 內部的服務崩潰時，預設不會自動重啟，需人工處理。
- 可擴展性：能透過手動或自動的方式來擴展或縮減你的服務，來應付大量的請求。
- 服務發現 與 負載均衡：Kubernetes 提供透過 DNS Name 或 IP 的方式來訪問容器。
    > 當你的同一個服務部署了多個 Container instance 時，會希望有個穩定提供服務的方式(DNS/IP)，不會因為 instance 增加/減少而改變，且希望請求流量能平均分配
- 滾動式部署(Rolling Deployment) 與 回滾(Rollback)：再不中斷服務的情況下，部署新版本的服務，待新版本服務準備好時，才關閉舊版本的 instance。若新版本出現故障或問題，也能快速回滾到穩定版本

當然還有更多強大的特性，如更有效的利用資源(CPU/MEM)、聲明式管理、配置管理(ConfigMpa/Secret)、Storage 編排...等等，這些讀者能從 [Kubernetes 官方](https://kubernetes.io/) 獲得更多資訊。

# Kubernetes 架構圖
![Kubernetes architecture](https://www.cncf.io/wp-content/uploads/2020/09/Kubernetes-architecture-diagram-1-1-1024x698.png)
圖檔來源 [CNCF / How Kubernetes works](https://www.cncf.io/blog/2019/08/19/how-kubernetes-works/)

Kubernetes 通常由多個伺服器組成，稱為 Kubernetes Cluster，而這些伺服器大致分為兩個類型
- Master Node: 
  - 控制台：Master Node 是 Kubernetes Cluster的大腦，負責整體控制和協調集群中的所有操作。它接收用戶和管理工具的指令，並確保集群中的所有工作都按照期望運行。

  - 決策中心：Master Node 包含各種控制器，這些控制器負責管理和維護應用程式的狀態。例如，它確保應用程式的副本數量正確，並根據需求進行擴展或縮減。

  - 資源調度：Master Node 的排程器（Scheduler）負責確定在哪個工作節點上運行容器，以確保最佳的資源利用率和性能。
    > 以上功能是由架構圖中的 Control plane 提供的，能大致認為運行 Control plane 伺服器為 Master Node
- Worker Node:
  - 容器執行：Worker Node 是實際運行容器的地方。它負責管理容器的生命週期，包括創建、啟動、停止和清理容器。

  - 資源提供者：Worker Node 提供計算和儲存資源，以運行容器。它確保容器具有足夠的資源（CPU、記憶體等）以正常運行。

  - 網絡管理：Worker Node 上的 Kube Proxy 確保容器可以相互通信並與外部網絡進行通信。它處理網絡路由、負載平衡和端口轉發等網絡任務。

總體來說，Master Node 是 Kubernetes Cluster 的控制中心，負責接收管理者的請求，並依照要求進行調配，而 Worker Node 是實際運行容器的地方，負責容器的生命週期管理和資源提供。它們共同協作以實現容器化應用程式的部署、維護和高可用性運行。

# 總結
大體來說使用 Kubernetes 能解決開發者與伺服器管理者再生產級別遇到的一些問題，讓開發與維護工作更有效率。
- 對開發者：    
    1. 更好的可用性：
       1. 當容器或服務崩潰時，它會嘗試自動重啟或重新調度到其他可用節點，以確保服務的持續可用性。
       2. 遇到高流量時，服務能自動擴展，當流量退去時，能自動縮減服務
       3. 流量能自動平均分配到各 Container Instance
       4. 更新服務版本時，透過 滾動式部署(Rolling Deployment)，失敗時也能快速回滾，能降低服務中斷時間。
    2. 易於開發：
       1. 提供穩定的方式(DNS Name)讓其他服務調用，開發分散式系統時特別需要。
       2. 集中式的配置管理(ConfigMap/Secret)，讓應用服務原始碼與配置分離，讓原始碼更乾淨且易於管理。
- 對伺服器管理者：
    1. 更好的可用性：
       1. 當 Worker Node 不可用時，會將容器調度到其他 Worker Node，嘗試恢復服務。
    2. 資源利用最大化：
       1. 服務會自動調度到有足夠資源的 Workder Node，不需依賴人工分配，減少管理作業並降低資源浪費。
    3. 易於管理：
       1. 提供 CLI 與 UI，能更輕鬆的管理、監控整個 Cluster。
       2. 透過宣告式的配置，由 Control plane 自動滿足配置要求。

今天我們簡單的介紹了 Kubernetes 的特性與解決哪些問題，明天會用 `kind` 在本地架設一個小的 Kubernetes 環境，方便後續介紹 Kubernetes 的組件。