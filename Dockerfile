FROM phusion/baseimage:0.9.17
MAINTAINER Antony Woods <antony@teamwoods.org>

CMD ["/sbin/my_init"]

# common
RUN sudo apt-get install software-properties-common

# java
RUN add-apt-repository -y ppa:webupd8team/java \
&& apt-get update \
&& echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | sudo /usr/bin/debconf-set-selections \
&& apt-get install -y \
software-properties-common \
oracle-java8-installer

# youtube-dl
RUN apt-get install -y python \
&& sudo curl -L https://yt-dl.org/downloads/latest/youtube-dl -o /usr/local/bin/youtube-dl \
&& sudo chmod a+rx /usr/local/bin/youtube-dl \
&& youtube-dl --version

RUN mkdir /etc/service/espdig

ADD target/espdig.jar /srv/espdig.jar

ADD scripts/run.sh /etc/service/espdig/run

RUN apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*
