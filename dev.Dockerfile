# Stage 1: Build (using GraalVM)
FROM ghcr.io/graalvm/native-image-community:21 AS build

ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

ENV JAVA_HOME=/usr/lib64/graalvm/graalvm-community-java21
ENV PATH=$JAVA_HOME/bin:$PATH

RUN microdnf install binutils xz -y 

RUN curl -L -f -o upx.tar.xz https://github.com/upx/upx/releases/download/v4.2.4/upx-4.2.4-amd64_linux.tar.xz \
    && tar -xJf upx.tar.xz \
    && mv upx-4.2.4-amd64_linux/upx /usr/local/bin/ \
    && rm -rf upx.tar.xz upx-4.2.4-amd64_linux

WORKDIR /apps

COPY .mvn/ .mvn
COPY mvnw pom.xml ./

RUN chmod +x mvnw && ./mvnw dependency:go-offline

COPY src ./src

RUN ./mvnw -Pnative native:compile -DskipTests  

RUN strip /apps/target/process-executable
RUN upx --best /apps/target/process-executable

# Stage 2: Runtime (Ultra-lightweight)

##FROM gcr.io/distroless/cc-debian12

## FROM debian:bookworm-slim !!!! before update like Rust
FROM gcr.io/distroless/cc
##FROM bitnami/minideb:bookworm

WORKDIR /apps

#RUN apk add --no-cache wget  && \
#    addgroup -S spring && adduser -S spring -G spring

#USER spring:spring

#COPY --from=build /apps/target/*.jar process.jar

##COPY --from=build /lib/x86_64-linux-gnu/libz.so.1 /lib/x86_64-linux-gnu/libz.so.1

COPY --from=build /apps/target/process-executable ./process-executable

##RUN install_packages wget

EXPOSE 8080

##HEALTHCHECK --interval=30s --timeout=3s --retries=3 --start-period=30s \
##  CMD wget -qO- http://localhost:8080/actuator/health | grep UP || exit 1

HEALTHCHECK --interval=30s --timeout=3s --retries=3 --start-period=10s \
  CMD ["/healthchecks/Distroless.HealthChecks", "--uri", "http://localhost:8080/actuator/health"]


#ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "process.jar"]
ENTRYPOINT ["./process-executable"]