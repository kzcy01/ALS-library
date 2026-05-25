FROM alpine:latest

# Install OpenJDK 26 directly into a lightweight base container
RUN apk add --no-repeat openjdk26-jre-headless

WORKDIR /app

# Copy your local project files (including your compiled 'out' directory)
COPY . .

# Run the pre-compiled class file directly from the production folder path
CMD ["java", "-cp", "out/production/untitled:out", "AdvancedLibrarySystem"]