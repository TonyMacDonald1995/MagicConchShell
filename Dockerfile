FROM openjdk:8-jdk-alpine
ADD https://github.com/TonyMacDonald1995/MagicConchShell/releases/latest/download/MagicConchShell.jar /opt
WORKDIR /opt
ENTRYPOINT [ "java", "-jar", "MagicConchShell.jar" ]