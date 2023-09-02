# 使用 OpenJDK 17 作為基本映像
FROM openjdk:17

# 在容器中創建一個目錄以存放應用程序 JAR 文件
WORKDIR /app

# 複製本地的 JAR 文件到容器中
COPY target/demo.jar /app/application.jar

# 定義容器啟動命令，運行 JAR 文件
CMD ["java", "-jar", "application.jar"]

# 暴露 8080 端口
EXPOSE 8080
