# JQM Skywalking plugin

This module provides a plugin for [JQM](https://github.com/enioka-Haute-Couture/jqm) to monitor jobs with Apache SkyWalking.

---

## Usage
---

### Install SkyWalking Docker

Run the following command:

```bash
bash <(curl -sSL https://skywalking.apache.org/quickstart-docker.sh)
```

The SkyWalking web interface should now be accessible at: ``http://localhost:8080``

### Install SkyWalking JQM Agent

Simply compile using `mvn clean package` to generate the plugin jar (inside the /target folder).


From [this URL](https://dlcdn.apache.org/skywalking/java-agent), go to the latest version available and download the `apache-skywalking-java-agent-<version>.tgz` file.
Decompress it, then go into skywaling-agent/plugins, and copy the generated jar file into this folder.

Finaly, for JQM to use the plugin, launch it with the following JVM argument:
```bash
export SW_AGENT_NAME="JQM-Node"
export JAVA_OPTS=-javaagent:<path/to/skywalking-agent.jar>
export SW_AGENT_COLLECTOR_BACKEND_SERVICES="localhost:11800" # only if you are not using the default port
```
