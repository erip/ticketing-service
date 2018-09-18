FROM hseeberger/scala-sbt:8u181_2.12.6_1.2.1

# Copy source to /app
COPY . /app

# Change to the /app directory
WORKDIR /app

# Make `sbt` the entrypoint. Any command-line args will be interpreted as sbt tasks
ENTRYPOINT ["sbt"]

# Make the default `docker run` command be `sbt run`
CMD ["runAll"]