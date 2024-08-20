
# Day-03-Kubernetes Architecture - Worker Node 

# Kubernetes Cluster Architecture
![Archtitecture](https://kubernetes.io/images/docs/kubernetes-cluster-architecture.svg)
圖檔來源 [Kubernetes 官方文件](https://kubernetes.io/docs/concepts/architecture/)

上一篇我們介紹了 Kubernetes Architecture 中的 Control Plane，它負責的管理與調配 Cluster 的任務，而今天要來介紹 Kubernetes 實際運行我們 Container 的伺服器類型 與 該伺服器上運行的核心組件。

## Worker Node
![worker node](https://github.com/YihongGao/picx-images-hosting/raw/master/20240818/截圖-2024-08-18-下午1.34.13.7i0b5m8o6j.webp)

在 Kubernetes  Cluster 中，Control Plane 之外的伺服器通常都是 Worker Node，這些 Node 負責執行使用者配置的 Pod(Container)，每個 Worker Node 會向 Control plane 註冊該伺服器的資訊與定期回報狀態，讓 Control Plane 有足夠資訊來協調 Cluster 運作
> 📘 精確來說 Worker Node 上運行的是 Pod 配置的 container，而 Pod 是 Kubernetes 的抽象概念。

## kubelet
kubelet 是運行於每個 Worker Node 的一個 Process，負責監控與管理 Pod，負責以下任務
- **監控與管理 Pod**：kubelet 會不斷監控節點上的 Pod，確保它們按照定義的狀態（如規範的容器數量與配置）正常運行。如果有 Pod 異常，kubelet 會嘗試重新啟動。

- **接收 Control Plane 指令**：kubelet 與 Control Plane 的 kube-apiserver 保持持續通信。當 kube-apiserver 下發新的 Pod 配置或更新時，kubelet 會接收並執行這些指令，啟動或停止容器。

- **狀態回報**：kubelet 會定期向 kube-apiserver 回報該節點上的 Pod 狀態，以及節點本身的健康狀況。讓 Control Plane 做出資源調度和故障恢復的決策。

## kube-proxy
於 [2023/Day-10-Kubernetes 介紹-Service] 介紹了為什麼應該透過 [Service] 提供的端點來向 Pod 的應用程序進行溝通。而 kube-proxy 就是實現 Service 功能的功臣之一。

kube-proxy 會依照 Service 的配置，將 Service 被分配到的 Cluster IP 透過 iptables 或 IPVS 來實現轉發規則，負責將往該 Cluster IP 的請求轉發到對應的 Pod。

而通常 Cluster 內也會安裝 DNS 服務(如 [CoreDNS])，當有對 [Service FQDN] 發出請求時，會透過 DNS 服務解析出該 FQDN 的 Cluster IP，調用端將流量往 Cluster IP 發送後，後續由 kube-proxy 將流量往正確的 Pod 轉發，來實現 [Service] 的功能性。

## Container Runtime Interface(CRI)
CRI 具體來說只是一個 Kubernetes 提供的標準化介面，只要該 Container Runtime 實現了 CRI 的介面，都能作為 Kubernetes 運行 Pod 的底層實現，負責處理 Pod 的生老病死，該介面也讓 Kubernetes 不依賴特定的 Container Runtime(如 Docker Engine)。

常見的 CRI 實現有
- [Containerd](https://containerd.io/)
- [CRI-O](https://kubernetes.io/docs/setup/production-environment/container-runtimes/#cri-o)
- [Docker Engine](https://kubernetes.io/docs/setup/production-environment/container-runtimes/#cri-o)


# Refernce
- [Kubernetes 官方/Container Runtime](https://kubernetes.io/docs/setup/production-environment/container-runtimes/)

[Service]: https://kubernetes.io/docs/concepts/services-networking/service/

[2023/Day-10-Kubernetes 介紹-Service]: https://ithelp.ithome.com.tw/articles/10323802

[CoreDNS]: https://kubernetes.io/docs/tasks/administer-cluster/coredns/

[Service FQDN]: https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/