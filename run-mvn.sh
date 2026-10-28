#!/usr/bin/env bash
export JAVA_HOME="/c/Users/Administrator/AppData/Local/Programs/IntelliJ IDEA/jbr"
export M2_HOME="/c/Users/Administrator/AppData/Local/Programs/IntelliJ IDEA/plugins/maven-plugin/lib/maven3"
export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"

mvn "$@"
