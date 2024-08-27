
# Day-09-如何分派你的 Pod - Pod Topology Spread Constraints

# 前言
前兩天我們介紹了
- Node Affinity / Anti-affinity
- Inter-Pod Affinity / Anti-affinity 
已經能靈活地引導 `scheduler` 幫我們把 Pod 調度到我們期待的 Worker Node 了。
但也發現若是期待 Pod 平均分散到 Worker Node，仍然會遇到一些問題
- 當僅 Node Affinity 時，Pod 仍可能集中在少數 Node 上
- 搭配 Inter-Pod Affinity 之後，限制每個 Node 只能一個同類的 Pod 時，又降低最大可用的副本數量。

這兩種問題可能影響可用性的因素，所以今天要介紹的 `Pod Topology Spread Constraints` 就是專門解決此問題而生的設計的。

# Pod Topology Spread Constraints
`Pod Topology Spread Constraints` 的目的就是最大程度的打散 Pod 到不同的 topology上，其核心是一個叫 `skew` 數據，每組 Pod 在不同 topology 時，都會依據 Pod 副本數量計算出對應的 `skew` 值。

運算公式為：skew = Pods **number** matched in **current** topology - **min** Pods matches in a topology

![https://www.hwchiu.com/assets/images/SkWAL7S33-08bbfe0f59afad8380f63e1af86df610.png](https://www.hwchiu.com/assets/images/SkWAL7S33-08bbfe0f59afad8380f63e1af86df610.png)
圖檔來源: [HWCHIU 學習筆記 / 解密 Assigning Pod To Nodes(下)]

以上圖為例，有三個 topology，目前分別運行著 `3,2,1` 個 Pod，故我們能知道 **min** Pods matches in a topology 的值為 `1`，而 `skew` 是每個 topology 獨立計算的所以套用公式如下

- **min** Pods matches in a topology : `1`
1. Topology A
    - Pods **number** matched in **current** topology: `3`
    - skew: `2 (3 - 1)`
2. Topology B
    - Pods **number** matched in **current** topology: `2`
    - skew: `1 (2 - 1)`
2. Topology C
    - Pods **number** matched in **current** topology: `1`
    - skew: `0 (1 - 1)`

了解如何計算之後，`Pod Topology Spread Constraints` 的配置就很容易懂了
```yaml
spec:
  topologySpreadConstraints:
  - topologyKey: <string>
    maxSkew: <integer>
    labelSelector: <object>
    whenUnsatisfiable: <string>
    
```
- `topologyKey`: 依據什麼 Node Label 來劃分 topology，用法跟 Inter-Pod Affinity 一樣
- `maxSkew`: 該 topology 中的 Skew 最大限制，簡單來說就是 Pod 不均勻的程度
- `labelSelector`: 哪些 Pod 需要計算
- `whenUnsatisfiable`: 當全部的 topology 都不滿足 `maxSkew` 的條件時，該如何處理
    - `DoNotSchedule`: (Default) 不調度該 Pod，該 Pod 會處於 Pending
    - `ScheduleAnyway`: 仍然調度該 Pod，並優先選 Skew 低的 topology。

我們來看一個使用範例
![https://kubernetes.io/images/blog/2020-05-05-introducing-podtopologyspread/api.png](https://kubernetes.io/images/blog/2020-05-05-introducing-podtopologyspread/api.png)
圖檔來自: [Kubernetes 官方](https://kubernetes.io/blog/2020/05/introducing-podtopologyspread/)

當我們 或 [HPA(Horizontal Pod Autoscaling)] 要新增一個 Pod(label `app=foo`) 的副本時，目前兩個 topoloy 中該 Pod 的分佈為 `2,0` (zone2 中的 Pod label 不符合條件，故不算在內)。
故 `scheduler` 會計算當把 Pod 調度到該 topology 時，是否超過 `maxSkew` 的配置，依此為例
- 若調度到 zone1 的 topology 時，skew 為 `3`，不符合 `maxSkew: 1` 的條件，故不可調度到該 topology
- 若調度到 zone2 的 topology 時，skew 為 `0`，符合 `maxSkew: 0` 的條件，故可以調度到該 topology

與 `Inter-Pod Affinity` 相比較，`Pod Topology Spread Constraints` 提供的 `maxSkew` 與 `whenUnsatisfiable`，讓 Pod 能均勻分佈到每個 topology，且不再有每個 topology 只能運行一個 Pod 的限制，大大的提高資源利用率。

## 進階用法
某些情境下，Pod 的分佈還是會與 `maxSkew` 有一點落差，比如 Deployment 進行 Rolling update 時，可能有以下情境

當 Rolling update，時，通常新舊版本的 Pod 都會被 `LabelSelectors` 選中，故在新舊 Pod 切換期間可能有下情況發生
![https://miro.medium.com/v2/resize:fit:1400/format:webp/0*DSX-uCIKI-MlW92B](https://miro.medium.com/v2/resize:fit:1400/format:webp/0*DSX-uCIKI-MlW92B)
圖檔來自: [Avoiding Kubernetes Pod Topology Spread Constraint Pitfalls]

Pod 目前均勻分佈在三個 topology，但有一個是 舊版本，因為三個 topology 的 `skew` 皆為 0，故可能部署到任何一個 topology，所以可能發生以下情況

![https://miro.medium.com/v2/resize:fit:1400/format:webp/0*nCdvNNKaerBoJLFg](https://miro.medium.com/v2/resize:fit:1400/format:webp/0*nCdvNNKaerBoJLFg)
圖檔來自: [Avoiding Kubernetes Pod Topology Spread Constraint Pitfalls]

第三個 新版本 Pod 被調度到 AZ1，而 AZ3 的 舊版本 Pod 因 Rolling update 完成，被 Terminated 了，最終導致 Pod 分佈變成 `2,1,0` 違反了 `maxSkew: 1`。

這時候需要搭配 `Pod Topology Spread Constraints` 的另一個`matchLabelKeys` 屬性 與 Deployment 會替 Pod 自動打的 label `pod-template-hash`。
> 📘 每當 Deployment 的 `spec.template` 更改時，都會得到一個 hash 值，Pod 上能透過 `pod-template-hash` label 獲取該 hash 值。

Deployment yaml 片段 範例如下：
```yaml
spec:
  containers:
    - name: nginx
      image: nginx:latest
  topologySpreadConstraints:
  - maxSkew: 1
    topologyKey: kubernetes.io/hostname
    whenUnsatisfiable: DoNotSchedule
    labelSelector:
      matchLabels:
        app: nginx
    matchLabelKeys:
    - pod-template-hash
```

當在計算 `skew` 時，就會因為 `pod-template-hash` label value 的不同，而分開計算 `skew` 值，回到 rolling update 的例子

![https://miro.medium.com/v2/resize:fit:1400/format:webp/0*DSX-uCIKI-MlW92B](https://miro.medium.com/v2/resize:fit:1400/format:webp/0*DSX-uCIKI-MlW92B)
圖檔來自: [Avoiding Kubernetes Pod Topology Spread Constraint Pitfalls]

這次計算如下
 - **min** Pods matches in a topology : `0` (因為舊版本的不計算在內，而 AZ3 無任何匹配的 Pod，故為 0)
1. Topology A
    - Pods **number** matched in **current** topology: `1`
    - skew: `1 (1 - 0)`
2. Topology B
    - Pods **number** matched in **current** topology: `1`=
    - skew: `1 (1 - 0)`
2. Topology C
    - Pods **number** matched in **current** topology: `0`
    - skew: `0 (0 - 0)`

此時，新版本的第三個 Pod 就會被正確均勻分佈到 AZ3。

# 小結
今天我們學到了如何透過 `Pod Topology Spread Constraints` 讓 Pod 均勻分佈到 topology 達成高可用，也保持良好的資源利用率，當 Worker Node 發生故障時，就能盡量降低服務中斷的風險。

# Refernce
- [kubernetes 官方文件](https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/)
- [Avoiding Kubernetes Pod Topology Spread Constraint Pitfalls]
- [HWCHIU 學習筆記 / 解密 Assigning Pod To Nodes(下)]
- [小信豬的部落格 / [Kubernetes] Assigning Pods to Nodes](https://godleon.github.io/blog/Kubernetes/k8s-Assigning-Pod-to-Nodes/)



[kind]: https://kind.sigs.k8s.io/

[HWCHIU 學習筆記 / 解密 Assigning Pod To Nodes(下)]: https://www.hwchiu.com/docs/2023/k8s-assigning-pod-2

[HPA(Horizontal Pod Autoscaling)]: https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/

[Avoiding Kubernetes Pod Topology Spread Constraint Pitfalls]: https://medium.com/wise-engineering/avoiding-kubernetes-pod-topology-spread-constraint-pitfalls-d369bb04689e