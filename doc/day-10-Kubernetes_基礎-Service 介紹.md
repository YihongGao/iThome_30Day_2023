# Day-10-Kubernetes_介紹-Service

# 前言
昨天我們使用 Deployment 來管理 Pod，但 Pod 隨時可能會被銷毀或創建(例: Rolling update 或 Pod crash)，Pod IP 隨著 Pod 銷毀時被移除，創建時重新隨機分配。

這衍生了一個問題，當你的 Pod 想調用另一個 Pod 的時候，你該怎麼知道該連到哪個 IP 呢？

今天就來介紹怎麼透過 Service 解決這個問題。

# 什麼是 Service
Kubernetes 中的 Service，能簡單理解為一個基於 [Label selector] 的服務發現的機制，能定義一個穩定的存取方式，並依照 Label selector 將流量發配至有對應 Label 的一個或多個 Pod，讓調用方不需要知道實際上連到哪個 Pod。
> Label selector 是一種標籤選擇器，用於定義哪些 Pod 與 Service 相關聯，詳細可參考[官網](https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/)。

直接產生一份 Service 的 yaml 來看看，實際上怎麼定義存取方式跟使用 Label selector，先透過 `kubectl expose` 產生一份 yaml
```
# 假設我想定義怎麼存取 Deployment/my-deployment 管理的 Pod，該 Service 收到 8080 port 的請求時，會將流量轉發到 Pod 的 80 port 
kubectl expose deployment my-deployment --port=8080 --target-port=80 -o yaml --dry-run=client --type=ClusterIP > service.yaml
```

會產生以下 yaml
```
apiVersion: v1
kind: Service
metadata:
  creationTimestamp: null
  labels:
    app: my-deployment
  name: my-deployment
spec:
  ports:
  - port: 8080
    protocol: TCP
    targetPort: 80
  selector:
    app: my-deployment
  type: ClusterIP
status:
  loadBalancer: {}
```

逐步介紹幾個重要的屬性
- apiVersion: 表示這份 yaml 使用的 API 版本號，目前 Deployment 的 API 版本號為 `apps/v1`，若描述其他元件時，可能會使用不同版本號
- kind: 表示這份 yaml 描述的元件名稱
- metadata: 
  - name: Service 的名稱
  - labels: key/value 結構的標籤
- spec:
  - ports: 定義流量轉發規則
    - port: 提供調用的 port 
    - targetPort: 轉發到實際 port 時的 port
    - protocol: 連線的協定，預設為 TCP
  - selector: 定義要發到哪些 Pod，依此例子，會轉發到擁有 `app: my-deployment` 的 Pod
  - type: Service 類型 (ClusterIP/NodePort/LoadBalancer) 預設為 ClusterIP，後面解釋

簡單來說，這份 yaml 會產生獨特的 DNS Name 與 IP，調用方連線到此  DNS Name 或 IP 時，會將流量轉發致 `selector` 中定義的 Pod 上。

我們來 apply 這份 yaml 看看 kubernetes 中會多出哪些 resource
```
# 要求 Kubernetes 依據 service.yaml 的內容，配置流量轉發規則
kubectl apply -f service.yaml

# 查詢 service 與 endpoints
kubectl get service,endpoints
```
預期結果(內容的IP是隨機的)
```
NAME                    TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)    AGE
service/my-deployment   ClusterIP   10.96.143.142   <none>        8080/TCP   112s

NAME                      ENDPOINTS                                      AGE
endpoints/my-deployment   10.244.1.41:80,10.244.1.42:80,10.244.1.43:80   112s
```

能看到生成了一個 service 與 endpoints。
- service: 代表提供外部存取的端點，依此為例，能從 Kubernetes 內部透過以下方式存取
  - IP: `curl 10.96.143.142:8080`
  > 因為 IP 是隨機分配的，通常我們也不會使用 IP 來連線
  - DNS Name: `curl my-deployment.default.svc.cluster.local:8080`
  > 1. DNS Name 為建議的存取方式，他是不可變的
  > 2. DNS Name 的規則是 \${servicename}.\${namespace}.svc.cluster.local
- endpoints: service 產生後，會關聯一個 endpoints，能從 endpoints 查到此 service 會轉發到哪些位址
  > 通常我們不關注 endpoints，只有想檢查 service selector 是否有選到正確的 Pod 時才會檢查，能透過 `kubectl get endpoints,pod -o wide`，比對 endpoints/ENDPOINTS 的 IP 是否與預期的 pod/IP 一致 

# 使用案例：
![Service](https://img1.wsimg.com/isteam/ip/ada6c322-5e3c-4a32-af67-7ac2e8fbc7ba/cluster-ip-pic.png/:/cr=t:0%25,l:0%25,w:100%25,h:100%25/rs=w:1280)
圖檔來源 [Nigel Poulton/explained-kubernetes-service-ports](https://nigelpoulton.com/explained-kubernetes-service-ports/)    

## 情境 - Kubernetes Cluster 中的內部調用
假設你有購物車 Pod (綠色) 與 支付服務 Pod(藍色)，當用戶付款時，購物車 Pod 需要調用 支付服務 Pod 才能完成結帳作業。

## 配置
1. 透過 Deployment 部署 Pod 們，並用 Label 標示出不同的服務
  例如: 
    購物車 Pod 擁有 `app:shopping-cart-api` 的 label
    支付服務 Pod 擁有 `app:payment-api` 的 label
2. 建立一個 Service 命名為 `payment-api` 且 `selector` 為 `app:payment-api`，代表可以轉發給任何 擁有 `app:payment-api` 的 label 的 Pod，也就是 支付服務 Pod。
  
## 調用
  購物車 Pod 調用 支付服務 Pod 時，需使用 DNS Name 當 target，假設此 Service 的 namespace 為 apps，則 DNS Name 為 `payment-api.apps.svc.cluster.local`。   
  ```
  curl http://payment-api.apps.svc.cluster.local:8080
  ````

  任何一個 支付服務 Pod 的 80 port 都可能會收到請留流量
  > 精確一點來說，只有 Ready 屬性為 ture 的 Pod 可能會收到流量，為 false 時，通常代表 Pod 還沒啟動完成，可能是容器中的應用還沒準備好

# Service Type
除了我們上面使用的 Service 類型: `ClusterIP`，Kubernetes 還提供了 `NodePort`, `LoadBalancer` 兩種類型

## ClusterIP
![Service-ClusterIP](https://seongjin.me/content/images/2022/02/clusterip.png)
圖檔來源 [Sandeep Dinesh/Kubernetes NodePort vs LoadBalancer vs Ingress? When should I use what?](https://medium.com/google-cloud/kubernetes-nodeport-vs-loadbalancer-vs-ingress-when-should-i-use-what-922f010849e0)
ClusterIP 是最常用的 Service 类型，讓 Cluster 內部調用能有穩定的存取方式(DNS name)，大多 Pod 的調用方都是 Cluster 內部的 Pod，所以沒有外部直接存取需求的 Pod，都使用 `ClusterIP` 即可。
> DNS name 與 Service IP 是 Cluster 外部無法存取的，只有 Pod 之間能透過 DNS name 與 Service IP 互相調用。

## NodePort
![Service-NodePort](https://miro.medium.com/v2/resize:fit:720/format:webp/1*CdyUtG-8CfGu2oFC5s0KwA.png)
圖檔來源 [Sandeep Dinesh/Kubernetes NodePort vs LoadBalancer vs Ingress? When should I use what?](https://medium.com/google-cloud/kubernetes-nodeport-vs-loadbalancer-vs-ingress-when-should-i-use-what-922f010849e0)

NodePort 能將服務曝露到 Cluster Node 的指定 port，讓外部能存取該服務，通常用在測試或開發階段，讓 host 主幾能直接訪問該 Pod。

通常不建議於生產環境使用，因為
- 每個 Port 只能有一個服務使用，難以管理
- Port Range 為 30000–32767，是有限資源
- NodePort，若 Node IP 異動時，可能會影響調用方

## LoadBalancer
![Service-ClusterIP](https://miro.medium.com/v2/resize:fit:720/format:webp/1*P-10bQg_1VheU9DRlvHBTQ.png)
圖檔來源 [Sandeep Dinesh/Kubernetes NodePort vs LoadBalancer vs Ingress? When should I use what?](https://medium.com/google-cloud/kubernetes-nodeport-vs-loadbalancer-vs-ingress-when-should-i-use-what-922f010849e0)
通常只有運行於 cloud platform(如 Google/GKE, AWS/EKS) 的 Kubernetes 才會使用 `LoadBalancer` 類型，此類型依賴 cloud platform 的實現。

已 AWS/EKS 舉例，每個`LoadBalancer` 類型的 Service 都會建立一個 [AWS/ELB](https://aws.amazon.com/tw/elasticloadbalancing/)，讓你快速的對外曝露服務。

> 注意:    
> 1. 使用 `LoadBalancer` 類型的 Service 通常會產生費用，如 AWS 會依照 ELB 的費率收費
> 2. 建議檢視 雲平台 產生的 LoadBalancer 防火牆等設定是否符合你的需求

# 總結
今天，我們學習了如何使用 Kubernetes Service 提供對 Pod 的存取方式。透過 label selector 實現服務發現，我們能夠自動定位到相應的 Pod，不受 Pod 變動的影響，從而實現了解耦 Pod 與 Pod 之間的關聯性。
 

 [Label selector]: https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/