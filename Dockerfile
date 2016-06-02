FROM phusion/baseimage:0.9.17
MAINTAINER Antony Woods <antony@mastodonc.com>

CMD ["/sbin/my_init"]

RUN sudo apt-get install software-properties-common
RUN add-apt-repository -y ppa:webupd8team/java \
&& apt-get update \
&& echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | sudo /usr/bin/debconf-set-selections \
&& apt-get install -y \
software-properties-common \
oracle-java8-installer

RUN mkdir /etc/service/workspace

ADD target/witan.workspace-standalone.jar /srv/witan.workspace.jar

ADD scripts/run.sh /etc/service/workspace/run

RUN apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*
