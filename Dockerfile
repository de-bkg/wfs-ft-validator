#====================================================
# wfs-validator Docker Image
#====================================================

FROM openjdk:10
MAINTAINER Dirk Thalheim<dirk.thalheim@bkg.bund.de>

ENV VALIDATOR_HOME=/opt/wfs-ft-validator

RUN  apt-get -y update \
     && apt-get clean \
     && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*
     
ADD build/install/wfs-ft-validator $VALIDATOR_HOME


ENTRYPOINT ["/opt/wfs-ft-validator/bin/wfs-ft-validator"]
