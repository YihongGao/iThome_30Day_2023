
# Day-01-大綱與架構

# 前言
再 [2023 年的鐵人賽系列的分享](https://ithelp.ithome.com.tw/users/20147637/ironman/6738)中，我們介紹了軟體開發者最常使用的 Kubernetes 組件，並透過 Gitlab CI 實現 GitOps，能一鍵部署資源上 Kubernetes，並透過 Observability Tool 觀測服務的 Log、Metrics、Tracing。
![architecture](https://cdn.jsdelivr.net/gh/YihongGao/picx-images-hosting@master/20230913/架構圖.5holmyq61hg0.webp)

在本系列中，我們將接續著介紹更多 Kubernetes 的應用 與 改善 CI/CD 流程，分為兩個章節
- 介紹更多 Kubernetes 組件 與 功能
  - Kubernetes 的架構與核心組件
  - Pod 部署到 Kubernetes 的旅程
  - 如何分派你的 Pod 
    - Node affinity
    - Inter podAffinity
    - Pod topology spread constraints
  - 如何提高 Pod 的可用性
  - 如何管理 Pod 的網路安全
    - Network policy
- DevOps in Kubernetes
  - 更完整的 GitOps 部署方案
    - ArgoCD
  - 藍綠部署、金絲雀部署方案
    - ArgoRollout
    - flipt
  - DevSecOps 提高系統安全性

不過近期筆者使用的生態系改到 Github 與 Google cloud plaform，故預計最終完成的架構會改如下
![architecture](https://github.com/YihongGao/picx-images-hosting/raw/master/20240818/架構圖.1.4qr8wr02xp.webp)

P.S 本次 Observability 相關的 Tool 篇幅較少，著重在增強 CI/CD 與 發佈策略的方案，故對 Observability 有興趣的能參考上屆 [Cloud Native 組冠軍的優質文章](https://ithelp.ithome.com.tw/users/20162175/ironman/6445)，或想快速的感受 Observability 解決方案能參考[筆者上屆的分享](https://ithelp.ithome.com.tw/articles/10335722)。

希望能幫助讀者能認識 Kubernetes 的更多應用方式，來面對更多生產環境的挑戰。


