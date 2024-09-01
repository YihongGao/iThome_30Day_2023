
# Day-10 KEDA 介紹 - 自由擴展你的 Pod

# 前言
我們了解許多管理 Pod 副本分佈來保持服務高可用的方式了，再搭配 [HPA (Horizontal Pod Autoscaling)] 的功能，就能依據 Pod CPU / Memory 自動擴/縮容 Pod 副本，達到系統高可用 與 良好的資源使用率。
> 📘 HPA 的使用方式能參閱官方文件或 [2023/day-29-Kubernetes 介紹-Pod 水平自動伸縮 (Horizontal Pod Autoscaler)]

但 [HPA] 有一些限制存在
- Metrics 單一 : 原生 HPA 只能透過 Container CPU, Memory 來配置擴/縮容條件，當關鍵指標為其他數據時，則無法有效調整 Pod 副本。
- 反應速度較慢：當 CPU, Memory 升高，可能都是收到大量負載，而導致資源使用率提高的現象。
- 無法縮容到 0 個 Pod：無法更有效的利用資源

而 [KEDA] 這個 CNCF 專案，能強化 HPA 的能力，讓 Kubernetes 提供更好的擴/縮容能力。

# 什麼是 KEDA (Kubernetes Event-driven Autoscaling)
[KEDA] 故名思義是基於 Event-driven 的 Autoscaling 組件，能將許多外部服務作為事件來源，更容易的控制 Pod 擴/縮容，例如
- 使用 Prometheus 中的 metrics 作為事件來源：     
    - 當 API QPS(query per second) 超過閥值時，進行擴/縮容
    - 平均 Response time 超過閥值時，進行擴/縮容
- 使用各種 Message Queue 作為事件來源：
    - Queue 長度大於閥值時，進行擴/縮容
- 使用 Database 作為事件來源：
    - 依照 SQL 結果進行擴/縮容

# KEDA 架構
![https://keda.sh/img/keda-arch.png](https://keda.sh/img/keda-arch.png)

KEDA 再 Kubernetes 中，透過以下三個主要組件 與 HPA 互相合作來提供功能。

## 主要組件
1. Controller : 依照配置判段 Workload 是否需要啟動，需要啟動時將 replica 調整為 1，而需要關閉時將 replica 調整為 0。
2. Metrics Adapter : 將外部事件轉為 metrics 提供給 HPA，進行進一步擴縮容決策。
3. Admission Webhooks：用於驗證 Kubernetes 資源的變更，避免配置錯誤。

簡單來說，KEDA 會監控外部服務(如 Prometheus)提供的 metrics，透過 Metrics Adapter 將 metrics 提供給 Controller 與 HPA，使其根據 metrics value 來控制 workload 副本數量。

## CustomResourceDefinition（CRD)
1. ScaledObjects：
    - 用來定義 Kubernetes Work load（如 Deployment 或 StatefulSet）的自動擴展策略
    - 監控的事件來源、觸發擴展的條件...等配置。

2. ScaledJob：
    與 ScaledObjects 相似，只不過管理的是 Kubernetes Job

3. TriggerAuthentication：
    - 用來配置向事件來源存取時，要攜帶的認證資訊或配置(Ex: API Key)，提供該 Resource namespace 的 ScaledObjects 與 ScaledJob 使用。

4. ClusterTriggerAuthentication：
    - 與 TriggerAuthentication 相似，不過是 Cluster 中的ScaledObjects 與 ScaledJob 都能使用。

# 環境準備
這幾天我們會在本地的 [kind]，安裝 [KEDA] 並進行實驗其功能性。

## 透過 [helm] 安裝 [KEDA]
```shell
helm repo add kedacore https://kedacore.github.io/charts

helm repo update

helm install keda kedacore/keda --namespace keda --create-namespace
```

能 kubernetes 中建立了一個新的 namespace: `keda`，裡面運作了三個 Deployment，分別就是 KEDA 提供服務三個主要組件
```shell
kubectl get deployments.apps -n keda 
NAME                              READY   UP-TO-DATE   AVAILABLE   AGE
keda-admission-webhooks           1/1     1            1           21h
keda-operator                     1/1     1            1           21h
keda-operator-metrics-apiserver   1/1     1            1           21h
```

# 小結
明天我們會先來 Demo 如何透過 KEDA 依據 Prometheus 中的 metrics 來進行自動擴展策略。

# Refernce
- [KEDA]
- [KEDA — 基本介紹](https://medium.com/@felix0607/keda-%E5%9F%BA%E6%9C%AC%E4%BB%8B%E7%B4%B9-2c7bf8249033)


[KEDA]: https://keda.sh/
[HPA]: https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/
[HPA (Horizontal Pod Autoscaling)]: https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/

[2023/day-29-Kubernetes 介紹-Pod 水平自動伸縮 (Horizontal Pod Autoscaler)]: https://ithelp.ithome.com.tw/articles/10336846

[kind]: https://kind.sigs.k8s.io/

[helm]: https://helm.sh/