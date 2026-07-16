# Etapa 1: compilar el WAR con Maven.
FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /source
COPY pom.xml .
# Descarga las dependencias primero para aprovechar la caché de capas de Docker.
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# Etapa 2: imagen de ejecución con Tomcat 10.1 (Jakarta EE 10).
FROM tomcat:10.1-jre17-temurin
# Compresión gzip en el conector HTTP (los tipos comprimibles por defecto ya incluyen
# html, css, js y json). Se elimina el contenido por defecto y la app se despliega como
# ROOT (contexto /).
# (useSendfile="off" porque sendfile omite la compresión de los archivos estáticos.)
RUN sed -i 's|<Connector port="8080" protocol="HTTP/1.1"|<Connector port="8080" protocol="HTTP/1.1"\n               compression="on" useSendfile="false"|' /usr/local/tomcat/conf/server.xml && \
    rm -rf /usr/local/tomcat/webapps/* && \
    mkdir -p /var/lib/encuestas && \
    groupadd --system tomcat && \
    useradd --system --gid tomcat --no-create-home tomcat && \
    chown -R tomcat:tomcat /usr/local/tomcat/webapps /usr/local/tomcat/work \
        /usr/local/tomcat/temp /usr/local/tomcat/logs /usr/local/tomcat/conf \
        /var/lib/encuestas
COPY --from=build --chown=tomcat:tomcat /source/target/encuestas.war /usr/local/tomcat/webapps/ROOT.war
# Usuario no root.
USER tomcat
EXPOSE 8080
CMD ["catalina.sh", "run"]
