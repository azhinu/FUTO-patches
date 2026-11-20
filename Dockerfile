# Android's Linux build tools require x86-64 (emulated on ARM hosts).
FROM --platform=linux/amd64 ubuntu:24.04

COPY scripts/setup-container.sh /tmp/setup-container.sh
RUN bash /tmp/setup-container.sh && rm /tmp/setup-container.sh

ENV MORPHE_CLI=/opt/morphe/morphe-cli.jar
WORKDIR /project
CMD ["bash"]
