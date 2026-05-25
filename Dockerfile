FROM container-registry.oracle.com/java/openjdk:26-oraclelinux9
WORKDIR /app

# Copy all project files into the container workspace
COPY . .

# Run the pre-compiled class directly using the dual-path script
CMD ["sh", "-c", "if [ -d \"out/production/untitled\" ]; then java -cp out/production/untitled:out AdvancedLibrarySystem; else java -cp src AdvancedLibrarySystem; fi"]