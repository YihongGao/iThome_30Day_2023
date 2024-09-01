
# Day-05-Kubernetes Architecture 介紹 - 當建立 Pod 時，發生了什麼(二)

# 前言
昨天我們介紹了當下達 `kubectl` 指令來建立 Pod 時，Control plane 對請求進行驗證，並把配置持久化到資料庫後，透過調度演算法挑選了一個適合的 Node 與該 Pod binding（綁定)，今天會介紹 Worker Node 是如何將 Pod 建立出來的。


# Kubernetes Cluster Architecture
![Archtitecture](https://kubernetes.io/images/docs/kubernetes-cluster-architecture.svg)
圖檔來源 [Kubernetes 官方文件](https://kubernetes.io/docs/concepts/architecture/)

# kubelet
![https://miro.medium.com/v2/resize:fit:1400/format:webp/0*GWevN0yZS4roLOtu.png](https://miro.medium.com/v2/resize:fit:1400/format:webp/0*GWevN0yZS4roLOtu.png)    
圖檔來至: [itnext.io/what-happens-when-you-create-a-pod-in-kubernetes](https://itnext.io/what-happens-when-you-create-a-pod-in-kubernetes-6b789b6db8a8)

每個 Worker Node 上都會運行一個叫 `kubelet` 的 Process，負責監控與管理該 Node 的 Pod，它會定期向 `kube-apiserver` 查詢是否有新的 Pod 被 Binding 到該 Node，若發現有新 Pod 時，則觸發建立流程。

該流程會透過使用 CRI、CNI、CSI 這三個 interface，將建立 Pod 的任務分派給底層的實現軟體。
1. CRI (Container Runtime Interface)： Kubernetes 用來與 `Container Runtime` 溝通，用來進行 Pod 的啟動、停止..等操作。
> 📘 常見的 Container Runtime 如 dockershim、containerd、CRI-O 這類負責建立、啟動、停止、銷毀 container 的實現軟體
2. CNI (Container Network Interface)： Kubernetes 用來管理 Pod 的網路，簡單來說它會幫你的 Pod 分配一個內部 IP，並負責讓該 Pod 能與其他 Pod 或外界進行網路通訊。

3. CSI (Container Storage Interface)： Kubernetes 用來向 Storage 的 Driver 管理儲存空間的生命週期，比如：當 Pod 有請求 Volume 資源時，會透過此介面將儲存空間與 Pod 綁定。

> 📘 Kubernetes 透過依賴 interface，而不直接使用特定實作來完成 Pod 的建立與生命週期，使得 Kubernetes 管理者能依照需求替換不同的實現軟體。

簡單來說 Kubernetes 
- 透過 CRI 初始化 Pod
- 透過 CNI 為該 Pod 配置內部 IP 給 Pod 與其網路設定
- 最後讓 CSI 掛載 volume 到 Pod，提供 Storage 空間。

這時，Pod 與其中的 Container 就正式運行在該 Worker Node 之中了。

![https://miro.medium.com/v2/resize:fit:1400/format:webp/0*WgOaCA0trzf4SmjJ.png](https://miro.medium.com/v2/resize:fit:1400/format:webp/0*WgOaCA0trzf4SmjJ.png)
圖檔來至: [itnext.io/what-happens-when-you-create-a-pod-in-kubernetes](https://itnext.io/what-happens-when-you-create-a-pod-in-kubernetes-6b789b6db8a8)

最後 `kubelet` 會將該 Pod 的詳細資訊(IP、狀態) 回報到 Control Plane (透過 `kube-apiserver` 儲存到 etcd)。

到這裡，這個 Pod 的初始化旅程基本上算是完成了。
若該 Pod 中的 container 運行正常，應該能走到 Pod 狀態機中的 `Succeeded`
![https://miro.medium.com/v2/resize:fit:1400/format:webp/0*vg1x1jEQ8pWyNzu9.png](https://miro.medium.com/v2/resize:fit:1400/format:webp/0*vg1x1jEQ8pWyNzu9.png)

若是 `kubectl get pod` 時，發現 Pod 的 Status 出現
- `ImagePullBackOff`：代表 CRI 沒辦法取得該 Pod 定義的 container image。
    1. 建議檢查 image 位址與 tag 是否正確
    2. 檢查是否有 pull 該 container image 的權限
    3. 檢查通往 image registry 的網路是否通暢
- `CrashLoopBackOff`：代表 Pod 的容器持續被判斷為不可用
    1. 透過 `kubectl describe pod {pod-name}` 檢查具體原因並排除即可
    2. 通常是容器中內部的應用程序啟動有問題，也能透過 `kubect logs {pod-name}` 檢視運行日誌
    3. 檢查 [liveness] 配置是否與應用程序提供的端點相符

## 小結

整個 Pod 部署的完整的流程能參考下圖，能快速了解 Kubernetes 的組件之間如何互相合作來完成此任務
![https://miro.medium.com/v2/format:webp/1*WDJmiyarVfcsDp6X1-lLFQ.png](https://miro.medium.com/v2/format:webp/1*WDJmiyarVfcsDp6X1-lLFQ.png)
圖檔來至: [The journey of a Pod: A guide to the world of Pod Lifecycle](https://medium.com/@seifeddinerajhi/navigating-the-journey-of-a-pod-a-guide-to-the-exciting-world-of-pod-lifecycle-a1fbc2c98c55)

若是對 `kubelet` 如何與 CRI、CNI 互動能參考此下圖
![https://miro.medium.com/v2/resize:fit:1400/1*OuXfcIUU-VShb3kXmEU2BA.png](https://miro.medium.com/v2/resize:fit:1400/1*OuXfcIUU-VShb3kXmEU2BA.png)
圖檔來至: [The birth story of the kubernetes pods](https://sitereliability.in/deep-dive-the-birth-of-a-kubernetes-pod-understand-the-kubernetes-internals)

到這裡，讀者們是不是發現 `kube-proxy` 這個 worker node 的核心組件，沒有出現於此流程當中。
明天我們會繼續介紹 `kube-proxy` 這個不可或缺的組件，在使用 kubernetes 中怎麼發揮作用。

# Refernce
- [itnext.io/what-happens-when-you-create-a-pod-in-kubernetes]
- [The birth story of the kubernetes pods]
- [kubectl 创建 Pod 背后到底发生了什么]
- [The journey of a Pod: A guide to the world of Pod Lifecycle]

[itnext.io/what-happens-when-you-create-a-pod-in-kubernetes]:
https://itnext.io/what-happens-when-you-create-a-pod-in-kubernetes-6b789b6db8a8

[The birth story of the kubernetes pods]: https://sitereliability.in/deep-dive-the-birth-of-a-kubernetes-pod-understand-the-kubernetes-internals

[kubectl 创建 Pod 背后到底发生了什么]: https://icloudnative.io/posts/what-happens-when-k8s/

[ResourceQuota]:https://kubernetes.io/docs/concepts/policy/resource-quotas/

[LimitRanger]: https://kubernetes.io/docs/tasks/administer-cluster/manage-resources/memory-default-namespace/

[init-container]: https://kubernetes.io/docs/concepts/workloads/pods/init-containers/

[The journey of a Pod: A guide to the world of Pod Lifecycle]: https://medium.com/@seifeddinerajhi/navigating-the-journey-of-a-pod-a-guide-to-the-exciting-world-of-pod-lifecycle-a1fbc2c98c55

[liveness]: https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/