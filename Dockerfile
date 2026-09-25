FROM eclipse-temurin:17-jdk AS build

WORKDIR /app
COPY *.java ./
RUN javac *.java

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build /app/*.class ./

ENV PORT=8000
EXPOSE 8000

CMD ["java", "Server"]
