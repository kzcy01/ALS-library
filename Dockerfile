FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY . .
RUN javac src/AdvancedLibrarySystem.java
# Using jshell allows us to bypass the GUI main method and boot up the Web backend directly
CMD ["jshell", "--class-path", "src", "-R-Djava.awt.headless=true", "---", "-c", "new AdvancedLibrarySystem();"]