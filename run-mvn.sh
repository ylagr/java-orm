#!/usr/bin/env bash
if ! command -v mvn >/dev/null 2>&1; then
  if [ -d "/c/Users/Administrator/AppData/Local/Programs/IntelliJ IDEA/jbr" ]; then
    export JAVA_HOME="/c/Users/Administrator/AppData/Local/Programs/IntelliJ IDEA/jbr"
    export M2_HOME="/c/Users/Administrator/AppData/Local/Programs/IntelliJ IDEA/plugins/maven-plugin/lib/maven3"
    export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"
  fi
fi

mvn "$@"
