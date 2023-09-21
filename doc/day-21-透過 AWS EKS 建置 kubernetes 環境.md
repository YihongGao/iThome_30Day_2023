# Day-21-透過 AWS EKS 建置 kubernetes 環境

# 前言
在本地學習 kubernetes 的核心組件之後，讓我們建立個 AWS EKS(Elastic Kubernetes Service)，方便後續進行 CI/CD、監控等介紹。

> 注意！建立 AWS EKS 的服務可能產生 AWS 平台相關費用，建議能先查詢讀者適合的 region 與 費率。

# 前置準備
## 需先安裝以下工具
- kubectl: Kubectl 是一種命令列工具，可用來與 Kubernetes API 伺服器進行通訊。[安裝指南](https://kubernetes.io/docs/tasks/tools/)
> kubectl 需與 EKS 的 Kubernetes 版本差距不超過 1 個版本。EKS Kubernetes 版本為 1.25 的話，可使用 kubectl 1.24 ~ 1.26
- AWS CLI: AWS CLI 是一種命令列工具，是 AWS 通用的 CLI 工具，可用來管理與互動 AWS 平台上的服務，而無需使用 AWS GUI[安裝指南](https://docs.aws.amazon.com/zh_tw/cli/latest/userguide/getting-started-install.html)
- eksctl: eksctl 是一種命令列工具，專門用來建立和管理 Amazon EKS 上的 Kubernetes 叢集。[安裝指南](https://docs.aws.amazon.com/zh_tw/eks/latest/userguide/eksctl.html)

## 權限設定
當工具都安裝好之後，需將 AWS IAM User 的 `Access Key` 與 `Secret Access Key` 配置到 AWS CLI 中。
1. 登入 AWS 平台，至 IAM 介面，建立 User
![ekscli-user](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-20-下午8.04.25-2.6byzowpkzts0.webp)
權限能參考官方建議的[最小權限](https://eksctl.io/usage/minimum-iam-policies/#)來配置或用管理者權限。
2. 建立 User 完成後，於 Users 清單點選該 User name，進入設定頁面後，選擇 `security_credentials` 頁籤，並於下方 `Access Keys` 區域選擇 **Create access Key**
![create access key](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-20-下午8.11.44.6cu7d2lu68c0.webp)
3. Use case 選 `Command Line Interface (CLI)` 後選 Next 直到 Create access key
4. 頁面上會出現 `Access key` 與 `Secret access key`，將兩個值複製起來
![take access key](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230919/截圖-2023-09-20-下午8.13.35.21ujm2fg2eg0.webp)
> 注意：
> 1. 任何人都能使用 `Access key` 與 `Secret access key` 代表你的帳號操作 AWS 平台，請不要外洩此值。    
> 2. `Secret access key` 只能在這時候複製，離開此畫面後就查詢不到了。

5. 使用 AWS CLI 將  `Access key` 與 `Secret access key` 配置進去，開啟 terminal（終端機）輸入以下指令
```
aws configure
```
填入 `Access key`、`Secret access key`、預設 region name、習慣的 output format
```
aws configure     
AWS Access Key ID [****************J26O]: 
AWS Secret Access Key [****************hUcs]: 
Default region name [ap-northeast-1]: 
Default output format [json]: 
```
這樣就完成權限設定了

## 建立 EKS cluster
能執行以下指令
```
eksctl create cluster \
--name ithome-demo \
--region ap-northeast-1 \
--node-type t3.small \
--nodes-min 1 \
--nodes-max 3 \
--node-volume-size 20 \
--spot
```
各參數介紹，讀者可自行調整
- `name`: EKS cluster 的名稱
- `region`: EKS cluster 要運行在哪個 region
- `node-type`: Kubernetes 節點類型
- `nodes-min`: 節點最小數量
- `nodes-max`: 節點最大數量
- `spot`: 使用 AWS [Spot Instances] 來節省成本(https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/using-spot-instances.html)

建立 EKS 需要花費幾分鐘的時間，建立完成能看到 `2023-09-20 22:30:37 [✔]  EKS cluster "ithome-demo" in "ap-northeast-1" region is ready` 的 Log
```
2023-09-20 22:17:51 [ℹ]  eksctl version 0.157.0
2023-09-20 22:17:51 [ℹ]  using region ap-northeast-1
2023-09-20 22:17:51 [ℹ]  setting availability zones to [ap-northeast-1a ap-northeast-1d ap-northeast-1c]
2023-09-20 22:17:51 [ℹ]  subnets for ap-northeast-1a - public:192.168.0.0/19 private:192.168.96.0/19
2023-09-20 22:17:51 [ℹ]  subnets for ap-northeast-1d - public:192.168.32.0/19 private:192.168.128.0/19
2023-09-20 22:17:51 [ℹ]  subnets for ap-northeast-1c - public:192.168.64.0/19 private:192.168.160.0/19
2023-09-20 22:17:51 [ℹ]  nodegroup "ng-cf3fffb0" will use "" [AmazonLinux2/1.25]
2023-09-20 22:17:51 [ℹ]  using Kubernetes version 1.25
2023-09-20 22:17:51 [ℹ]  creating EKS cluster "ithome-demo" in "ap-northeast-1" region with managed nodes
2023-09-20 22:17:51 [ℹ]  will create 2 separate CloudFormation stacks for cluster itself and the initial managed nodegroup
2023-09-20 22:17:51 [ℹ]  if you encounter any issues, check CloudFormation console or try 'eksctl utils describe-stacks --region=ap-northeast-1 --cluster=ithome-demo'
2023-09-20 22:17:51 [ℹ]  Kubernetes API endpoint access will use default of {publicAccess=true, privateAccess=false} for cluster "ithome-demo" in "ap-northeast-1"
2023-09-20 22:17:51 [ℹ]  CloudWatch logging will not be enabled for cluster "ithome-demo" in "ap-northeast-1"
2023-09-20 22:17:51 [ℹ]  you can enable it with 'eksctl utils update-cluster-logging --enable-types={SPECIFY-YOUR-LOG-TYPES-HERE (e.g. all)} --region=ap-northeast-1 --cluster=ithome-demo'
2023-09-20 22:17:51 [ℹ]  
2 sequential tasks: { create cluster control plane "ithome-demo", 
    2 sequential sub-tasks: { 
        wait for control plane to become ready,
        create managed nodegroup "ng-cf3fffb0",
    } 
}
2023-09-20 22:17:51 [ℹ]  building cluster stack "eksctl-ithome-demo-cluster"
2023-09-20 22:17:57 [ℹ]  deploying stack "eksctl-ithome-demo-cluster"
2023-09-20 22:18:27 [ℹ]  waiting for CloudFormation stack "eksctl-ithome-demo-cluster"
2023-09-20 22:18:57 [ℹ]  waiting for CloudFormation stack "eksctl-ithome-demo-cluster"
2023-09-20 22:19:57 [ℹ]  waiting for CloudFormation stack "eksctl-ithome-demo-cluster"
2023-09-20 22:20:58 [ℹ]  waiting for CloudFormation stack "eksctl-ithome-demo-cluster"
2023-09-20 22:21:58 [ℹ]  waiting for CloudFormation stack "eksctl-ithome-demo-cluster"
2023-09-20 22:22:58 [ℹ]  waiting for CloudFormation stack "eksctl-ithome-demo-cluster"
2023-09-20 22:23:59 [ℹ]  waiting for CloudFormation stack "eksctl-ithome-demo-cluster"
2023-09-20 22:24:59 [ℹ]  waiting for CloudFormation stack "eksctl-ithome-demo-cluster"
2023-09-20 22:25:59 [ℹ]  waiting for CloudFormation stack "eksctl-ithome-demo-cluster"
2023-09-20 22:28:02 [ℹ]  building managed nodegroup stack "eksctl-ithome-demo-nodegroup-ng-cf3fffb0"
2023-09-20 22:28:03 [ℹ]  deploying stack "eksctl-ithome-demo-nodegroup-ng-cf3fffb0"
2023-09-20 22:28:03 [ℹ]  waiting for CloudFormation stack "eksctl-ithome-demo-nodegroup-ng-cf3fffb0"
2023-09-20 22:28:33 [ℹ]  waiting for CloudFormation stack "eksctl-ithome-demo-nodegroup-ng-cf3fffb0"
2023-09-20 22:29:19 [ℹ]  waiting for CloudFormation stack "eksctl-ithome-demo-nodegroup-ng-cf3fffb0"
2023-09-20 22:30:36 [ℹ]  waiting for CloudFormation stack "eksctl-ithome-demo-nodegroup-ng-cf3fffb0"
2023-09-20 22:30:36 [ℹ]  waiting for the control plane to become ready
2023-09-20 22:30:36 [✔]  saved kubeconfig as "/Users/yihung.kao/.kube/config"
2023-09-20 22:30:36 [ℹ]  no tasks
2023-09-20 22:30:36 [✔]  all EKS cluster resources for "ithome-demo" have been created
2023-09-20 22:30:36 [ℹ]  nodegroup "ng-cf3fffb0" has 1 node(s)
2023-09-20 22:30:36 [ℹ]  node "ip-192-168-87-116.ap-northeast-1.compute.internal" is ready
2023-09-20 22:30:36 [ℹ]  waiting for at least 1 node(s) to become ready in "ng-cf3fffb0"
2023-09-20 22:30:36 [ℹ]  nodegroup "ng-cf3fffb0" has 1 node(s)
2023-09-20 22:30:36 [ℹ]  node "ip-192-168-87-116.ap-northeast-1.compute.internal" is ready
2023-09-20 22:30:37 [✖]  parsing kubectl version string  (upstream error: WARNING: version difference between client (1.28) and server (1.25) exceeds the supported minor version skew of +/-1
) / "0.0.0": Version string empty
2023-09-20 22:30:37 [ℹ]  cluster should be functional despite missing (or misconfigured) client binaries
2023-09-20 22:30:37 [✔]  EKS cluster "ithome-demo" in "ap-northeast-1" region is ready
```

## 透過 kubectl 操作 EKS


```
# 查看 Node 
$ kubectl get node 
NAME                                                STATUS   ROLES    AGE    VERSION
ip-192-168-49-157.ap-northeast-1.compute.internal   Ready    <none>   4m1s   v1.25.12-eks-8ccc7ba

# 運行個 Pod 看看
$ kubectl run nginx --image nginx

# 查有哪些 Pod 在運行
$ kubectl get pod -A
NAMESPACE     NAME                       READY   STATUS    RESTARTS   AGE
default       nginx                      1/1     Running   0          6s
kube-system   aws-node-p9jc2             1/1     Running   0          3m35s
kube-system   coredns-7dbf6bcd5b-9clv7   1/1     Running   0          10m
kube-system   coredns-7dbf6bcd5b-q7lj9   1/1     Running   0          10m
kube-system   kube-proxy-f59d6           1/1     Running   0          3m35s
```

# 總結
今天我們透過了 eksctl 建立了一個 EKS cluster，並能從本地 kubectl 操作該 cluster，後續的介紹會繼續使用 EKS 環境來操作。

# 清理環境 - 刪除 EKS cluster
EKS 使用期間會有費用產生，讀者可使用以下指令刪除 EKS 環境避免持續產生費用
```
eksctl delete cluster ithome-demo
```