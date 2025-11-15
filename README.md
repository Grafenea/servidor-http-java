# Servidor http Java

Um pequeno servidor http que usa Java.

Para usar requer Java 21 (deve funcionar também com o 17).

[Download do executável](https://github.com/Grafenea/servidor-http-java/releases/download/1.0/servidor.jar).

## Compilar

Requer Java JDK 21.

1 - Baixe o código fonte.

2 - Abra o terminal no diretório.

3 - Dê: 

    javac GuiFileServer.java

4 - E depois, para gerar o JAR:

    jar --create --file servidor.jar --main-class=GuiFileServer -C . .

5 - Execute o JAR, com dois cliques no arquivo, ou dê:

    java -jar servidor.jar



