def COLOR_MAP = ['SUCCESS': 'good', 'FAILURE': 'danger', 'UNSTABLE': 'danger', 'ABORTED': 'danger']

pipeline {
  agent any 

  options {
    timestamps()
  }

  environment {
    ANSIBLE_FORCE_COLOR = true
    ANSIBLE_STDOUT_CALLBACK = "debug"
    AWS_PROFILE="nfq-devops"
    DEPLOY_HOST_TAG="migrate"
    TIMESTAMP = """${sh(
                returnStdout: true,
                script: 'date --utc +%Y%m%d_%H%M%SZ'
                ).trim()}"""
  }

  parameters {
    string (
      defaultValue: "https://github.com/gsviec/demo-migrate.git",
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
        }
      }
    }
    
    stage('Deploy') {
        steps {
          sh """
            cd ./app
            cp .env.prod .env
            export ANSIBLE_CONFIG="./hostmap/ansible.cfg"
            ansible-galaxy install -r hostmap/roles/requirements.yaml

            ansible-playbook -i hostmap/inventory-tools.aws_ec2.yml deploy.yaml
            """
        }
    }
  }
}