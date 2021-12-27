FROM openjdk:latest
WORKDIR /opt/docker
ADD --chown=daemon:daemon backend/target/docker/stage/opt /opt
USER daemon
ENTRYPOINT ["/opt/docker/bin/mixzpokerbackend"]
CMD []
