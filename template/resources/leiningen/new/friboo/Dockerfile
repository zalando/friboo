FROM java:openjdk-8u91-jre
COPY target/uberjar/{{name}}.jar /{{name}}.jar
COPY scm-source.json /scm-source.json
ENTRYPOINT ["java", "-jar", "/{{name}}.jar"]
