#!/usr/bin/env bash
apt-get update
wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein -O /usr/bin/lein
chmod 755 /usr/bin/lein

add-apt-repository -y ppa:webupd8team/java \
&& apt-get update \
&& echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | sudo /usr/bin/debconf-set-selections \
&& apt-get install -y \
software-properties-common \
oracle-java8-installer

lein version
