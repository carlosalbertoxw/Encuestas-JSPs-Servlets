# Etapa 1: compilar el WAR con el Maven Wrapper del repositorio (misma versión de Maven que en local y en CI).
FROM eclipse-temurin:25-jdk AS build
WORKDIR /source
COPY mvnw pom.xml ./
COPY .mvn .mvn
# Descarga las dependencias primero para aprovechar la caché de capas de Docker.
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY config ./config
COPY src ./src
RUN ./mvnw -B -q package -DskipTests -Dcheckstyle.skip -Dspotbugs.skip
# Sonda del HEALTHCHECK compilada aquí: el JRE de la imagen final no trae el compilador.
COPY docker/HealthCheck.java ./docker/
RUN javac -d /healthcheck docker/HealthCheck.java

# Etapa 2: imagen de ejecución con Tomcat 11 (Jakarta EE 11) sobre Java 25.
FROM tomcat:11.0-jre25-temurin
# Compresión gzip en el conector HTTP (los tipos comprimibles por defecto ya incluyen
# html, css, js y json). Se elimina el contenido por defecto y la app se despliega como
# ROOT (contexto /).
# (useSendfile="off" porque sendfile omite la compresión de los archivos estáticos.)
# También se aplican los parches de seguridad del sistema base y se eliminan las apps de
# ejemplo de Tomcat (webapps.dist), que no se usan.
RUN apt-get update && apt-get upgrade -y --no-install-recommends && rm -rf /var/lib/apt/lists/* && \
    sed -i 's|<Connector port="8080" protocol="HTTP/1.1"|<Connector port="8080" protocol="HTTP/1.1"\n               compression="on" useSendfile="false"|' /usr/local/tomcat/conf/server.xml && \
    rm -rf /usr/local/tomcat/webapps/* /usr/local/tomcat/webapps.dist && \
    mkdir -p /var/lib/encuestas && \
    groupadd --system tomcat && \
    useradd --system --gid tomcat --no-create-home tomcat && \
    chown -R tomcat:tomcat /usr/local/tomcat/webapps /usr/local/tomcat/work \
        /usr/local/tomcat/temp /usr/local/tomcat/logs /usr/local/tomcat/conf \
        /var/lib/encuestas
COPY --from=build /healthcheck /usr/local/lib/healthcheck
COPY --from=build --chown=tomcat:tomcat /source/target/encuestas.war /usr/local/tomcat/webapps/ROOT.war
# Usuario no root.
USER tomcat
EXPOSE 8080
# Sin curl en la imagen: la sonda es una clase Java que consulta /health/live.
HEALTHCHECK --interval=15s --timeout=10s --start-period=60s --retries=3 \
    CMD ["java", "-cp", "/usr/local/lib/healthcheck", "HealthCheck"]
CMD ["catalina.sh", "run"]
