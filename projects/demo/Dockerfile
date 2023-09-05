FROM arm64v8/eclipse-temurin:17.0.8.1_1-jre-ubi9-minimal

WORKDIR /app

# 創建一個非 root 用戶
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# 切換到用戶 appuser
USER appuser

COPY target/demo.jar /app/application.jar

# 定義容器啟動命令，運行 JAR 文件
CMD ["java", "-jar", "application.jar"]

# 暴露 8080 端口
EXPOSE 8080