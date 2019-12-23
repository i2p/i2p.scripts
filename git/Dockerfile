FROM debian:stretch

RUN apt-get -y update && apt-get -y install monotone
ENTRYPOINT ["mtn"]
VOLUME /mtn
WORKDIR /mtn
CMD ["--help"]

