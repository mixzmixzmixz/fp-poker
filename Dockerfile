FROM openjdk:latest
WORKDIR /opt/docker
ADD --chown=daemon:daemon backend/target/docker/stage/opt /opt
USER daemon

EXPOSE 8080

ENTRYPOINT ["/opt/docker/bin/mixzpokerbackend"]
CMD []
