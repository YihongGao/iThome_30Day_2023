# Day-11-Kubernetes_介紹-Ingress 

# 前言
昨天我們介紹了 Service，解決了 Pod 動態變動的問題，但 Service type `ClusterIP` 只能有內部服務(Pod) 能調用，提供外部訪問的 Service 只有 `NodePort` 或 `LoadBalancer`，但分別會造成 管理問題 或 成本問題。
今天我們來介紹如何透過 Ingress 解決提供外部訪問的需求。

# 什麼是 Ingress
![Ingress](https://miro.medium.com/v2/resize:fit:720/format:webp/1*KIVa4hUVZxg-8Ncabo8pdg.png)
圖檔來源 [Sandeep Dinesh/Kubernetes NodePort vs LoadBalancer vs Ingress? When should I use what?](https://medium.com/google-cloud/kubernetes-nodeport-vs-loadbalancer-vs-ingress-when-should-i-use-what-922f010849e0)

Ingress 能提供對外統一的訪問位址，來將流量轉發給 Service，轉發的規則可以依據 HTTP/ HTTPS request 的 `host` 或 `URL path` 來配置。

以上圖為例，Ingress 收到 request 時，轉發規則如下
  - 若 request host 為 `foo.mydomain.com`，會將 request 轉發給 __左邊__ 的 Service，Service 負責進行負載均衡並將 request 轉發給 Pod。
  - 若 request host 為 `mydomain.com` 且 URL path 為 `/bar`，會將 request 轉發給 __中間__ 的 Service，Service 負責進行負載均衡並將 request 轉發給 Pod。
  - 若 request host 的 host 與 URL path 皆為符合上述兩個規則時，將 request 轉發給 __右邊__ 的 Service，Service 負責進行負載均衡並將 request 轉發給 Pod。

這些 Service 可以是任意 tpye，但通常最好管理且無額外成本的就是 `ClusterIP`，也就避免 `NodePort` 難以管理 與 使用大量 `LoadBalancer` 成本昂貴的問題。

# 前置作業
Ingress 是屬於 Kubernetes 中的高階抽象組件，他定義了路由轉發的規則，但需要自行安裝實現路由轉發的組件，我們稱為 [Ingress controller](https://kubernetes.io/docs/concepts/services-networking/ingress-controllers/)，你可以依據需求或組織熟悉的組件來選擇。

今天我們使用目前主流之一的 [NGINX Ingress Controller](https://docs.nginx.com/nginx-ingress-controller/) 來介紹

## 安裝 Ingress controller
安裝 [NGINX Ingress Controller](https://docs.nginx.com/nginx-ingress-controller/)
```
# 查 worker node name
kubectl get node

# 重要! 
# 要替 worker node 打上 ingress-ready: "true" 的 label, 讓 controller pod 能被調度
kubectl label nodes ${you workder node name}

# 安裝官方提供給 kind 環境安裝的 nginx ingress controller yaml
kubectl apply --filename https://raw.githubusercontent.com/kubernetes/ingress-nginx/master/deploy/static/provider/kind/deploy.yaml

# 查詢 nginx ingress controller 建置是否完成
kubectl get deployment,svc,pod -n ingress-nginx
```

返回結果中能看到 ingress-nginx-controller 的 Pod 為 Running。

```
NAME                                       READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/ingress-nginx-controller   1/1     1            1           36m

NAME                                         TYPE        CLUSTER-IP    EXTERNAL-IP   PORT(S)                      AGE
service/ingress-nginx-controller             NodePort    10.96.3.61    <none>        80:30000/TCP,443:30001/TCP   36m
service/ingress-nginx-controller-admission   ClusterIP   10.96.56.18   <none>        443/TCP                      36m

NAME                                            READY   STATUS      RESTARTS   AGE
pod/ingress-nginx-admission-create-f9crk        0/1     Completed   0          36m
pod/ingress-nginx-admission-patch-nwrd8         0/1     Completed   1          36m
pod/ingress-nginx-controller-67d4779649-bvkv9   1/1     Running     0          36m
```

## 曝露 ingress 到 host 主機
安裝完之後，我想直接從本地主機透過 domain 連上 ingress，這時還需要調整一下 service/ingress-nginx-controller 的 NodePort Number，需使用 kind 的 container 映射到本地主機的 Port。
> 以下是 kind 環境需要做的配置，若讀者其他 Kubernetes 環境，能參考 [nginx 官網](https://kubernetes.github.io/ingress-nginx/deploy/)。

```
# 查 kind 的 container 映射到本地主機的 Port
docker ps | grep kindest
```

能看到我的 worker node 的 PORTS 欄位曝露了 30000, 30001 Port
```
CONTAINER ID   IMAGE                  COMMAND                  CREATED      STATUS      PORTS                                                             NAMES
f788133bc0e7   kindest/node:v1.27.3   "/usr/local/bin/entr…"   4 days ago   Up 4 days                                                                     my-cluster-worker
81471117dd64   kindest/node:v1.27.3   "/usr/local/bin/entr…"   4 days ago   Up 4 days   0.0.0.0:30000-30001->30000-30001/tcp, 127.0.0.1:56492->6443/tcp   my-cluster-control-plane
```
> Note: 若你的 kind container 沒曝露 Port 的話，可能是 create kind cluster 時沒使用 extraPortMappings 配置，能參考 **Day-07-Kubernetes_介紹-建立本地 Kubernetes** 建置 kind 環境時的的配置檔

因爲我曝露的 port 是 30000, 30001，所以透過 kubectl 將 service/ingress-nginx-controller 的 NodePort Number 改為 30000 & 30001
```
# 執行後會開啟編輯器，讓你編輯 ingress-nginx-controller 的 yaml
kubectl edit service ingress-nginx-controller -n ingress-nginx 
```

找到 `spec.ports` 的區塊，並將 nodePort 改為你曝露的 Port number
```
ports:
  - appProtocol: http
    name: http
    nodePort: 30000
    port: 80
    protocol: TCP
    targetPort: http
  - appProtocol: https
    name: https
    nodePort: 30001
    port: 443
    protocol: TCP
    targetPort: https
```

## 部署測試服務
```
# Create deployment
kubectl create deployment hello --image=nginxdemos/hello --port=80

# Create a service for deployment hello
kubectl expose deployment hello --port 80
```

## 配置 ingress 轉發規則
新增一個 hello-ingress.yaml
```
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: hello
spec:
  rules:
    - host: hello.ingress.com
      http:
        paths:
          - pathType: ImplementationSpecific
            backend:
              service:
                name: hello
                port:
                  number: 80
```

介紹幾個重要的屬性
- `rules`: 配置轉發規則的區塊，可定義多個轉發規則
  - `host`: 收到 request 指向的 host 為 `hello.ingress.com`
  - `http`: 指定針對 http 的轉發規則
    - `pathType`: 匹配規則，有 Prefix, Exact 等多種方式, nginx-ingress controller 的 ImplementationSpecific 與同 Prefix 相同含義
    - `backend`: 符合以上匹配規則後，要將請求轉到哪裡
      - `service`: service 的名稱
      - `port`: 轉發的 port number

簡單來說這個 Ingress 配置就是，收到 HTTP 請求指向 `hello.ingress.com` 時，將流量轉發給名稱為 hello 的 service 且使用 80 port.

配置 hello-ingress.yaml 到 kubernetes
```
# apply ingress
kubectl apply -f hello-ingress.yaml

# 查是否添加成功
kubectl get ingress
```
預期結果

```
NAME    CLASS    HOSTS               ADDRESS     PORTS   AGE
hello   <none>   hello.ingress.com   localhost   80      79m
```

# 開始測試
測試方式為從本地主機，透過瀏覽器或 curl 連至 `http://hello.ingress.com` 時，能收到 pod 的 response。

  1. 為了能透過 `hello.ingress.com` 連線到我們本地端的 Kubernetes，透過 vi 開啟 `/etc/hosts`
  ```
  sudo vi /etc/hosts
  ```
  2. 於 `/etc/hosts` 檔案中添加以下內容
  ```
  127.0.0.1 hello.ingress.com
  ```

配置完之後，在本地端連線至 `hello.ingress.com` 的網域時，都會解析為本地位址(127.0.0.1)，讓我們能連向本地端的 Kubernetes

透過瀏覽器連線至 `hello.ingress.com`，出現以下畫面代表成功
![hello.ingress.com](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230910/截圖-2023-09-10-上午2.07.50.32cqs1j9lu20.webp)

# 總結
我們今天學會了如何使用 Ingress 來實現對外曝露服務的需求，補足 Service 在這需求上的短板，值得補充的是 Ingress 與 Service 並不互斥，他們專注在各自的職責上，並能互補。
- Service 
  - 服務發現: 提供 Pod 的抽象概念，解決 Pod 動態位址
  - 負載均衡: 將流量均勻分配給多個 Pod，提高可用性 
- Ingress
  - 路由規則管理: 能當作外部流量的入口，藉由路由規則轉發請求
  - SSL/TLS 終止(termination): 對外的端口能使用 https 保護資料傳輸的安全性，但轉發給內部服務時使用 http，減少開發或憑證管理的複雜度。

# 參考文獻
- [How to Test Ingress in a kind cluster](https://dustinspecker.com/posts/test-ingress-in-kind/)