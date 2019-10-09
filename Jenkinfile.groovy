def COLOR_MAP = ['SUCCESS': 'good', 'FAILURE': 'danger', 'UNSTABLE': 'danger', 'ABORTED': 'danger']

pipeline {
  agent any 

  options {
    timestamps()
    ansiColor('xterm')
  }

  environment {
    ANSIBLE_FORCE_COLOR = true
    ANSIBLE_STDOUT_CALLBACK = "debug"
    AWS_PROFILE="exocreate-devops"
    TIMESTAMP = """${sh(
                returnStdout: true,
                script: 'date --utc +%Y%m%d_%H%M%SZ'
                ).trim()}"""
  }

  parameters {
    string (
      defaultValue: "git@github.com:EXO-Travel/exocreate.git",
      description: "Repo to build",
      name: "repo"
    )
    string (
      defaultValue: "master",
      description: "Branch to build",
      name: "branch"
    )
  }

  stages {
    stage("Checkout SCM and Build") {
      steps {
        dir ("./app") {
          git(
            url: "${params.repo}",
            credentialsId: "github",
            branch: "${params.branch}",
            changelog: true)
          withCredentials([
              string(credentialsId: "prod_firebase_private_key",
                variable: "FIREBASE_PRIVATE_KEY"
              ),
              string(credentialsId: "prod_aws_app_assets_secret_key",
                variable: "AWS_APP_ASSETS_SECRET"
              ),
              string(credentialsId: "prod_auth_secret",
                variable: "AUTH_SECRET"
              ),
              string(credentialsId: "prod_auth_management_client_secret",
                variable: "AUTH_MANAGEMENT_CLIENT_SECRET"
              ),
              string(credentialsId: "prod_google_client_secret",
                variable: "GOOGLE_CLIENT_SECRET"
              ),
              string(credentialsId: "prod_mongodb_password",
                variable: "MONGODB_PASSWORD"
              ),
              string(credentialsId: "prod_redis_auth",
                variable: "REDIS_AUTH"
              ),
          ]) {
            sh """
              mv .env.prod .env
              sed -i '/AUTH_SECRET/c AUTH_SECRET=${AUTH_SECRET}' .env
              sed -i '/AWS_ACCESS_SECRET/c AWS_ACCESS_SECRET=${AWS_APP_ASSETS_SECRET}' .env
              sed -i '/FIREBASE_PRIVATE_KEY/c FIREBASE_PRIVATE_KEY=${FIREBASE_PRIVATE_KEY}' .env
              sed -i '/GOOGLE_CLIENT_SECRET/c GOOGLE_CLIENT_SECRET=${GOOGLE_CLIENT_SECRET}' .env
              sed -i '/AUTH_MANAGEMENT_CLIENT_SECRET/c AUTH_MANAGEMENT_CLIENT_SECRET=${AUTH_MANAGEMENT_CLIENT_SECRET}' .env
              sed -i 's/app:ManagedByDevopsPleaseReplace/app:'${MONGODB_PASSWORD}'/g' .env
              sed -i 's/:ManagedByDevopsPleaseReplace/':${REDIS_AUTH}'/g' .env
              mv pm2/api-gateway.ecosystem.config.js ecosystem.config.js
            """
          }
        }
      }
    }
    
    stage('Deploy') {
        steps {
          sh """
            cd ./app
            export ANSIBLE_CONFIG="./hostmap/ansible.cfg"
            ansible-galaxy install -r hostmap/roles/requirements.yaml

            cd provision/playbooks/environment-prod/app/api-gateway
            ansible-playbook asg.yml
            """
        }
    }
  }
}