
# Day-05-Kubernetes Architecture - kube-proxy

# 前言
前兩天我們認識了建立 Pod 的指令背後，在 kubernetes 中發生的一連串處理機制。
今天會介紹在該機制中沒有出現，但仍然是不可或缺的組件: `kube-proxy`

在介紹 kube-proxy 之前，要先介紹一下通常我們如何存取 Pod 的應用程序。

# 如何存取 Pod 的應用程序
我們知道當 Pod 被建立時，會由 CNI (Container Network Interface) 分配一個內部 IP 給 Pod，稱為 Pod IP，並且能透過 Pod IP 對 Pod 發送網路請求。

但 Pod 隨時可能會被銷毀或創建，並且當 Pod 被創建時，都可能分配到不同的 Pod IP。

因為 Pod IP 是不穩定的，所以使用 Pod IP 來存取 Pod 並不是一個好主意。通常會透過 [Service] 來作為 Pod 的存取端點，它提供一個能穩定調用 Pod 應用程序的位址，不會隨著 Pod 的建立/銷毀而改變，並且具有負載均衡的效果。

我們來快速看一下 Service，我們透過 `kind` 在本地啟動一個 kubernetes 環境
```
kind create cluster --name ithome-2024 --config kind-config.yaml

# 建立 namespace
kubectx create ns ithome

-- 
kubectl create -n ithome deployment nginx --image=nginx:latest --replicas=2 

kubectl expose -n ithome deployment nginx --port=8080 --target-port=80




```

> 📘 關於 [Service] 的使用方式能參考[官方文件](https://kubernetes.io/docs/concepts/services-networking/service/) 或是 筆者去年的[分享](https://ithelp.ithome.com.tw/articles/10323802)


# 那當建立 Service 時，
而 `kube-proxy` 就是負責實現 Service 的關鍵組件。

# kube-proxy







[Service]: https://kubernetes.io/docs/concepts/services-networking/service/

[Endpoints]: https://kubernetes.io/zh-cn/docs/concepts/services-networking/service/#endpoints