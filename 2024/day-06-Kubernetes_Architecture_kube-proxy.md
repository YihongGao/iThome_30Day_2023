
# Day-06-Kubernetes Architecture 介紹 - kube-proxy

# 前言
前兩天我們認識了建立 Pod 的指令背後，在 kubernetes 中發生的一連串處理機制。
今天會介紹在該機制中沒有出現，但仍然是不可或缺的組件: `kube-proxy`，並實際到 worker node 中檢視實現原理。

在介紹 kube-proxy 之前，要先回顧一下 Kubernetes - [Service]

# 爲什麼需要 Service
**因為 Pod IP 是不穩定的**，當 Pod 被建立時，會由 CNI (Container Network Interface) 分配一個內部 IP 給 Pod 提供其他 Pod 存取，稱為 Pod IP。

但 Pod 隨時可能會被銷毀或創建，而每次 Pod 被分配到的 Pod IP 可能是不同的，所以使用 Pod IP 來存取 Pod 並不是一個好主意。

所以通常會透過 [Service] 來作為 Pod 的存取端點，Service 也會被分配到一個 Cluster IP 並向 kubernetes 中的 DNS 服務註冊 DNS Name，讓要存取 Pod 時，只要向 DNS Name 發送網路請求即可，不用擔心 Cluster Ip 或 Pod IP 有異動。

而 Control Plane 會替 Service 建立一個 [Endpoints] 的資源，將 Service 中 Label selector 找到的 Pod IP 關鏈起來儲存於 etcd。
![https://miro.medium.com/v2/resize:fit:720/format:webp/0*UgqBgcH4W2maPnbY.png](https://miro.medium.com/v2/resize:fit:720/format:webp/0*UgqBgcH4W2maPnbY.png)

> 📘 關於 [Service] 的使用方式能參考[官方文件](https://kubernetes.io/docs/concepts/services-networking/service/) 或是 筆者去年的[分享](https://ithelp.ithome.com.tw/articles/10323802)

簡單來說，當 [Service] 與 [Endpoints] 建立後，Kubernetes 中具有以下連線資訊
- DNS 服務中有 Service 的 `DNS name` 與其 `ClusterIP`
- etcd 中有 `Cluster IP` 與其關聯的 `Pod IP`

所以穩定存取 Pod 的方式，能透過 DNS name 向 DNS 服務找到 Cluster IP，並對 Cluster IP 發送請求，由 Cluster IP 做爲 Pod 接收流量的位址。
![https://miro.medium.com/v2/resize:fit:720/format:webp/0*JEGbAlXFEFgASFrq.png](https://miro.medium.com/v2/resize:fit:720/format:webp/0*JEGbAlXFEFgASFrq.png)

但是誰負責處理 Cluster IP 收到的流量，將其進行轉發、負載均衡到 Pod 上？    
這就是由 `kube-proxy` 發揮作用的時刻了。

# kube-proxy
`kube-proxy` 負責維護每個 Node 上的網路流量轉發規則，簡單來說就是透過 `iptables` 或 `IPVS` 等實現，將送往 `Cluster IP` 的流量轉發與負載均衡到 Pod IP。
每個 `kube-proxy` 會定期向 `kube-apiserver` 查詢，當發現 [Endpoints] 有新增或刪減時，會自動調整 `iptables` 或 `IPVS` 的配置來確保流量分配正確。

## kube-proxy 定期透過 kube-apiserver 感知 Endpoints 的變化
![https://community.ops.io/images/-HbYdCD1tXhJmGIbIc75AcMfqUPx_MEcpZYIqecvYYU/w:880/mb:500000/ar:1/aHR0cHM6Ly9yZXMu/Y2xvdWRpbmFyeS5j/b20vdWFzYWJpL2lt/YWdlL3VwbG9hZC92/MTY3NDE5NDYwMy90/aHJlYWRzL2t1YmUt/cHJveHktY2x1c3Rl/cmlwLTExLnBuZw](https://community.ops.io/images/-HbYdCD1tXhJmGIbIc75AcMfqUPx_MEcpZYIqecvYYU/w:880/mb:500000/ar:1/aHR0cHM6Ly9yZXMu/Y2xvdWRpbmFyeS5j/b20vdWFzYWJpL2lt/YWdlL3VwbG9hZC92/MTY3NDE5NDYwMy90/aHJlYWRzL2t1YmUt/cHJveHktY2x1c3Rl/cmlwLTExLnBuZw)
圖檔來源 : [Learn why you can't ping a Kubernetes service]

## 依據 Endpoints 變化配置 iptables or IPVS
![https://community.ops.io/images/xSn5oj4BXJQmIKSYmmnGEf7IUM6z7exgblIdnhZEG5I/w:880/mb:500000/ar:1/aHR0cHM6Ly9yZXMu/Y2xvdWRpbmFyeS5j/b20vdWFzYWJpL2lt/YWdlL3VwbG9hZC92/MTY3NDE5NDYwMy90/aHJlYWRzL2t1YmUt/cHJveHktY2x1c3Rl/cmlwLTEyLnBuZw](https://community.ops.io/images/xSn5oj4BXJQmIKSYmmnGEf7IUM6z7exgblIdnhZEG5I/w:880/mb:500000/ar:1/aHR0cHM6Ly9yZXMu/Y2xvdWRpbmFyeS5j/b20vdWFzYWJpL2lt/YWdlL3VwbG9hZC92/MTY3NDE5NDYwMy90/aHJlYWRzL2t1YmUt/cHJveHktY2x1c3Rl/cmlwLTEyLnBuZw)
圖檔來源 : [Learn why you can't ping a Kubernetes service]

現在我們透過 [kind] 在本地端建立一個 kubernetes 環境，依 `iptables` mode 為例，看 `kube-proxy` 對 `iptables` 做了什麼配置

## 建立 Kubernetes 環境 與 部署 Deployment、Service
```
# 建立 Kubernetes 環境
kind create cluster --name ithome-2024 --config kind-config.yaml

# 建立 namespace
kubectx create ns ithome

# 建立 Deployment 與要求 3 個 Pod
kubectl create -n ithome deployment nginx --image=nginx:latest --replicas=3

# 建立 service 
kubectl expose -n ithome deployment nginx --port=8080 --target-port=80
```

## 檢視 Pod IP、Cluster IP 與 ENDPOINTS 資訊
```
kubectl get -n ithome pod,svc,ep -o wide
NAME                         READY   STATUS    RESTARTS   AGE     IP           NODE                  NOMINATED NODE   READINESS GATES
pod/nginx-7584b6f84c-2z6z9   1/1     Running   0          78m     10.244.1.2   ithome-2024-worker2   <none>           <none>
pod/nginx-7584b6f84c-pjbkm   1/1     Running   0          78m     10.244.2.2   ithome-2024-worker    <none>           <none>
pod/nginx-7584b6f84c-wcq92   1/1     Running   0          2m11s   10.244.2.3   ithome-2024-worker    <none>           <none>

NAME            TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)    AGE   SELECTOR
service/nginx   ClusterIP   10.96.191.108   <none>        8080/TCP   73m   app=nginx

NAME              ENDPOINTS                                   AGE
endpoints/nginx   10.244.1.2:80,10.244.2.2:80,10.244.2.3:80   73m
```
此 Kubernetes namespace 中有以下資源
- 3 個 Pod，Pod IP 分別為 `10.244.1.2`、`10.244.2.2`、`10.244.2.3`
- 1 個 Service，Cluster IP 為 `10.96.191.108`
- 1 個 Endpoints 對應到上述 3 個 Pod Ip 與 port

我們進入到 Worker Node 中看看 `kube-proxy` 對 `iptables` 進行了什麼配置
 ```
# 因 kind 是透過 container 在本地建立 kubernetes cluster，所以能透過 docker exec 進入 node 環境

docker ps
CONTAINER ID   IMAGE                     COMMAND                  CREATED      STATUS                PORTS                                                             NAMES
5f9a3cf1d9f7   kindest/node:v1.30.0      "/usr/local/bin/entr…"   6 days ago   Up 6 days                                                                               ithome-2024-worker2
ef7cab04410c   kindest/node:v1.30.0      "/usr/local/bin/entr…"   6 days ago   Up 6 days             0.0.0.0:30000-30001->30000-30001/tcp, 127.0.0.1:59704->6443/tcp   ithome-2024-control-plane
865219ff586e   kindest/node:v1.30.0      "/usr/local/bin/entr…"   6 days ago   Up 6 days                                                                               ithome-2024-worker

# 進入任一 worker node

docker exec -it 865219ff586e /bin/bash
# root@ithome-2024-worker:/#

# 開始探索 iptables
# iptables -t nat -L -n -v 能列出 NAT 表中所有規則
# 現在我們專注找 Cluster IP(`10.96.191.108`) 收到流量時，如何轉發流量

iptables -t nat -L KUBE-SERVICES -n -v | grep 10.96.191.108
 pkts bytes target     prot opt in     out     source               destination 
    0     0 KUBE-SVC-V6MXQNFYUC7YNW7B  6    --  *      *       0.0.0.0/0            10.96.191.108        /* ithome/nginx cluster IP */ tcp dpt:8080
 ```

能看到在 `KUBE-SERVICES` 的規則鏈中找到 1 條轉發規則
`KUBE-SVC-V6MXQNFYUC7YNW7B`
- source: `0.0.0/0` (代表任何來源 IP)
- destination: `10.96.191.108` (nginx 這個 service 的 Cluster IP)
- protocol: tcp
- port: 8080

意思是當流量的目的地址是 10.96.191.108，並且流量使用的是 TCP 與 8080 port 時，這些流量會被轉發到 `KUBE-SVC-V6MXQNFYUC7YNW7B` 鏈進一步處理。

## 檢視下一段轉發規則
```
iptables -t nat -L KUBE-SVC-V6MXQNFYUC7YNW7B -n -v
Chain KUBE-SVC-V6MXQNFYUC7YNW7B (1 references)
 pkts bytes target     prot opt in     out     source               destination         
    0     0 KUBE-MARK-MASQ  6    --  *      *      !10.244.0.0/16        10.96.191.108        /* ithome/nginx cluster IP */ tcp dpt:8080
    0     0 KUBE-SEP-7DAGYOML5QKPWFSL  0    --  *      *       0.0.0.0/0            0.0.0.0/0            /* ithome/nginx -> 10.244.1.2:80 */ statistic mode random probability 0.33333333349
    0     0 KUBE-SEP-YJAODCWU7PX6K2OG  0    --  *      *       0.0.0.0/0            0.0.0.0/0            /* ithome/nginx -> 10.244.2.2:80 */ statistic mode random probability 0.50000000000
    0     0 KUBE-SEP-DYRIFX6ZVNZI7BRA  0    --  *      *       0.0.0.0/0            0.0.0.0/0            /* ithome/nginx -> 10.244.2.3:80 */
```
能看到 `KUBE-SVC-V6MXQNFYUC7YNW7B` 中配置了四條規則
- `KUBE-MARK-MASQ`: 用來標記外部流量，確保 Response 時能正確返回來源，這邊不討論此規則細節。
- `KUBE-SEP-7DAGYOML5QKPWFSL`: 
    - source: `0.0.0/0` (代表任何來源 IP)
    - destination: `10.244.1.2:80` (其中一個 Pod IP)
    - 選擇機率: 0.33333333349
- `KUBE-SEP-YJAODCWU7PX6K2OG`
    - source: `0.0.0/0` (代表任何來源 IP)
    - destination: `10.244.2.2:80` (其中一個 Pod IP)
    - 選擇機率: 0.50000000000 (50%)
- `KUBE-SEP-DYRIFX6ZVNZI7BRA`
    - source: `0.0.0/0` (代表任何來源 IP)
    - destination: `10.244.2.3:80` (其中一個 Pod IP)
    - 選擇機率: 未配置 (100%)

當流量被分配到  `KUBE-SVC-V6MXQNFYUC7YNW7B` 這規則鏈時，會依序對每條轉發規則進行處理
1. 當流量來自外部時，對流量進行標記 (`KUBE-MARK-MASQ`)
2. 進行流量轉發判斷，33% 機率將流量轉發到 `10.244.1.2:80` Pod IP(`KUBE-SEP-7DAGYOML5QKPWFSL`)，若未命中則進行下一個規則判斷
3. 進行流量轉發判斷，50% 機率將流量轉發到 `10.244.2.2:80` Pod IP(`KUBE-SEP-YJAODCWU7PX6K2OG`)，若未命中則進行下一個規則判斷
    > 為了讓每條分配機率公平，因為 33% 的判斷未命中才會進入此規則，故此規則分配機率應是 (100% - 33%) * 0.5，與前一條規則的機率 (33%) 很接近。
4. 進行流量轉發判斷，將流量轉發到 `10.244.2.3:80` Pod IP(`KUBE-SEP-DYRIFX6ZVNZI7BRA`)


以上就是 `kube-proxy` 透過 iptables 實現對 Cluster IP 收到的流量進行負載均衡，並轉發到 Pod IP 的原理。
> 📘 有興趣的讀者，能刪除一個 Pod 或 移除 Service，再來看看 `iptables` 發生什麼變化。

# 小結
今天介紹了 `kube-proxy` 與 DNS 服務如何合作，實現 [Service] 的功能，提供穩定的調用端點 與 負載均衡。
![https://cilium.io/static/7720169a677cd13bbad2b9c431d560d8/1ab28/ogimage.webp](https://cilium.io/static/7720169a677cd13bbad2b9c431d560d8/1ab28/ogimage.webp)
圖檔來源 : [Debugging and Monitoring DNS issues in Kubernetes]
 
明天會在對 Pod 的實現進行延伸介紹。


# Refernce
- [Kube-Proxy: What Is It and How It Works](https://kodekloud.com/blog/kube-proxy/)
- [what-happens-when-you-create-a-pod-in-kubernetes](https://itnext.io/what-happens-when-you-create-a-pod-in-kubernetes-6b789b6db8a8)
- [kube-proxy與iptables](https://barry-cheng.medium.com/kube-proxy%E8%88%87iptables-baeec63c808b)


[Service]: https://kubernetes.io/docs/concepts/services-networking/service/

[Endpoints]: https://kubernetes.io/zh-cn/docs/concepts/services-networking/service/#endpoints

[kind]: https://kind.sigs.k8s.io/

[Learn why you can't ping a Kubernetes service]: https://community.ops.io/danielepolencic/learn-why-you-cant-ping-a-kubernetes-service-2gog

[Debugging and Monitoring DNS issues in Kubernetes]: https://cilium.io/blog/2019/12/18/how-to-debug-dns-issues-in-k8s/