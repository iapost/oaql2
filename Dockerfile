FROM maven:3.8.4-openjdk-17
COPY ./pom.xml /usr/src/mymaven/
COPY ./src /usr/src/mymaven/src
COPY ./html /usr/src/mymaven/html