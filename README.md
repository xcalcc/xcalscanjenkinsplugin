# Xcalscan Jenkins plugin

Xcalscan Jenkins plugin is a plugin for Jenkins to prepare and trigger scan in xcalscan 

## Objective

Xcalscan is a SAST (Static Application Security Testing) tools which use for source code scanning and report potential defects.
The Jenkins plugin enable user to prepare and trigger scanning procss in the the xcalscan server. 
## Build

Install [Maven](http://maven.apache.org) and run the following:

        git clone https://github.com/xcalcc/xcalscanjenkinsplugin.git
        cd xcalscanjenkinsplugin
        mvn package

## Install Plugin

The instruction how to install the plugin by uploading hpi file
[here](https://www.jenkins.io/doc/book/managing/plugins/)

1. Navigate to the **Manage Jenkins** > **Manage Plugins** page in the web UI.
2. Click on the Advanced tab.
3. Choose the .hpi file under the Upload Plugin section.
4. Upload the plugin file.

## Workflow sequence of the plugin
View as graph by cope below code to [here](https://sequencediagram.org)
```sequence
title Scan workflow with Jenkins Plugin
actor "user" as u
participant "Git" as g
participantgroup **Jenkins**
participant "Jenkins Server" as j
participantgroup **Jenkins Slave**
participant "Jenkins Plugin\n(Xcalscan build step)" as jp
participant "Xcalclient" as xc
end
end
participant "Xcalscan Server" as xs
end
activate u
u->g:push
activate g
u<--g:push result
deactivateafter u
g->j:webhook
activate j
g<--j:http resposne ack
deactivateafter g
j->jp:process build step
activate jp
jp->xc:invoke client with CLI
activate xc
xc->xc:collect source code and all necessary information
activate xc
deactivateafter xc
xc->xc:compile
activate xc
deactivateafter xc
xc->xs:upload pre-processed file, source code, other necessary files
activate xs
xs->]:store file to storage
xc<--xs:file_info_id(s)
deactivateafter xs
xc->xs:start scan
activate xs
xc<--xs:scan task id
jp<--xc:start scan result
deactivateafter xc
xs->xs:analysis
activate xs
deactivateafter xs
xs->xs:store result
activate xs
xs->]:store result to database
deactivate xs
deactivateafter xs
jp->xs:query scan task id by project id
activate xs
jp<--xs:scan task id
deactivateafter xs
loop untill status is failed or completed
jp->xs:query scan task status
activate xs
jp<--xs:scan task status
deactivateafter xs
end
jp->xs:query scan result
activate xs
jp<--xs:scan result
deactivateafter xs
j<--jp:build step status
deactivateafter jp
deactivateafter j
```
