# Day-19-Kubernetes_除錯思路分享

# 前言
今天會分享幾個我在做軟體開發 或 當擔任 DevOps 時，比較常遇到或被問的問題與排錯的思路，若剛好有遇到相同情況，希望能替讀者節省一些 Debug 的時間。

# 1. Pod 狀態卡在 `Pending`，一直無法變 `Running`
Pod `Pending` 代表在等待 Control plane 的 `scheduler` 組件將 Pod 分配到**適合**的節點上，什麼叫適合？

滿足以下條件
- Node 足夠的資源提供給 Pod 的 `resources.request`
- Node label 滿足 Pod 指定節點的需求 ([NodeSelector])
- 若 Node 配置有 taint，則 Pod 需有對應的 tolerations ([Taints and Tolerations])

以上三個問題，通常能使用 `kubectl describe pod` 來查看
```
kubectl describe pod demo-deployment-7cfb7c974d-rcg5s
```
節錄返回結果中 Conditions 與 Events 區塊
```
Conditions:
  Type           Status
  PodScheduled   False 

Events:
  Type     Reason            Age                From               Message
  ----     ------            ----               ----               -------
  Warning  FailedScheduling  81s (x4 over 16m)  default-scheduler  0/2 nodes are available: 1 Insufficient cpu, 1 node(s) had untolerated taint {node-role.kubernetes.io/control-plane: }. preemption: 0/2 nodes are available: 1 No preemption victims found for incoming pod, 1 Preemption is not helpful for scheduling..
```
能看到 `Conditions` 中 PodScheduled 狀態為 False，代表 Pod 尚未被分配到節點，而 `Events` 中能看到原因是沒有節點能足夠 cpu (此 pod)
` 0/2 nodes are available: 1 Insufficient cpu`

能從 `Events` 的 Message，判斷發生的原因，來調整 Pod 或找管理者協助。

已筆者自身的經驗，以下是我遇到該問題的次數(高到低)與處理方式
1. CPU/Memory 資源不足    
    ```
    Conditions:
      Type           Status
      PodScheduled   False 

    Events:
      Type     Reason            Age                From               Message
      ----     ------            ----               ----               -------
      Warning  FailedScheduling  81s (x4 over 16m)  default-scheduler  0/2 nodes are available: 1 Insufficient cpu, 1 node(s) had untolerated taint {node-role.kubernetes.io/control-plane: }. preemption: 0/2 nodes are available: 1 No preemption victims found for incoming pod, 1 Preemption is not helpful for scheduling..
    ```
   以下三種方式能擇一
   - 調整 Pod 資源，減少 request
   - 添加 Kubernetes worker node
   - 提高現有 worker node resource
2. 無法滿足 Pod [NodeSelector]
    ```
    Events:
      Type     Reason            Age   From               Message
      ----     ------            ----  ----               -------
      Warning  FailedScheduling  14s   default-scheduler  0/2 nodes are available: 1 node(s) didn't match Pod's node affinity/selector, 1 node(s) had untolerated taint {node-role.kubernetes.io/control-plane: }. preemption: 0/2 nodes are available: 2 Preemption is not helpful for scheduling..
    ```
    透過 ` kubectl get node --show-labels` 看是否有 Node 符合該 Pod 的 nodeSelector，看是 Node label 漏打還是 nodeSelector 寫錯。
3. 該 Pod 無法滿足 Node 的 taint
    ```
    Events:
    Type     Reason            Age   From               Message
    ----     ------            ----  ----               -------
    Warning  FailedScheduling  10s   default-scheduler  0/2 nodes are available: 1 node(s) had untolerated taint {key1: my-taint}, 1 node(s) had untolerated taint {node-role.kubernetes.io/control-plane: }. preemption: 0/2 nodes are available: 2 Preemption is not helpful for scheduling..
    ```
    這情況通常會跟 Kubernetes 管理者一起檢查，因為配置 taint 的 Node 都有特殊用途，比如具有 GPU 或 專門用於運行基礎設施。
    
    若經過管理者確認 Pod 應該要被運行在有 taint 的節點，則添加 tolerations 屬性於 Pod 上，可參閱 ([Taints and Tolerations])

若非以上問題，且 `Events` 也沒錯誤訊息，能在檢查負責調度節點的組件 `kube-scheduler` 是否運作正常
```
kubectl get pods -n kube-system | grep kube-scheduler

kubectl logs -n kube-system <kube-scheduler-pod-name>
```

# 2. 為什麼連不到我的服務
通常連不到服務的狀況，通常會從兩個面向開始檢查
1. Pod 運作是否正常
2. 流量傳遞是否正確

## Pod 運作是否正常
1. `STATUS` 是否為 RUNNING
2. `READY` 是否為 true 或 M/M
   > M 代表個 Pod 的 container 數量，比如該 Pod 只有一個 Container 則是 1/1
   透過 `kubectl get pod 來檢查
```
NAME                               READY   STATUS    RESTARTS       AGE
app-1   0/1     ImagePullBackOff   0              72m
app-2   0/1     Error   0              72m
app-3   0/1     Running   0              72m
app-3   1/1     Running   0              72m
```
`STATUS` 非 Running 時，能嘗試以下方式
- 透過 `STATUS` 去 Google 或 `kubectl describe` 檢查錯誤原因
- 使用 `kubectl logs` 檢查運行日誌
- 使用 `kubectl get pod ${pod-name} -o yaml` 匯出 pod yaml 檢查內容

`READY` 非 ture 與 M/M 時
通常是 readiness 探針檢測的位置不存在，或readiness 探針檢測的返回值返回失敗。
能透過 `kubectl describe` 從 `Events` 區塊看到錯誤訊息，若不確定返回值，能進入到容器內調用看看
```
# 進入容器
kubectl exec -ti ${pod-name} -- bash

# 調用你配置的 Http readiness 端點
$ curl localhost:${port-number}/${readiness target URL path}
```

若以上檢查都完成，確認 Pod 的 `STATUS` 為 `Running` 且 `READY` 為 `true`，我們開始調查流量傳遞是否正確。

## 流量傳遞是否正確
回想我們先前介紹的 Ingress, service, pod，分別處理流量轉發的一部分工作，流量傳遞由外到內的大致順序如下
![network-traffic](https://banzaicloud.com/blog/k8s-ingress/ingress-host-based_huceb35be881c2eb47b69d4d146880e204_114327_960x0_resize_box_2.png)
1. Ingress 負責接收外部流量，依照 routing rule 發配流量給 Service
2. Service、Endpoints 與 kube-proxy 負責進行服務發現與實現流量轉發規則，讓流量能導向 Pod
3. Pod 透過隨機的 Pod IP 與 expose 定義來接收流量到內部 container
4. 實際運行服務的 container 收到流量請求並處理

實務經驗上來看，最常見的是 Pod 或 Container 層的問題，所以我們從內到外開始檢查。
- 檢查 Container 應用程序是否正常
- 檢查 Pod 流量映射規則
- 檢查 Service/Endpoints 是否正常
- 檢查 Ingress 是否正常

### 檢查 Container 應用程序是否正常
透過 `kubectl exec` 進入 container 中，並調用應用程序，看是否有回應
```
kubectl exec -ti ${pod-name} -- bash

$ curl localhost:${port-number}
```
若調用不到，代表該 container 運行的應用程序，不存在你想調用的 Pod，應檢查 container image 版本是否有誤，或包入 container 的應用程序版本是否正確。

### 檢查 Pod 流量映射規則
檢查透過 Pod IP 進行 Pod to Pod 的調用，看是否服務能正常回應
1. 查詢該 Pod IP
    ```
    kubectl get pod -o wide
    ```

    ```
    NAME                               READY   STATUS    RESTARTS       AGE     IP            NODE                NOMINATED NODE   READINESS GATES
    demo-deployment-767f4b86d9-dxc9v   1/1     Running   0              2d19h   10.244.1.49   my-cluster-worker   <none>           <none>
    ```
    依此為例 Pod IP 為 `10.244.1.49`

2. 找一個 Debug Container，從下方選一個方式
  - 透過 `kubectl debug` (推薦, 但 kubernetes 版本需 > 1.18)
  - 運行一個 Debug container (推薦)    
    ```
    kubectl run -n default debug-container --rm -i --tty --image docker.io/nicolaka/netshoot -- /bin/bash
    ```
    > Debug Container 指有適當除錯工具的 container，方便進行調用測試，而 docker.io/nicolaka/netshoot 是個不錯的選擇。
    - 找有調用工具(如 `curl`)的既有 Pod，從該 Pod 進行調用
    ```
    kubectl exec -it ${pod-name} -- /bin/bash
    ```
3. 在 Container 中調用該 Pod IP
   ```
   $ ping 10.244.1.49

   $ curl 10.244.1.49:{port-number}
   ```
    若調用不到，很可能是 Pod yaml 中配置的 `spec.ports` 有問題

### 檢查 Service/Endpoints 是否正常 
1. 檢查 Endpoint 是否有映射到 Pod IP
    ```
    kubectl get endpoints ${endpoints}
    ```
    ```
    NAME                             ENDPOINTS                                            AGE
    demo-deployment                  10.244.1.13:8080,10.244.1.48:8080,10.244.1.49:8080   2d19h
    ```
    能看到 ENDPOINTS 包含 Pod IP `10.244.1.49`，代表 Endpoints 的服務發現機制正常。   
    若 ENDPOINTS 沒有你的 Pod IP，請檢查 Service 的 `selector` 是否能對應到 Pod

2. 找一個 Debug Container，參考 **檢查 Pod 流量映射規則**中第二步驟。
3. 在 Container 中調用該 Service DNS name
    > Service DNS name 規則為: ${service-name}.${namespace}.svc.cluster.local，依此為例為 demo-deployment.default.svc.cluster.local
    ```
    $ nslookup demo-deployment.default.svc.cluster.local

    $ ping demo-deployment.default.svc.cluster.local

    $ curl demo-deployment.default.svc.cluster.local
    ```
    若調用不到，請確認 service yaml 中的 `ports` 是否有誤

#### **檢查 Ingress 是否正常**
1. 透過外部系統 或 Debug Container，對 Ingress hosts 進行調用
    ```
    $ curl hello.ingress.com
    ```
  若調用不到，檢查 ingress yaml 中 `spec.rules` 是否正確

透過上述的排查順序，通常能找到問題發生的原因，筆者的經驗上問題比較長出在 Container / Pod 的部分居多，大多時候是應用程序問題, expose 錯 ports，偶爾會有 service selector, ingress rule 寫錯。

更詳細的排查方式，能拜讀這兩篇神作 
- [A visual guide on troubleshooting Kubernetes deployments]
- [為什麼我佈署的 Kubernetes 服務不會動!? 個人除錯思路分享]

# 3. 其他 namespace 的 Pod 連不到我的 Pod
算是上一個情境的延伸題，通常不同團隊會有自己的 namespace，當服務互相需要調用時，會透過 Service DNS names 調用。
假設你已經透過 Debug container 在同個 namespace 下，透過 Service DNS names 調用到你的服務，那能檢查一下 [network policy](https://kubernetes.io/docs/concepts/services-networking/network-policies/)，network policy 能在 namespace 維度管理流量的進出，可能是對方的 namespace 的 network policy 阻擋流量發送，也可能是你 namespace 的 network policy 阻擋對方流量進入。

 ```
# 查 networkpolicies 配置
kubectl get networkpolicies.networking.k8s.io
 ```
 檢查 networkpolicies 中的 `spec`，相關規則參考 [官方文檔](networkpolicies) 

4. 我的 Pod 一直重啟
一直重啟很可能是 container 運行失敗，能透過以下方式檢查
   1. 使用 `kubectl logs` 檢查運行日誌
   2. 檢查 liveness 是否設定在對的位址
   3. 透過 `kubectl exec` 進入 Pod 內部檢查失敗原因
      1. 若 Pod 很快就崩潰關閉，能更改 Pod yaml 的 `command` 屬性搭配 `sleep` 指令來爭取檢查 container 內部的時間，範例如下
      ```
      apiVersion: v1
      kind: Pod
      metadata:
        name: you-pod-name
        labels:
          app: you-pod-name
      spec:
        containers:
        - name: app
          image: you-image-name
          ports:
          - containerPort: 80
          command: ["/bin/sh", "-ec", "sleep 1000"]
      ```

# 總結
今天快速的分享幾個很常見的問題與排錯思路，希望對開發與維護有幫助
另外推薦花一點時間讀這兩篇大佬分享排錯思路的文章，排查問題時，幫助我節省很多時間。
- [A visual guide on troubleshooting Kubernetes deployments]
- [為什麼我佈署的 Kubernetes 服務不會動!? 個人除錯思路分享]




# 參考
- [A visual guide on troubleshooting Kubernetes deployments]
- [為什麼我佈署的 Kubernetes 服務不會動!? 個人除錯思路分享]
- [Kubernetes ingress, deep dive](https://banzaicloud.com/blog/k8s-ingress/)

[A visual guide on troubleshooting Kubernetes deployments]:https://learnk8s.io/troubleshooting-deployments
[為什麼我佈署的 Kubernetes 服務不會動!? 個人除錯思路分享]: https://blog.pichuang.com.tw/20211129-kubernetes-service-troubleshoot.html

[NodeSelector]: [nodeSelector](https://kubernetes.io/docs/tasks/configure-pod-container/assign-pods-nodes/)

[Taints and Tolerations]: https://kubernetes.io/docs/concepts/scheduling-eviction/taint-and-toleration/