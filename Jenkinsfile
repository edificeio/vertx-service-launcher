pipeline {
  agent any
  stages {
      stage("Initialization") {
        steps {
          script {
            def version = sh(returnStdout: true, script: 'mvn help:evaluate -Dexpression=project.version -q -DforceStdout')
            buildName "${env.GIT_BRANCH.replace("origin/", "")}@${version}"
          }
        }
      }
    stage('Build') {
      steps {
        sh 'mvn clean package -DskipMavenDockerBuild'
      }
    }
    stage('Publish') {
      steps {
        // sh 'mvn deploy'
        sh '''
          export jarFile=`ls target/vertx-service-launcher-*-fat.jar`
          export jarVersion=`echo $jarFile | sed 's|target/vertx-service-launcher-||' | sed 's/-fat.jar//'`
        case "$jarVersion" in
          *SNAPSHOT) export nexusRepository='snapshots' ;;
          *)         export nexusRepository='releases' ;;
          esac
            mvn deploy:deploy-file -DgroupId=com.opendigitaleducation -DartifactId=vertx-service-launcher -Dversion=$jarVersion -Dpackaging=jar -Dclassifier=fat -Dfile=$jarFile -DrepositoryId=ode-$nexusRepository -Durl=https://maven.opendigitaleducation.com/nexus/content/repositories/$nexusRepository/
        '''
      }
    }
  }
}

