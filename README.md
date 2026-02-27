# JQM Skywalking plugin

This module provides a plugin for [JQM](https://github.com/enioka-Haute-Couture/jqm) to monitor jobs with Apache SkyWalking, allowing to propagate distributed traces from client applications creating job requests to jobs.

This is designed as a plugin of the official Skywalking Java agent, which can be obtained [here](https://dlcdn.apache.org/skywalking/java-agent) or on their Github.

## Usage

The Skywalking agent must be copied to a directory with sufficient permissions for the user running the JQM service. This path is called `SW_ROOT` later.

Download the JQM plugin from Github: [Latest release](./releases/latest). Copy the jar file into the `SW_ROOT/plugins` folder.

Finaly, for JQM to use the Skywalking Java agent with the plugin, launch JQM with the following JVM arguments:

```bash
export SW_AGENT_NAME="JQM-Node"
export JAVA_OPTS=-javaagent:SW_ROOT/skywalking-agent.jar
export SW_AGENT_COLLECTOR_BACKEND_SERVICES="localhost:11800" # only if you are not using the default port
```

## Developing and testing locally

### Install SkyWalking Docker

Run the following command:

```bash
bash <(curl -sSL https://skywalking.apache.org/quickstart-docker.sh)
```

The SkyWalking web interface should now be accessible at: ``http://localhost:8080``

### Install SkyWalking JQM Agent

Simply compile using `mvn clean package` to generate the plugin jar (inside the /target folder). The copy it into a SW agent plugins directory.
