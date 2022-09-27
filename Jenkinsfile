pipeline {
    agent any
    stages {
        stage ('Pull') {
            steps {
                echo '========= pull =========='
                git credentialsId: 'xxxx', branch: 'dev', url: 'gitlab-address/xcalscanjenkinsplugin.git'
            }
        }

        stage ('Artifactory configuration') {
            steps {
                rtServer (
                        id: "xc5-artifactory",
                        url: 'http://127.0.0.1:8082/artifactory',
                        credentialsId: 'xc5-jfrog-artifactory'
                )

                rtMavenDeployer (
                        id: "MAVEN_DEPLOYER",
                        serverId: "xc5-artifactory",
                        releaseRepo: "libs-release-local",
                        snapshotRepo: "libs-snapshot-local"
                )

                rtMavenResolver (
                        id: "MAVEN_RESOLVER",
                        serverId: "xc5-artifactory",
                        releaseRepo: "libs-release",
                        snapshotRepo: "libs-snapshot"
                )
            }
        }

        stage ('Exec Maven') {
            steps {
                rtMavenRun (
                        tool: 'maven-3-6-0', // Tool name from Jenkins configuration
                        pom: 'pom.xml',
                        goals: 'clean install',
                        deployerId: "MAVEN_DEPLOYER",
                )
            }
        }

        stage ('Publish build info') {
            steps {
                rtPublishBuildInfo (
                        serverId: "xc5-artifactory"
                )
            }
        }
    }
}
