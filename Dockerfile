#====================================================
# wfs-validator Docker Image
#====================================================

FROM openjdk:10
MAINTAINER Dirk Thalheim<dirk.thalheim@bkg.bund.de>

ENV VALIDATOR_HOME=/opt/wfs-ft-validator

COPY build/install/wfs-ft-validator $VALIDATOR_HOME
RUN  ln -s $VALIDATOR_HOME/bin/wfs-ft-validator /bin/wfs-ft-validator

COPY ./docker-entrypoint.sh /
RUN chmod 755 /docker-entrypoint.sh

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["--help"]
