FROM clojure:openjdk-11-tools-deps AS builder

RUN mkdir /tmp/tekton-watcher

WORKDIR /tmp/tekton-watcher

COPY . .

RUN ./build/package.sh

FROM openjdk:12

ENV TW_HOME=/opt/tekton-watcher

RUN mkdir $TW_HOME

COPY --from=builder /tmp/tekton-watcher/target/tekton-watcher.jar $TW_HOME

COPY build/tw.sh /usr/local/bin/tw

ENTRYPOINT ["tw"]
